package io.lunes.state2.appender

import cats.data.EitherT
import io.lunes.utx.UtxPool
import io.lunes.metrics.{BlockStats, Instrumented}
import io.lunes.network.MicroBlockSynchronizer.MicroblockData
import io.lunes.network._
import io.netty.channel.Channel
import io.netty.channel.group.ChannelGroup
import kamon.Kamon
import monix.eval.Task
import monix.execution.Scheduler
import scorex.block.MicroBlock
import io.lunes.transaction.ValidationError.{InvalidSignature, MicroBlockAppendError}
import io.lunes.transaction.{BlockchainUpdater, CheckpointService, History, ValidationError}
import scorex.utils.ScorexLogging

import scala.util.{Left, Right}

/**
  *
  */
object MicroblockAppender extends ScorexLogging with Instrumented {
  /**
    *
    * @param checkpoint
    * @param history
    * @param blockchainUpdater
    * @param utxStorage
    * @param scheduler
    * @param microBlock
    * @return
    */
  def apply(checkpoint: CheckpointService, history: History, blockchainUpdater: BlockchainUpdater, utxStorage: UtxPool,
            scheduler: Scheduler)
           (microBlock: MicroBlock): Task[Either[ValidationError, Unit]] = Task(measureSuccessful(microblockProcessingTimeStats, for {
    _ <- Either.cond(checkpoint.isBlockValid(microBlock.totalResBlockSig, history.height() + 1), (),
      MicroBlockAppendError(s"[h = ${history.height() + 1}] is not valid with respect to checkpoint", microBlock))
    _ <- blockchainUpdater.processMicroBlock(microBlock)
  } yield utxStorage.removeAll(microBlock.transactionData))).executeOn(scheduler)

  /**
    *
    * @param checkpoint
    * @param history
    * @param blockchainUpdater
    * @param utxStorage
    * @param allChannels
    * @param peerDatabase
    * @param scheduler
    * @param ch
    * @param md
    * @return
    */
  def apply(checkpoint: CheckpointService, history: History, blockchainUpdater: BlockchainUpdater, utxStorage: UtxPool,
            allChannels: ChannelGroup, peerDatabase: PeerDatabase, scheduler: Scheduler)(ch: Channel, md: MicroblockData): Task[Unit] = {
    import md.microBlock
    val microblockTotalResBlockSig = microBlock.totalResBlockSig
    (for {
      _ <- EitherT(Task.now(microBlock.signaturesValid()))
      validApplication <- EitherT(apply(checkpoint, history, blockchainUpdater, utxStorage, scheduler)(microBlock))
    } yield validApplication).value.map {
      case Right(()) =>
        md.invOpt match {
          case Some(mi) => allChannels.broadcast(mi, except = md.microblockOwners())
          case None => log.warn(s"${id(ch)} Not broadcasting MicroBlockInv")
        }
        BlockStats.applied(microBlock)
      case Left(is: InvalidSignature) =>
        peerDatabase.blacklistAndClose(ch, s"Could not append microblock $microblockTotalResBlockSig: $is")
      case Left(ve) =>
        BlockStats.declined(microBlock)
        log.debug(s"${id(ch)} Could not append microblock $microblockTotalResBlockSig: $ve")
    }
  }

  private val microblockProcessingTimeStats = Kamon.metrics.histogram("microblock-processing-time")
}
