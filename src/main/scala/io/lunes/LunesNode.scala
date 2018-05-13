package io.lunes
/** lunes.io main package
  * = Overview =
  *
  *
  */
import java.io.File
import java.util.concurrent._

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import cats.instances.all._
import com.typesafe.config._
import io.lunes.utx._
import io.lunes.root.RootActorSystem
import io.lunes.db.openDB
import io.lunes.features.api.ActivationApiRoute
import io.lunes.history.{CheckpointServiceImpl, StorageFactory}
import io.lunes.http.NodeApiRoute
import io.lunes.metrics.Metrics
import io.lunes.mining.{Miner, MinerImpl}
import io.lunes.network.RxExtensionLoader.RxExtensionLoaderShutdownHook
import io.lunes.network._
import io.lunes.settings._
import io.lunes.state2.appender.{BlockAppender, CheckpointAppender, ExtensionAppender, MicroblockAppender}
import io.lunes.utils.{SystemInformationReporter, fixNTP, forceStopApplication}
import io.netty.channel.Channel
import io.netty.channel.group.DefaultChannelGroup
import io.netty.util.concurrent.GlobalEventExecutor
import kamon.Kamon
import monix.reactive.subjects.ConcurrentSubject
import monix.execution.Scheduler.global
import monix.execution.schedulers.SchedulerService
import monix.execution.{Scheduler, UncaughtExceptionReporter}
import monix.reactive.Observable
import org.influxdb.dto.Point
import org.slf4j.bridge.SLF4JBridgeHandler
import scorex.account.AddressScheme
import scorex.api.http._
import scorex.api.http.alias.{AliasApiRoute, AliasBroadcastApiRoute}
import scorex.api.http.assets.{AssetsApiRoute, AssetsBroadcastApiRoute}
import scorex.api.http.leasing.{LeaseApiRoute, LeaseBroadcastApiRoute}
import scorex.consensus.nxt.api.http.NxtConsensusApiRoute
import io.lunes.transaction._
import scorex.platform.http.DebugApiRoute
import scorex.utils.{NTP, ScorexLogging, Time}
import scorex.wallet.Wallet

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.reflect.runtime.universe._
import scala.util.Try

/** Lunes Node 
 *  @param actorSystem Akka ActorSystem sign in descriptor
 *  @param settings [[io.lunes.settings.LunesSettings]] configurations
 */
class LunesNode(val actorSystem: ActorSystem, val settings: LunesSettings, configRoot: ConfigObject) extends ScorexLogging {

  import monix.execution.Scheduler.Implicits.{global => scheduler}

  private val db = openDB(settings.dataDirectory, settings.levelDbCacheSize)

  private val LocalScoreBroadcastDebounce = 1.second

  // Start /node API right away
  private implicit val as: ActorSystem = actorSystem
  private implicit val materializer: ActorMaterializer = ActorMaterializer()
  private val (storage, heights) = StorageFactory(db, settings).get
  private val nodeApi = Option(settings.restAPISettings.enable).collect { case true =>
    val tags = Seq(typeOf[NodeApiRoute])
    val routes = Seq(NodeApiRoute(settings.restAPISettings, heights, () => apiShutdown()))
    val combinedRoute: Route = CompositeHttpService(actorSystem, tags, routes, settings.restAPISettings).compositeRoute
    val httpFuture = Http().bindAndHandle(combinedRoute, settings.restAPISettings.bindAddress, settings.restAPISettings.port)
    serverBinding = Await.result(httpFuture, 10.seconds)
    log.debug(s"Node REST API was bound on ${settings.restAPISettings.bindAddress}:${settings.restAPISettings.port}")
    (tags, routes)
  }

  private val checkpointService = new CheckpointServiceImpl(db, settings.checkpointsSettings)
  private val (history, featureProvider, stateReader, blockchainUpdater, blockchainDebugInfo) = storage()
  private lazy val upnp = new UPnP(settings.networkSettings.uPnPSettings) // don't initialize unless enabled

  private val wallet: Wallet = try {
    Wallet(settings.walletSettings)
  } catch {
    case e: IllegalStateException =>
      log.error(s"Failed to open wallet file '${settings.walletSettings.file.get.getAbsolutePath}")
      throw e
  }
  private val peerDatabase = new PeerDatabaseImpl(settings.networkSettings)

