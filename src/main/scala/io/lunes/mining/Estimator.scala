package io.lunes.mining

import io.lunes.features.{BlockchainFeatures, FeatureProvider}
import io.lunes.settings.MinerSettings
import scorex.block.Block
import io.lunes.transaction.Transaction

/**
  *
  */
trait Estimator {
  def max: Long
  def estimate(x: Block): Long
  def estimate(x: Transaction): Long
}

/**
  *
  * @param max
  */
case class TxNumberEstimator(max: Long) extends Estimator {
  override def estimate(x: Block): Long = x.transactionCount
  override def estimate(x: Transaction): Long = 1
}

/**
  * @param max in bytes
  */
case class SizeEstimator(max: Long) extends Estimator {
  override def estimate(x: Block): Long = x.transactionData.view.map(estimate).sum
  override def estimate(x: Transaction): Long = x.bytes().length // + headers
}

/**
  *
  * @param total
  * @param keyBlock
  * @param micro
  */
case class MiningEstimators(total: Estimator, keyBlock: Estimator, micro: Estimator)

/**
  *
  */
object MiningEstimators {
  private val ClassicAmountOfTxsInBlock = 100
  private val MaxTxsSizeInBytes = 1 * 1024 * 1024 // 1 megabyte
  /**
    *
    * @param minerSettings
    * @param featureProvider
    * @param height
    * @return
    */
  def apply(minerSettings: MinerSettings, featureProvider: FeatureProvider, height: Int): MiningEstimators = {
    val activatedFeatures = featureProvider.activatedFeatures(height)
    val isMassTransferEnabled = activatedFeatures.contains(BlockchainFeatures.MassTransfer.id)

    MiningEstimators(
      total = if (isMassTransferEnabled) SizeEstimator(MaxTxsSizeInBytes) else {
        TxNumberEstimator(Block.MaxTransactionsPerBlockVer3)
      },
      keyBlock = if (isMassTransferEnabled) TxNumberEstimator(0) else {
        TxNumberEstimator(minerSettings.maxTransactionsInKeyBlock)
      },
      micro = TxNumberEstimator(minerSettings.maxTransactionsInMicroBlock)
    )
  }
}
