package io.lunes.consensus

//import io.lunes.features.BlockchainFeatures
import io.lunes.settings.FunctionalitySettings
import io.lunes.state.Blockchain
import scorex.account.Address
import scorex.block.Block

object GeneratingBalanceProvider {
  private val MinimalEffectiveBalanceForGenerator: Long = 500000000000L
  private val FirstDepth = 50
  private val SecondDepth = 1000

  def isMiningAllowed(blockchain: Blockchain,
                      height: Int,
                      effectiveBalance: Long): Boolean =
    (effectiveBalance >= MinimalEffectiveBalanceForGenerator)

  def isEffectiveBalanceValid(blockchain: Blockchain,
                              fs: FunctionalitySettings,
                              height: Int,
                              block: Block,
                              effectiveBalance: Long): Boolean =
    (effectiveBalance >= MinimalEffectiveBalanceForGenerator)

  def balance(blockchain: Blockchain,
              fs: FunctionalitySettings,
              height: Int,
              account: Address): Long = {
    val depth =
      if (height >= fs.generationBalanceDepthFrom50To1000AfterHeight)
        SecondDepth
      else FirstDepth
    blockchain.effectiveBalance(account, height, depth)
  }
}