  private val extensionLoaderScheduler = Scheduler.singleThread("tx-extension-loader", reporter = UncaughtExceptionReporter { ex => log.error(s"ExtensionLoader: $ex") })
  private val microblockSynchronizerScheduler = Scheduler.singleThread("microblock-synchronizer", reporter = UncaughtExceptionReporter { ex => log.error(s"MicroblockSynchronizer: $ex") })
  private val scoreObserverScheduler = Scheduler.singleThread("rx-score-observer", reporter = UncaughtExceptionReporter { ex => log.error(s"ScoreObserver: $ex") })
  private val appenderScheduler = Scheduler.singleThread("appender", reporter = UncaughtExceptionReporter { ex => log.error(s"Appender: $ex") })
  private val historyRepliesScheduler = Scheduler.fixedPool(name = "history-replier", poolSize = 2, reporter = UncaughtExceptionReporter { ex => log.error(s"HistoryReplier: $ex") })
  private val minerScheduler = Scheduler.fixedPool(name = "miner-pool", poolSize = 2, reporter = UncaughtExceptionReporter { ex => log.error(s"Miner: $ex") })


  //private var matcher: Option[Matcher] = None
  private var rxExtensionLoaderShutdown: Option[RxExtensionLoaderShutdownHook] = None
  private var maybeUtx: Option[UtxPoolImpl] = None
  private var maybeNetwork: Option[NS] = None

  /** API shutdown method with no returning type. */
  def apiShutdown(): Unit = {
    for {
      u <- maybeUtx
      n <- maybeNetwork
    } yield shutdown(u, n)
  }

  /** Run the Node. */
  def run(): Unit = {
    checkGenesis(history, settings, blockchainUpdater)  // chamada desnecessária.

    if (wallet.privateKeyAccounts.isEmpty){
        wallet.generateNewAccounts(1)
        log.error("ATTENTION: edit the config file and add your seed in wallet section.")
        forceStopApplication()
    }

    log.info("wallet: {} ", wallet.privateKeyAccounts.mkString("[",",","]"))

    val feeCalculator = new FeeCalculator(settings.feesSettings)
    val time: Time = NTP
    val establishedConnections = new ConcurrentHashMap[Channel, PeerInfo]
    val allChannels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE)
    val utxStorage = new UtxPoolImpl(time, stateReader, history, featureProvider, feeCalculator, settings.blockchainSettings.functionalitySettings, settings.utxSettings)
    maybeUtx = Some(utxStorage)
    val knownInvalidBlocks = new InvalidBlockStorageImpl(settings.synchronizationSettings.invalidBlocksStorage)
    val miner = if (settings.minerSettings.enable)
      new MinerImpl(allChannels, blockchainUpdater, checkpointService, history, featureProvider, stateReader, settings,
        time, utxStorage, wallet, minerScheduler, appenderScheduler)
    else Miner.Disabled

    val processBlock = BlockAppender(checkpointService, history, blockchainUpdater, time, stateReader, utxStorage,
      settings, featureProvider, allChannels, peerDatabase, miner, appenderScheduler) _
    val processCheckpoint = CheckpointAppender(checkpointService, history, blockchainUpdater, peerDatabase, miner,
      allChannels, appenderScheduler) _
    val processFork = ExtensionAppender(checkpointService, history, blockchainUpdater, stateReader, utxStorage, time,
      settings, featureProvider, knownInvalidBlocks, peerDatabase, miner, allChannels, appenderScheduler) _
    val processMicroBlock = MicroblockAppender(checkpointService, history, blockchainUpdater, utxStorage, allChannels, peerDatabase, appenderScheduler) _

    import blockchainUpdater.lastBlockInfo

    val lastScore = lastBlockInfo
      .map(_.score)
      .distinctUntilChanged
      .share(scheduler)

    lastScore
      .debounce(LocalScoreBroadcastDebounce)
      .foreach { x =>
        allChannels.broadcast(LocalScoreChanged(x))
      }(scheduler)

    val historyReplier = new HistoryReplier(history, settings.synchronizationSettings, historyRepliesScheduler)
    val network = NetworkServer(settings, lastBlockInfo, history, historyReplier, utxStorage, peerDatabase, allChannels, establishedConnections)
    maybeNetwork = Some(network)
    val (signatures, blocks, blockchainScores, checkpoints, microblockInvs, microblockResponses, transactions) = network.messages

    val timeoutSubject: ConcurrentSubject[Channel, Channel] = ConcurrentSubject.publish[Channel]
    val (syncWithChannelClosed, scoreStatsReporter) = RxScoreObserver(settings.synchronizationSettings.scoreTTL, 1.second,
      history.score(), lastScore, blockchainScores, network.closedChannels, timeoutSubject, scoreObserverScheduler)
    val (microblockDatas, mbSyncCacheSizes) = MicroBlockSynchronizer(settings.synchronizationSettings.microBlockSynchronizer,
      peerDatabase, lastBlockInfo.map(_.id), microblockInvs, microblockResponses, microblockSynchronizerScheduler)
    val (newBlocks, extLoaderState, sh) = RxExtensionLoader(settings.synchronizationSettings.maxRollback, settings.synchronizationSettings.synchronizationTimeout,
      history, peerDatabase, knownInvalidBlocks, blocks, signatures, syncWithChannelClosed, extensionLoaderScheduler, timeoutSubject) { case ((c, b)) => processFork(c, b.blocks) }

