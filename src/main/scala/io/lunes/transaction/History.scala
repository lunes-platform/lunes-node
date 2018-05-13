package io.lunes.transaction

import io.lunes.network.{BlockCheckpoint, Checkpoint}
import io.lunes.state2.ByteStr
import io.lunes.utils.HeightInfo
import scorex.block.Block.BlockId
import scorex.block.{Block, BlockHeader, MicroBlock}
import scorex.consensus.nxt.NxtLikeConsensusBlockData
import io.lunes.transaction.History.{BlockMinerInfo, BlockchainScore}
import scorex.utils.Synchronized

import scala.util.Try

/**
  *
  */
trait History extends Synchronized {
  /**
    *
    * @return
    */
  def height(): Int

  /**
    *
    * @param height
    * @return
    */
  def blockAt(height: Int): Option[Block]

  /**
    *
    * @param height
    * @return
    */
  def blockHeaderAndSizeAt(height: Int): Option[(BlockHeader, Int)]

  /**
    *
    * @param height
    * @return
    */
  def blockBytes(height: Int): Option[Array[Byte]]

  /**
    *
    * @param id
    * @return
    */
  def scoreOf(id: ByteStr): Option[BlockchainScore]

  /**
    *
    * @param blockId
    * @return
    */
  def heightOf(blockId: ByteStr): Option[Int]

  /**
    *
    * @param howMany
    * @return
    */
  def lastBlockIds(howMany: Int): Seq[ByteStr]

  /**
    *
    * @return
    */
  def lastBlockTimestamp(): Option[Long]

  /**
    *
    * @return
    */
  def lastBlockId(): Option[ByteStr]

  /**
    *
    * @return
    */
  def debugInfo: HeightInfo
}

/**
  *
  */
trait NgHistory extends History {
  /**
    *
     * @param id
    * @return
    */
  def microBlock(id: ByteStr): Option[MicroBlock]

  /**
    *
    * @param maxTimestamp
    * @return
    */
  def bestLastBlockInfo(maxTimestamp: Long): Option[BlockMinerInfo]
}

/**
  *
  */
trait DebugNgHistory {
  /**
    *
     * @param count
    * @return
    */
  def lastPersistedBlockIds(count: Int): Seq[BlockId]

  /**
    *
    * @return
    */
  def microblockIds(): Seq[BlockId]
}

/**
  *
  */
trait CheckpointService {
  /**
    *
    * @param checkpoint
    * @return
    */
  def set(checkpoint: Checkpoint): Either[ValidationError, Unit]

  /**
    *
    * @return
    */
  def get: Option[Checkpoint]
}

/**
  *
  */
object CheckpointService {

  /**
    *
    * @param cs
    */
  implicit class CheckpointServiceExt(cs: CheckpointService) {
    def isBlockValid(candidateSignature: ByteStr, estimatedHeight: Int): Boolean =
      !cs.get.exists {
        _.items.exists { case BlockCheckpoint(h, sig) =>
          h == estimatedHeight && candidateSignature != ByteStr(sig)
        }
      }
  }

}

/**
  *
  */
object History {

  type BlockchainScore = BigInt

  /**
    *
    * @param consensus
    * @param timestamp
    * @param blockId
    */
  case class BlockMinerInfo(consensus: NxtLikeConsensusBlockData, timestamp: Long, blockId: BlockId)

  /**
    *
    * @param history
    */
  implicit class HistoryExt(history: History) {
    /**
      *
      * @return
      */
    def score(): BlockchainScore = history.read { implicit lock =>
      history.lastBlock.flatMap(last => history.scoreOf(last.uniqueId)).getOrElse(0)
    }

    /**
      *
      * @return
      */
    def isEmpty: Boolean = history.height() == 0

    /**
      *
      * @param block
      * @return
      */
    def contains(block: Block): Boolean = history.contains(block.uniqueId)

    /**
      *
      * @param signature
      * @return
      */
    def contains(signature: ByteStr): Boolean = history.heightOf(signature).isDefined

    /**
      *
      * @param blockId
      * @return
      */
    def blockById(blockId: ByteStr): Option[Block] = history.read { _ =>
      history.heightOf(blockId).flatMap(history.blockAt)
    }

    /**
      *
      * @param blockId
      * @return
      */
    def blockById(blockId: String): Option[Block] = ByteStr.decodeBase58(blockId).toOption.flatMap(history.blockById)

    /**
      *
      * @param block
      * @return
      */
    def heightOf(block: Block): Option[Int] = history.heightOf(block.uniqueId)

    /**
      *
      * @param block
      * @return
      */
    def confirmations(block: Block): Option[Int] = history.read { _ =>
      heightOf(block).map(history.height() - _)
    }

    /**
      *
      * @return
      */
    def lastBlock: Option[Block] = history.read { _ =>
      history.blockAt(history.height())
    }

    /**
      *
      * @param block
      * @param blockNum
      * @return
      */
    def averageDelay(block: Block, blockNum: Int): Try[Long] = Try {
      (block.timestamp - parent(block, blockNum).get.timestamp) / blockNum
    }

    /**
      *
      * @param block
      * @param back
      * @return
      */
    def parent(block: Block, back: Int = 1): Option[Block] = history.read { _ =>
      require(back > 0)
      history.heightOf(block.reference).flatMap(referenceHeight => history.blockAt(referenceHeight - back + 1))
    }

    /**
      *
      * @param block
      * @return
      */
    def child(block: Block): Option[Block] = history.read { _ =>
      history.heightOf(block.uniqueId).flatMap(h => history.blockAt(h + 1))
    }

    /**
      *
      * @param howMany
      * @return
      */
    def lastBlocks(howMany: Int): Seq[Block] = history.read { _ =>
      (Math.max(1, history.height() - howMany + 1) to history.height()).flatMap(history.blockAt).reverse
    }

    /**
      *
      * @param parentSignature
      * @param howMany
      * @return
      */
    def blockIdsAfter(parentSignature: ByteStr, howMany: Int): Seq[ByteStr] = history.read { _ =>
      history.heightOf(parentSignature).map { h =>
        (h + 1).to(Math.min(history.height(), h + howMany: Int)).flatMap(history.blockAt).map(_.uniqueId)
      }.getOrElse(Seq())
    }

    /**
      *
      * @return
      */
    def genesis: Block = history.blockAt(1).get
  }
}
