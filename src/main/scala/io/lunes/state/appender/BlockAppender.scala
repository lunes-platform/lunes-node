package io.lunes.state.appender

import cats.data.EitherT
import io.lunes.consensus.PoSSelector
import io.lunes.metrics._
import io.lunes.mining.Miner
import io.lunes.network._
import io.lunes.settings.LunesSettings
import io.lunes.state.Blockchain
import io.lunes.utx.UtxPool
import io.netty.channel.Channel
import io.netty.channel.group.ChannelGroup
import kamon.Kamon
import monix.eval.Task
import monix.execution.Scheduler
import scorex.block.Block
import io.lunes.transaction.ValidationError.{BlockAppendError, InvalidSignature}
import io.lunes.transaction.{
  BlockchainUpdater,
  CheckpointService,
  ValidationError
}
import scorex.utils.{ScorexLogging, Time}

import scala.util.Right

object BlockAppender extends ScorexLogging with Instrumented {

  def apply(checkpoint: CheckpointService,
            blockchainUpdater: BlockchainUpdater with Blockchain,
            time: Time,
            utxStorage: UtxPool,
            pos: PoSSelector,
            settings: LunesSettings,
            scheduler: Scheduler)(
      newBlock: Block): Task[Either[ValidationError, Option[BigInt]]] =
    Task {
      measureSuccessful(
        blockProcessingTimeStats, {
          if (blockchainUpdater.isLastBlockId(newBlock.reference)) {
            appendBlock(checkpoint,
                        blockchainUpdater,
                        utxStorage,
                        pos,
                        time,
                        settings)(newBlock).map(_ =>
              Some(blockchainUpdater.score))
          } else if (blockchainUpdater.contains(newBlock.uniqueId)) {
            Right(None)
          } else {
            Left(
              BlockAppendError("Block is not a child of the last block",
                               newBlock))
          }
        }
      )
    }.executeOn(scheduler)

  def apply(checkpoint: CheckpointService,
            blockchainUpdater: BlockchainUpdater with Blockchain,
            time: Time,
            utxStorage: UtxPool,
            pos: PoSSelector,
            settings: LunesSettings,
            allChannels: ChannelGroup,
            peerDatabase: PeerDatabase,
            miner: Miner,
            scheduler: Scheduler)(ch: Channel, newBlock: Block): Task[Unit] = {
    BlockStats.received(newBlock, BlockStats.Source.Broadcast, ch)
    blockReceivingLag.safeRecord(
      System.currentTimeMillis() - newBlock.timestamp)
    (for {
      _ <- EitherT(Task.now(newBlock.signaturesValid()))
      validApplication <- EitherT(
        apply(checkpoint,
              blockchainUpdater,
              time,
              utxStorage,
              pos,
              settings,
              scheduler)(newBlock))
    } yield validApplication).value.map {
      case Right(None) => // block already appended
      case Right(Some(_)) =>
        BlockStats.applied(newBlock,
                           BlockStats.Source.Broadcast,
                           blockchainUpdater.height)
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
  private val blockProcessingTimeStats =
    Kamon.metrics.histogram("single-block-processing-time")

}