    rxExtensionLoaderShutdown = Some(sh)

    UtxPoolSynchronizer.start(utxStorage, settings.synchronizationSettings.utxSynchronizerSettings, allChannels, transactions)
    val microBlockSink = microblockDatas.mapTask(scala.Function.tupled(processMicroBlock))
    val blockSink = newBlocks.mapTask(scala.Function.tupled(processBlock))
    val checkpointSink = checkpoints.mapTask { case ((s, c)) => processCheckpoint(Some(s), c) }

    Observable.merge(microBlockSink, blockSink, checkpointSink).subscribe()(monix.execution.Scheduler.Implicits.global)
    miner.scheduleMining()

    for (addr <- settings.networkSettings.declaredAddress if settings.networkSettings.uPnPSettings.enable) {
      upnp.addPort(addr.getPort)
    }

    // Start complete REST API. Node API is already running, so we need to re-bind
    nodeApi.foreach { case (tags, routes) =>
      val apiRoutes = routes ++ Seq(
        BlocksApiRoute(settings.restAPISettings, history, blockchainUpdater, allChannels, c => processCheckpoint(None, c)),
        TransactionsApiRoute(settings.restAPISettings, wallet, stateReader, history, utxStorage, allChannels, time),
        NxtConsensusApiRoute(settings.restAPISettings, stateReader, history, settings.blockchainSettings.functionalitySettings),
        WalletApiRoute(settings.restAPISettings, wallet),
        UtilsApiRoute(settings.restAPISettings),
        PeersApiRoute(settings.restAPISettings, network.connect, peerDatabase, establishedConnections),
        AddressApiRoute(settings.restAPISettings, wallet, stateReader, settings.blockchainSettings.functionalitySettings),
        DebugApiRoute(settings.restAPISettings, wallet, stateReader, history, peerDatabase, establishedConnections, blockchainUpdater, allChannels,
          utxStorage, blockchainDebugInfo, miner, historyReplier, extLoaderState, mbSyncCacheSizes, scoreStatsReporter, configRoot),
        AssetsApiRoute(settings.restAPISettings, wallet, utxStorage, allChannels, stateReader, time),
        ActivationApiRoute(settings.restAPISettings, settings.blockchainSettings.functionalitySettings, settings.featuresSettings, history, featureProvider),
        AssetsBroadcastApiRoute(settings.restAPISettings, utxStorage, allChannels),
        LeaseApiRoute(settings.restAPISettings, wallet, stateReader, utxStorage, allChannels, time),
        LeaseBroadcastApiRoute(settings.restAPISettings, utxStorage, allChannels),
        AliasApiRoute(settings.restAPISettings, wallet, utxStorage, allChannels, time, stateReader),
        AliasBroadcastApiRoute(settings.restAPISettings, utxStorage, allChannels)
      )

      val apiTypes = tags ++ Seq(
        typeOf[BlocksApiRoute],
        typeOf[TransactionsApiRoute],
        typeOf[NxtConsensusApiRoute],
        typeOf[WalletApiRoute],
        typeOf[UtilsApiRoute],
        typeOf[PeersApiRoute],
        typeOf[AddressApiRoute],
        typeOf[DebugApiRoute],
        typeOf[AssetsApiRoute],
        typeOf[ActivationApiRoute],
        typeOf[AssetsBroadcastApiRoute],
        typeOf[LeaseApiRoute],
        typeOf[LeaseBroadcastApiRoute],
        typeOf[AliasApiRoute],
        typeOf[AliasBroadcastApiRoute]
      )
      val combinedRoute: Route = CompositeHttpService(actorSystem, apiTypes, apiRoutes, settings.restAPISettings).loggingCompositeRoute
      val httpFuture = serverBinding.unbind().flatMap { _ =>
        Http().bindAndHandle(combinedRoute, settings.restAPISettings.bindAddress, settings.restAPISettings.port)
      }
      serverBinding = Await.result(httpFuture, 20.seconds)
      log.debug(s"REST API was bound on ${settings.restAPISettings.bindAddress}:${settings.restAPISettings.port}")
    }

    //on unexpected shutdown
    sys.addShutdownHook {
      Kamon.shutdown()
      Metrics.shutdown()
      shutdown(utxStorage, network)
    }

