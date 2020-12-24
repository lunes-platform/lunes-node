package io.lunes.mining

import cats.data.EitherT
import io.lunes.utx.UtxPool
import io.lunes.features.{BlockchainFeatureStatus, BlockchainFeatures, FeatureProvider}
import io.lunes.metrics.{BlockStats, HistogramExt, Instrumented}
import io.lunes.network._
import io.lunes.settings.LunesSettings
import io.lunes.state2._
import io.lunes.state2.appender.{BlockAppender, MicroblockAppender}
import io.netty.channel.group.ChannelGroup
import io.lunes.transaction.PoSCalc._
import io.lunes.transaction._
import kamon.Kamon
import kamon.metric.instrument
import monix.eval.Task
import monix.execution.cancelables.{CompositeCancelable, SerialCancelable}
import monix.execution.schedulers.SchedulerService
import scorex.account.{Address, PrivateKeyAccount}
import scorex.block.Block._
import scorex.block.{Block, MicroBlock}
import scorex.consensus.nxt.NxtLikeConsensusBlockData
import scorex.utils.{ScorexLogging, Time}
import scorex.wallet.Wallet

import scala.collection.mutable.{Map => MMap}
import scala.concurrent.Await
import scala.concurrent.duration._

/** Miner Trait interface*/
trait Miner {
  /** Provides an interface for Schedule Mining. */
  def scheduleMining(): Unit
}

/** Miner Degub Information Trait interface.*/
trait MinerDebugInfo {
  /** Provides interface for Debug Information State.
    * @return Returns the State of the [[MinerDebugInfo]].
    */
  def state: MinerDebugInfo.State

  /** Provides an interface for Collecting Next Block Generation Times.
    * @return Returns a list of Tuples ([[scorex.account.Address]], Long)
    */
  def collectNextBlockGenerationTimes: List[(Address, Long)]
}

/** Miner Debug Information Companion Object. */
object MinerDebugInfo {

  /** Algebraic Data Type for determining States for Miner Debug Information.
    *
    * Possible cases are:
    * [[MiningBlocks]]
    * [[MiningMicroblocks]]
    * [[Disabled]]
    * [[Error]]
    */
  sealed trait State

  /** Case Object for ADT Type. */
  case object MiningBlocks extends State

  /** Case Object for ADT Type. */
  case object MiningMicroblocks extends State

  /** Case Object for ADT Type. */
  case object Disabled extends State

  /** Case Class for Error ADT Type.
    * @param error Input Error String.
    */
  case class Error(error: String) extends State

}

/**
  * Class for Miner Implementator.
  * @constructor Creates a new Miner Implementator
  * @param allChannels Netty Channel Group.
  * @param blockchainUpdater Blockchain Updater
  * @param checkpoint Checkpoint Service.
  * @param history NgHistory
  * @param featureProvider Feature Provider Object.
  * @param stateReader StateReader Object.
  * @param settings LunesSettings Object.
  * @param timeService Time Object.
  * @param utx Transaction Pool.
  * @param wallet inputs [[scorex.wallet.Wallet]].
  * @param scheduler Sets Scheduler.
  * @param appenderScheduler Sets Appender Scheduler.
  */
