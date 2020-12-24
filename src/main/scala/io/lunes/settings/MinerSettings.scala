package io.lunes.settings

import io.lunes.mining.Miner

import scala.concurrent.duration.FiniteDuration

/**
  *
  * @param enable
  * @param quorum
  * @param intervalAfterLastBlockThenGenerationIsAllowed
  * @param microBlockInterval
  * @param minimalBlockGenerationOffset
  * @param maxTransactionsInKeyBlock
  * @param maxTransactionsInMicroBlock
  * @param minMicroBlockAge
  */
case class MinerSettings(
    enable: Boolean,
    quorum: Int,
    intervalAfterLastBlockThenGenerationIsAllowed: FiniteDuration,
    microBlockInterval: FiniteDuration,
    minimalBlockGenerationOffset: FiniteDuration,
    maxTransactionsInKeyBlock: Int,
    maxTransactionsInMicroBlock: Int,
    minMicroBlockAge: FiniteDuration) {
  require(maxTransactionsInMicroBlock <= Miner.MaxTransactionsPerMicroblock)
}