    // matcher = None
  }

  @volatile var shutdownInProgress = false
  @volatile var serverBinding: ServerBinding = _


  /** Shutdown
    * @param utx informs the [[io.lunes.utx.UtxPoolImpl]] transaction pool object.
    * @param network sets the network.
 */
  def shutdown(utx: UtxPoolImpl, network: NS): Unit = {
    if (!shutdownInProgress) {
      shutdownInProgress = true

      utx.close()

      shutdownAndWait(historyRepliesScheduler, "HistoryReplier", 5.minutes)

      log.debug("Closing REST API")
      if (settings.restAPISettings.enable) {
        Try(Await.ready(serverBinding.unbind(), 2.minutes))
          .failed.map(e => log.error("Failed to unbind REST API port", e))
      }
      for (addr <- settings.networkSettings.declaredAddress if settings.networkSettings.uPnPSettings.enable) {
        upnp.deletePort(addr.getPort)
      }

      // matcher.foreach(_.shutdownMatcher())

      log.debug("Closing peer database")
      peerDatabase.close()

      Try(Await.result(actorSystem.terminate(), 2.minute))
        .failed.map(e => log.error("Failed to terminate actor system", e))

      blockchainUpdater.shutdown()
      rxExtensionLoaderShutdown.foreach(_.shutdown())

      log.debug("Stopping network services")
      network.shutdown()

      shutdownAndWait(minerScheduler, "Miner")
      shutdownAndWait(microblockSynchronizerScheduler, "MicroblockSynchronizer")
      shutdownAndWait(scoreObserverScheduler, "ScoreObserver")
      shutdownAndWait(extensionLoaderScheduler, "ExtensionLoader")
      shutdownAndWait(appenderScheduler, "Appender", 5.minutes)

      log.debug("Closing storage")
      db.close()

      log.info("Shutdown complete")
    }
  }

  private def shutdownAndWait(scheduler: SchedulerService, name: String, timeout: FiniteDuration = 1.minute): Unit = {
    scheduler.shutdown()
    val r = Await.result(scheduler.awaitTermination(timeout, global), Duration.Inf)
    if (r)
      log.debug(s"$name was shutdown successfully")
    else
      log.warn(s"Failed to shutdown $name properly during timeout")
  }

}
/** LunesNode companion object executable */
object LunesNode extends ScorexLogging {

  /** Configuration Reader
   *  @param userConfigPath informs the filepath for the configuration file.
   */
  private def readConfig(userConfigPath: Option[String]): Config = {
    val maybeConfigFile = for {
      maybeFilename <- userConfigPath
      file = new File(maybeFilename)
      if file.exists
    } yield file

    val config = maybeConfigFile match {
      case None =>
        log.error("No configuration file was provided!")
        forceStopApplication()
        //TODO:
        ConfigFactory.load()
      // application config needs to be resolved wrt both system properties *and* user-supplied config.
      case Some(file) =>
        val cfg = ConfigFactory.parseFile(file)
        if (!cfg.hasPath("lunes")) {
          log.error("Malformed configuration file was provided!")
          forceStopApplication()
        }
        loadConfig(cfg)
    }

    config
  }

  /** Main executable method
   * @param args - Command Line arguments
   */
  def main(args: Array[String]): Unit = {
    fixNTP()

    SLF4JBridgeHandler.removeHandlersForRootLogger()
    SLF4JBridgeHandler.install()

    val config = readConfig(args.headOption)
    System.setProperty("lunes.directory", config.getString("lunes.directory"))

    log.info("Starting...")
    sys.addShutdownHook {
      SystemInformationReporter.report(config)
    }

    val settings = LunesSettings.fromConfig(config)
    Kamon.start(config)
    val isMetricsStarted = Metrics.start(settings.metrics)

    RootActorSystem.start("lunes", config) { actorSystem =>
      import actorSystem.dispatcher
      isMetricsStarted.foreach { started =>
        if (started) {
          import settings.synchronizationSettings.microBlockSynchronizer
          import settings.{minerSettings => miner}

          Metrics.write(
            Point
              .measurement("config")
              .addField("miner-micro-block-interval", miner.microBlockInterval.toMillis)
              .addField("miner-max-transactions-in-key-block", miner.maxTransactionsInKeyBlock)
              .addField("miner-max-transactions-in-micro-block", miner.maxTransactionsInMicroBlock)
              .addField("miner-min-micro-block-age", miner.minMicroBlockAge.toMillis)
              .addField("mbs-wait-response-timeout", microBlockSynchronizer.waitResponseTimeout.toMillis)
          )
        }
      }

      // Initialize global var with actual address scheme
      AddressScheme.current = new AddressScheme {
        override val chainId: Byte = settings.blockchainSettings.addressSchemeCharacter.toByte
      }

      log.info(s"${Constants.AgentName} Blockchain Id: ${settings.blockchainSettings.addressSchemeCharacter}")

      new LunesNode(actorSystem, settings, config.root()).run()
    }
  }
}