class MinerImpl(allChannels: ChannelGroup,
                blockchainUpdater: BlockchainUpdater,
                checkpoint: CheckpointService,
                history: NgHistory,
                featureProvider: FeatureProvider,
                stateReader: StateReader,
                settings: LunesSettings,
                timeService: Time,
                utx: UtxPool,
                wallet: Wallet,
                val scheduler: SchedulerService,
                val appenderScheduler: SchedulerService) extends Miner with MinerDebugInfo with ScorexLogging with Instrumented {

  import Miner._

  private implicit val s: SchedulerService = scheduler

  private lazy val minerSettings = settings.minerSettings
  private lazy val minMicroBlockDurationMills = minerSettings.minMicroBlockAge.toMillis
  private lazy val blockchainSettings = settings.blockchainSettings

  private val scheduledAttempts = SerialCancelable()
  private val microBlockAttempt = SerialCancelable()

  private val blockBuildTimeStats = Kamon.metrics.histogram("pack-and-forge-block-time", instrument.Time.Milliseconds)
  private val microBlockBuildTimeStats = Kamon.metrics.histogram("forge-microblock-time", instrument.Time.Milliseconds)

  private val nextBlockGenerationTimes: MMap[Address, Long] = MMap.empty

  @volatile private var debugState: MinerDebugInfo.State = MinerDebugInfo.Disabled

	/** Collects Next Block Generation Times.
		* @return Returns a list of Tuples ([[scorex.account.Address]], Long)
		*/
  def collectNextBlockGenerationTimes: List[(Address, Long)] = Await.result(Task.now(nextBlockGenerationTimes.toList).runAsyncLogErr, Duration.Inf)

	/** Checks Age.
		* @param parentHeight Inputs Parent Height.
		* @param parentTimestamp Inputs Parent Time Stamp.
		* @return Returns Either a String (case Failure) or is a Unit (case Success).
		*/
  private def checkAge(parentHeight: Int, parentTimestamp: Long): Either[String, Unit] =
    Either.cond(parentHeight == 1, (), (timeService.correctedTime() - parentTimestamp).millis)
      .left.flatMap(blockAge => Either.cond(blockAge <= minerSettings.intervalAfterLastBlockThenGenerationIsAllowed, (),
      s"BlockChain is too old (last block timestamp is $parentTimestamp generated $blockAge ago)"
    ))

	/** Check if ng is Enabled.
		* @return Return true if ng is Enabled.
		*/
  private def ngEnabled: Boolean = featureProvider.featureActivationHeight(BlockchainFeatures.NG.id).exists(history.height > _ + 1)

	/** Generates One Block Task.
		* @param account Account Private Key.
		* @param balance Account Balance.
		* @param delay Sets Delay.
		* @return Return the Task.
		*/
  private def generateOneBlockTask(account: PrivateKeyAccount, balance: Long)(delay: FiniteDuration): Task[Either[String, (MiningEstimators, Block, MiningConstraint)]] = Task {
    history.read { implicit l =>
      // should take last block right at the time of mining since microblocks might have been added
      val height = history.height()
      val version = if (height <= blockchainSettings.functionalitySettings.blockVersion3AfterHeight) PlainBlockVersion else NgBlockVersion
      val lastBlock = history.lastBlock.get
      val greatGrandParentTimestamp = history.parent(lastBlock, 2).map(_.timestamp)
      val referencedBlockInfo = history.bestLastBlockInfo(System.currentTimeMillis() - minMicroBlockDurationMills).get
      val pc = allChannels.size()
      lazy val currentTime = timeService.correctedTime()
      lazy val h = calcHit(referencedBlockInfo.consensus, account)
      lazy val t = calcTarget(referencedBlockInfo.timestamp, referencedBlockInfo.consensus.baseTarget, currentTime, balance)
      measureSuccessful(blockBuildTimeStats, for {
        _ <- Either.cond(pc >= minerSettings.quorum, (), s"Quorum not available ($pc/${minerSettings.quorum}, not forging block with ${account.address}")
        _ <- Either.cond(h < t, (), s"${System.currentTimeMillis()}: Hit $h was NOT less than target $t, not forging block with ${account.address}")
        _ = log.debug(s"Forging with ${account.address}, H $h < T $t, balance $balance, prev block ${referencedBlockInfo.blockId}")
        _ = log.debug(s"Previous block ID ${referencedBlockInfo.blockId} at $height with target ${referencedBlockInfo.consensus.baseTarget}")
        block <- {
          val avgBlockDelay = blockchainSettings.genesisSettings.averageBlockDelay
          val btg = calcBaseTarget(avgBlockDelay, height, referencedBlockInfo.consensus.baseTarget, referencedBlockInfo.timestamp, greatGrandParentTimestamp, currentTime)
          val gs = calcGeneratorSignature(referencedBlockInfo.consensus, account)
          val consensusData = NxtLikeConsensusBlockData(btg, ByteStr(gs))
          val sortInBlock = history.height() <= blockchainSettings.functionalitySettings.dontRequireSortedTransactionsAfter

          val estimators = MiningEstimators(minerSettings, featureProvider, height)
          val mdConstraint = TwoDimensionalMiningConstraint.full(estimators.total, estimators.keyBlock)
          val (unconfirmed, updatedMdConstraint) = utx.packUnconfirmed(mdConstraint, sortInBlock)

          val features = if (version > 2) settings.featuresSettings.supported
            .filter(featureProvider.featureStatus(_, height) == BlockchainFeatureStatus.Undefined)
            .toSet.intersect(BlockchainFeatures.implemented) else Set.empty[Short]

          log.debug(s"Adding ${unconfirmed.size} unconfirmed transaction(s) to new block")
          Block.buildAndSign(version.toByte, currentTime, referencedBlockInfo.blockId, consensusData, unconfirmed, account, features) match {
            case Left(e) => Left(e.err)
            case Right(x) => Right((estimators, x, updatedMdConstraint.first))
          }
        }
      } yield block)
    }
  }.delayExecution(delay)

	/** Generates One Micro Block Task.
		* @param account Account Private Key.
		* @param accumulatedBlock Accumulated Block.
		* @param microEstimator Micro Estimator.
		* @param restTotalConstraint Rest of Total Mining Constraint.
		* @return
		*/
  private def generateOneMicroBlockTask(account: PrivateKeyAccount, accumulatedBlock: Block, microEstimator: Estimator, restTotalConstraint: MiningConstraint): Task[MicroblockMiningResult] = {
    log.trace(s"Generating microBlock for $account")
    val pc = allChannels.size()
    if (pc < minerSettings.quorum) {
      log.trace(s"Quorum not available ($pc/${minerSettings.quorum}, not forging microblock with ${account.address}")
      Task.now(Retry)
    } else if (utx.size == 0) {
      log.trace(s"Skipping microBlock because utx is empty")
      Task.now(Retry)
    } else {
      val (unconfirmed, updatedTotalConstraint) = measureLog("packing unconfirmed transactions for microblock") {
        val mdConstraint = TwoDimensionalMiningConstraint.partial(restTotalConstraint, OneDimensionalMiningConstraint.full(microEstimator))
        val (unconfirmed, updatedMdConstraint) = utx.packUnconfirmed(mdConstraint, sortInBlock = false)
        (unconfirmed, updatedMdConstraint.first)
      }

      if (updatedTotalConstraint.isEmpty) {
        log.trace(s"Stopping forging microBlocks, the block is full")
        Task.now(Stop)
      } else if (unconfirmed.isEmpty) {
        log.trace(s"Stopping forging microBlocks, because all transactions are too big")
        Task.now(Stop)
      } else {
        log.trace(s"Accumulated ${unconfirmed.size} txs for microblock")
        val start = System.currentTimeMillis()
        (for {
          signedBlock <- EitherT.fromEither[Task](Block.buildAndSign(
            version = 3,
            timestamp = accumulatedBlock.timestamp,
            reference = accumulatedBlock.reference,
            consensusData = accumulatedBlock.consensusData,
            transactionData = accumulatedBlock.transactionData ++ unconfirmed,
            signer = account,
            featureVotes = accumulatedBlock.featureVotes
          ))
          microBlock <- EitherT.fromEither[Task](MicroBlock.buildAndSign(account, unconfirmed, accumulatedBlock.signerData.signature, signedBlock.signerData.signature))
          _ = microBlockBuildTimeStats.safeRecord(System.currentTimeMillis() - start)
          _ <- EitherT(MicroblockAppender(checkpoint, history, blockchainUpdater, utx, appenderScheduler)(microBlock))
        } yield (microBlock, signedBlock)).value map {
          case Left(err) => Error(err)
          case Right((microBlock, signedBlock)) =>
            BlockStats.mined(microBlock)
            log.trace(s"$microBlock has been mined for $account}")
            allChannels.broadcast(MicroBlockInv(account, microBlock.totalResBlockSig, microBlock.prevResBlockSig))
            Success(signedBlock, updatedTotalConstraint)
        }
      }
    }
  }

	/** Generates Micro Block Sequence.
		* @param account Account Private Key.
		* @param accumulatedBlock Accumulated Block.
		* @param delay Sets Delay.
		* @param microEstimator Micro Estimator.
		* @param restTotalConstraint Rest of Total Mining Constraint.
		* @return
		*/
	//TODO:tailrec
  private def generateMicroBlockSequence(account: PrivateKeyAccount, accumulatedBlock: Block, delay: FiniteDuration, microEstimator: Estimator, restTotalConstraint: MiningConstraint): Task[Unit] = {
    debugState = MinerDebugInfo.MiningMicroblocks
    generateOneMicroBlockTask(account, accumulatedBlock, microEstimator, restTotalConstraint).delayExecution(delay).flatMap {
      case Error(e) => Task {
        debugState = MinerDebugInfo.Error(e.toString)
        log.warn("Error mining MicroBlock: " + e.toString)
      }
      case Success(newTotal, updatedTotalConstraint) => generateMicroBlockSequence(account, newTotal, minerSettings.microBlockInterval, microEstimator, updatedTotalConstraint)
      case Retry => generateMicroBlockSequence(account, accumulatedBlock, minerSettings.microBlockInterval, microEstimator, restTotalConstraint)
      case Stop => Task {
        debugState = MinerDebugInfo.MiningBlocks
        log.debug("MicroBlock mining completed, block is full")
      }
    }
  }

	/** Generates Block Task
		* @param account Account Private Key.
		* @return Return the Block Task
		*/
	//TODO:tailrec
  private def generateBlockTask(account: PrivateKeyAccount): Task[Unit] = {
    history.read { implicit l =>
      val height = history.height()
      val lastBlock = history.lastBlock.get
      for {
        _ <- checkAge(height, history.lastBlockTimestamp().get)
        balanceAndTs <- nextBlockGenerationTime(height, stateReader, blockchainSettings.functionalitySettings, lastBlock, account, featureProvider)
        (balance, ts) = balanceAndTs
        offset = calcOffset(timeService, ts, minerSettings.minimalBlockGenerationOffset)
      } yield (offset, balance)
    } match {
      case Right((offset, balance)) =>
        log.debug(s"Next attempt for acc=$account in $offset")
        nextBlockGenerationTimes += account.toAddress -> (System.currentTimeMillis() + offset.toMillis)
        generateOneBlockTask(account, balance)(offset).flatMap {
          case Right((estimators, block, totalConstraint)) =>
            BlockAppender(checkpoint, history, blockchainUpdater, timeService, stateReader, utx,
              settings, featureProvider, appenderScheduler)(block).map {
              case Left(err) => log.warn("Error mining Block: " + err.toString)
              case Right(Some(score)) =>
                log.debug(s"Forged and applied $block by ${account.address} with cumulative score $score")
                BlockStats.mined(block, history.height())
                allChannels.broadcast(BlockForged(block))
                scheduleMining()
                if (ngEnabled && !totalConstraint.isEmpty) startMicroBlockMining(account, block, estimators.micro, totalConstraint)
              case Right(None) => log.warn("Newly created block has already been appended, should not happen")
            }
          case Left(err) =>
            log.debug(s"No block generated because $err, retrying")
            generateBlockTask(account)
        }
      case Left(err) =>
        log.debug(s"Not scheduling block mining because $err")
        debugState = MinerDebugInfo.Error(err)
        Task.unit
    }
  }

	/** Schedules mining.
		*/
  def scheduleMining(): Unit = {
    Miner.blockMiningStarted.increment()
    scheduledAttempts := CompositeCancelable.fromSet(wallet.privateKeyAccounts.map(generateBlockTask).map(_.runAsyncLogErr).toSet)
    microBlockAttempt := SerialCancelable()
    debugState = MinerDebugInfo.MiningBlocks
  }

	/** Starts Microblock Mining.
		* @param account Account Private Key.
		* @param lastBlock Last Block to generate sequence.
		* @param microEstimator Micro estimator.
		* @param restTotalConstraint Rest of Total Mining Constraint.
		*/
  private def startMicroBlockMining(account: PrivateKeyAccount, lastBlock: Block, microEstimator: Estimator, restTotalConstraint: MiningConstraint): Unit = {
    Miner.microMiningStarted.increment()
    microBlockAttempt := generateMicroBlockSequence(account, lastBlock, Duration.Zero, microEstimator, restTotalConstraint).runAsyncLogErr
    log.trace(s"MicroBlock mining scheduled for $account")
  }

	/** Gets Debug State
		* @return Returns the State of the [[MinerDebugInfo]].
		*/
  override def state: MinerDebugInfo.State = debugState
}

