package io.lunes.transaction

import io.lunes.state2.ByteStr
import io.lunes.utils.HeightInfo
import monix.reactive.Observable
import scorex.block.Block.BlockId
import scorex.block.{Block, MicroBlock}
import io.lunes.transaction.History.BlockchainScore
import scorex.utils.Synchronized

/**
  *
  */
trait BlockchainUpdater extends Synchronized {
  /**
    *
    * @param block
    * @return
    */
  def processBlock(block: Block): Either[ValidationError, Option[DiscardedTransactions]]

  /**
    *
    * @param microBlock
    * @return
    */
  def processMicroBlock(microBlock: MicroBlock): Either[ValidationError, Unit]

  /**
    *
    * @param blockId
    * @return
    */
  def removeAfter(blockId: ByteStr): Either[ValidationError, DiscardedBlocks]

  /**
    *
    * @return
    */
  def lastBlockInfo: Observable[LastBlockInfo]

  /**
    *
    */
  def shutdown(): Unit
}

/**
  *
  */
trait BlockchainDebugInfo {
  def lockfreeStateHeight: HeightInfo
}

/**
  *
  * @param id
  * @param height
  * @param score
  * @param ready
  */
case class LastBlockInfo(id: BlockId, height: Int, score: BlockchainScore, ready: Boolean)
