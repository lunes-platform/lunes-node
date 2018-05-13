package io.lunes.state2.appender

import cats.data.EitherT
import io.lunes.utx.UtxPool
import io.lunes.features.FeatureProvider
import io.lunes.metrics._
import io.lunes.mining.Miner
import io.lunes.network._
import io.lunes.settings.LunesSettings
import io.lunes.state2.StateReader
import io.netty.channel.Channel
import io.netty.channel.group.ChannelGroup
import kamon.Kamon
import monix.eval.Task
import monix.execution.Scheduler
import scorex.block.Block
import io.lunes.transaction.History.BlockchainScore
import io.lunes.transaction.ValidationError.{BlockAppendError, InvalidSignature}
import io.lunes.transaction.{BlockchainUpdater, CheckpointService, History, ValidationError}
import scorex.utils.{ScorexLogging, Time}

import scala.util.Right

/** BlockAppender logging Object */
object BlockAppender extends ScorexLogging with Instrumented {
  /** Factory method for [[monix.eval.Task]] object of Either an Option for [[BlockchainScore]] (case Success) or a ValidationError (case Failure).
    * @param checkpoint Inputs a Checkpoint Service.
    * @param history Inputs the History Object.
    * @param blockchainUpdater The Blockchain Updater.
    * @param time the Time Object.
    * @param stateReader Inputs a StateReader.
    * @param utxStorage The Transcation Pool.
    * @param settings The Lunes Settings.
    * @param featureProvider The Feature Provider.
    * @param scheduler Informs the Scheduler.
    * @param newBlock The Block to Append.
    * @return Returns the Task.
    */
  def apply(checkpoint: CheckpointService, history: History, blockchainUpdater: BlockchainUpdater, time: Time,
            stateReader: StateReader, utxStorage: UtxPool, settings: LunesSettings,
            featureProvider: FeatureProvider, scheduler: Scheduler)(newBlock: Block): Task[Either[ValidationError, Option[BlockchainScore]]] = Task {
    measureSuccessful(blockProcessingTimeStats, history.write("apply") { implicit l =>
      if (history.contains(newBlock)) Right(None)
      else for {
        _ <- Either.cond(history.heightOf(newBlock.reference).exists(_ >= history.height() - 1), (), BlockAppendError("Irrelevant block", newBlock))
        maybeBaseHeight <- appendBlock(checkpoint, history, blockchainUpdater, stateReader(), utxStorage, time, settings, featureProvider)(newBlock)
      } yield maybeBaseHeight map (_ => history.score())
    })
  }.executeOn(scheduler)

  /** Alternative Factory for a [[monix.eval.Task]] of Unit with Channel inputs.
    * @param checkpoint Inputs a Checkpoint Service.
    * @param history Inputs the History Object.
    * @param blockchainUpdater The Blockchain Updater.
    * @param time the Time Object.
    * @param stateReader Inputs a StateReader.
    * @param utxStorage The Transcation Pool.
    * @param settings The Lunes Settings.
    * @param featureProvider The Feature Provider.
    * @param allChannels Inputs the ChannelGroup.
    * @param peerDatabase Informs the Peer Database.
    * @param miner Sets a Miner.
    * @param scheduler Informs the Scheduler.
    * @param ch The Channel.
    * @param newBlock The Block to Append.
    * @return Returns the Task.
    */
  def apply(checkpoint: CheckpointService, history: History, blockchainUpdater: BlockchainUpdater, time: Time,
            stateReader: StateReader, utxStorage: UtxPool, settings: LunesSettings,
            featureProvider: FeatureProvider, allChannels: ChannelGroup, peerDatabase: PeerDatabase, miner: Miner,
            scheduler: Scheduler)(ch: Channel, newBlock: Block): Task[Unit] = {
    BlockStats.received(newBlock, BlockStats.Source.Broadcast, ch)
    blockReceivingLag.safeRecord(System.currentTimeMillis() - newBlock.timestamp)
    (for {
      _ <- EitherT(Task.now(newBlock.signaturesValid()))
      validApplication <- EitherT(apply(checkpoint, history, blockchainUpdater, time, stateReader, utxStorage, settings,
        featureProvider, scheduler)(newBlock))
    } yield validApplication).value.map {
      case Right(None) =>
        log.trace(s"${id(ch)} $newBlock already appended")
      case Right(Some(_)) =>
        BlockStats.applied(newBlock, BlockStats.Source.Broadcast, history.height())
        log.debug(s"${id(ch)} Appended $newBlock")
        if (newBlock.transactionData.isEmpty)
          allChannels.broadcast(BlockForged(newBlock), Some(ch))
        miner.scheduleMining()
      case Left(is: InvalidSignature) =>
        peerDatabase.blacklistAndClose(ch, s"Could not append $newBlock: $is")
      case Left(ve) =>
        BlockStats.declined(newBlock, BlockStats.Source.Broadcast)
        log.debug(s"${id(ch)} Could not append $newBlock: $ve")
    }
  }

  private val blockReceivingLag = Kamon.metrics.histogram("block-receiving-lag")
  private val blockProcessingTimeStats = Kamon.metrics.histogram("single-block-processing-time")

}
