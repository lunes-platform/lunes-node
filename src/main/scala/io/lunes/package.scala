package io

import io.lunes.settings.LunesSettings
import io.lunes.state.NG
import io.lunes.utils.forceStopApplication
import scorex.block.Block
import io.lunes.transaction.BlockchainUpdater
import scorex.utils.ScorexLogging

package object lunes extends ScorexLogging {
  def checkGenesis(settings: LunesSettings,
                   blockchainUpdater: BlockchainUpdater with NG): Unit =
    if (blockchainUpdater.isEmpty) {
      Block
        .genesis(settings.blockchainSettings.genesisSettings)
        .flatMap(blockchainUpdater.processBlock)
        .left
        .foreach { value =>
          log.error(value.toString)
          forceStopApplication()
        }
      log.info(
        s"Genesis block ${blockchainUpdater.blockHeaderAndSize(1).get._1} has been added to the state")
    }
}
