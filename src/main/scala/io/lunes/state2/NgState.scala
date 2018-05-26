package io.lunes.state2

import java.util.concurrent.TimeUnit

import cats.kernel.Monoid
import com.google.common.cache.CacheBuilder
import io.lunes.mining.{OneDimensionalMiningConstraint, Estimator}
import scorex.block.Block.BlockId
import scorex.block.{Block, MicroBlock}
import io.lunes.transaction.History.BlockMinerInfo
import io.lunes.transaction.{DiscardedMicroBlocks, Transaction}
import scorex.utils.ScorexLogging

import scala.collection.mutable.{ListBuffer => MList, Map => MMap}

/** Class for NgState data.
  * @constructor Creates a new NgState object.
  * @param base inputs the [[scorex.block.Block]].
  * @param baseBlockDiff Inputs the [[io.lunes.state2.BlockDiff]].
  * @param acceptedFeatures Inputs the Accepted Features.
  * @param totalBlockEstimator Inputs a [[io.lunes.mining.Estimator]] for the Block.
  */
class NgState(val base: Block, val baseBlockDiff: BlockDiff, val acceptedFeatures: Set[Short], totalBlockEstimator: Estimator) extends ScorexLogging {

  private val MaxTotalDiffs = 3

  private var constraint = OneDimensionalMiningConstraint.full(totalBlockEstimator).put(base)
  private val microDiffs: MMap[BlockId, (BlockDiff, Long)] = MMap.empty
  private val micros: MList[MicroBlock] = MList.empty // fresh head
  private val totalBlockDiffCache = CacheBuilder.newBuilder()
    .maximumSize(MaxTotalDiffs)
    .expireAfterWrite(10, TimeUnit.MINUTES)
    .build[BlockId, BlockDiff]()

  /** Gets The IDs for the Micro Blocks.
    * @return Returns a Sequence for BlockId objects.
    */
  def microBlockIds: Seq[BlockId] = micros.map(_.totalResBlockSig).toList

  //TODO: Tailrec
  private def diffFor(totalResBlockSig: BlockId): BlockDiff =
    if (totalResBlockSig == base.uniqueId)
      baseBlockDiff
    else Option(totalBlockDiffCache.getIfPresent(totalResBlockSig)) match {
      case Some(d) => d
      case None =>
        val prevResBlockSig = micros.find(_.totalResBlockSig == totalResBlockSig).get.prevResBlockSig
        val prevResBlockDiff = Option(totalBlockDiffCache.getIfPresent(prevResBlockSig)).getOrElse(diffFor(prevResBlockSig))
        val currentMicroDiff = microDiffs(totalResBlockSig)._1
        val r = Monoid.combine(prevResBlockDiff, currentMicroDiff)
        totalBlockDiffCache.put(totalResBlockSig, r)
        r
    }

  /** Get the Best Liquid Block Id.
    * @return Returns a BlockId for Best Block.
    */
  def bestLiquidBlockId: BlockId =
    micros.headOption.map(_.totalResBlockSig).getOrElse(base.uniqueId)

  /** Get the Last Micro Block.
    * @return Returns an Option of [[scorex.block.MicroBlock]] .
    */
  def lastMicroBlock: Option[MicroBlock] = micros.headOption

  /** Gets the Transactions.
    * @return Return a Sequence of [[io.lunes.transaction.Transaction]] objects.
    */
  def transactions: Seq[Transaction] = base.transactionData ++ micros.map(_.transactionData).reverse.flatten

  /** Gest the Best Liquid Block
    * @return Returns the Best Liquid [[scorex.block.Block]].
    */
  def bestLiquidBlock: Block =
    if (micros.isEmpty) {
      base
    } else {
      base.copy(signerData = base.signerData.copy(signature = micros.head.totalResBlockSig),
        transactionData = transactions)
    }

  /** Gets the total Differece of a Block.
    * @param id Inputs the BlockId.
    * @return Returns an Option for a Tuple ([[scorex.block.Block]], [[io.lunes.state2.BlockDiff]], [[io.lunes.transaction.DiscardedMicroBlocks]]).
    */
  def totalDiffOf(id: BlockId): Option[(Block, BlockDiff, DiscardedMicroBlocks)] =
    forgeBlock(id).map { case (b, txs) => (b, diffFor(id), txs) }

  /** Gets the Best Liquid Difference.
    * @return Returns the [[io.lunes.state2.BlockDiff]] with the best liquid difference.
    */
  def bestLiquidDiff: BlockDiff = micros.headOption.map(m => totalDiffOf(m.totalResBlockSig).get._2).getOrElse(baseBlockDiff)

  /** Check if Block ID exists.
    * @param blockId Inputs the BlockID
    * @return True if it exists.
    */
  def contains(blockId: BlockId): Boolean = base.uniqueId == blockId || microDiffs.contains(blockId)

  /** Gets the MicroBlock which has the given BlockID.
    * @param id Inputs the BlockId.
    * @return Returns an Option of [[scorex.block.MicroBlock]].
    */
  def microBlock(id: BlockId): Option[MicroBlock] = micros.find(_.totalResBlockSig == id)

  private def forgeBlock(id: BlockId): Option[(Block, DiscardedMicroBlocks)] = {
    val ms = micros.reverse
    if (base.uniqueId == id) {
      Some((base, ms))
    } else if (!ms.exists(_.totalResBlockSig == id)) None
    else {
      val (accumulatedTxs, maybeFound) = ms.foldLeft((List.empty[Transaction], Option.empty[(ByteStr, DiscardedMicroBlocks)])) { case ((accumulated, maybeDiscarded), micro) =>
        maybeDiscarded match {
          case Some((sig, discarded)) => (accumulated, Some((sig, micro +: discarded)))
          case None =>
            if (micro.totalResBlockSig == id)
              (accumulated ++ micro.transactionData, Some((micro.totalResBlockSig, Seq.empty[MicroBlock])))
            else
              (accumulated ++ micro.transactionData, None)
        }
      }
      maybeFound.map { case (sig, discardedMicroblocks) => (
        base.copy(signerData = base.signerData.copy(signature = sig), transactionData = base.transactionData ++ accumulatedTxs),
        discardedMicroblocks)
      }
    }
  }

  /** Gets the Best LastBlock Information.
    * @param maxTimeStamp Inputs the Maximum Time Stamp.
    * @return Returns a [[io.lunes.transaction.History.BlockMinerInfo]].
    */
  def bestLastBlockInfo(maxTimeStamp: Long): BlockMinerInfo = {
    val blockId = micros.find(micro => microDiffs(micro.totalResBlockSig)._2 <= maxTimeStamp)
      .map(_.totalResBlockSig)
      .getOrElse(base.uniqueId)
    BlockMinerInfo(base.consensusData, base.timestamp, blockId)
  }

  /** Appends microBlock into the NgState.
    * @param m Inputs the [[scorex.block.MicroBlock]].
    * @param diff Inputs the [[scorex.block.MicroBlock]].
    * @param timestamp Inputs the Time Stamp.
    * @return Returns True if succeeded appending.
    */
  def append(m: MicroBlock, diff: BlockDiff, timestamp: Long): Boolean = {
    val updatedConstraint = m.transactionData.foldLeft(constraint)(_.put(_))
    val successful = !updatedConstraint.isOverfilled
    if (successful) {
      constraint = updatedConstraint
      microDiffs.put(m.totalResBlockSig, (diff, timestamp))
      micros.prepend(m)
    }
    successful
  }
}