/** Miner Object. */
object Miner {
  val blockMiningStarted = Kamon.metrics.counter("block-mining-started")
  val microMiningStarted = Kamon.metrics.counter("micro-mining-started")

  val MaxTransactionsPerMicroblock: Int = 500

  val Disabled = new Miner with MinerDebugInfo {
    override def scheduleMining(): Unit = ()

    override def collectNextBlockGenerationTimes: List[(Address, Long)] = List.empty

    override val state = MinerDebugInfo.Disabled
  }

	/** Calculates Offset.
		* @param timeService Inputs current Time.
		* @param calculatedTimestamp Inputs a Calculated Timestamp.
		* @param minimalBlockGenerationOffset Inputs Minimal Offset.
		* @return Returs the Calculated Offset.
		*/
  def calcOffset(timeService: Time, calculatedTimestamp: Long, minimalBlockGenerationOffset: FiniteDuration): FiniteDuration = {
    val calculatedGenerationTimestamp = (Math.ceil(calculatedTimestamp / 1000.0) * 1000).toLong
    val calculatedOffset = calculatedGenerationTimestamp - timeService.correctedTime()
    Math.max(minimalBlockGenerationOffset.toMillis, calculatedOffset).millis
  }

	/** Algebraic Data Type for determining States for Microblock Mining Result
		*
		* Possible cases are:
		* [[Stop]]
		* [[Retry]]
		* [[Error]]
		* [[Success]]
		*/
  sealed trait MicroblockMiningResult

	/** Case Object for ADT Type.*/
  case object Stop extends MicroblockMiningResult
  /** Case Object for ADT Type. */
  case object Retry extends MicroblockMiningResult

	/** Case Class for ADT Type.
		* @constructor Creates a ADT Error Type.
		* @param e Sets [[io.lunes.transaction.ValidationError]].
		*/
  case class Error(e: ValidationError) extends MicroblockMiningResult

	/** Case Class for ADT Type.
		* @constructor Creates a ADT Success Type.
		* @param b [[scorex.block.Block]] input.
		* @param totalConstraint [[io.lunes.mining.MiningConstraint]] input.
		*/
  case class Success(b: Block, totalConstraint: MiningConstraint) extends MicroblockMiningResult

}


