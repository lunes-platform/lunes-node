package io

import io.lunes.settings.LunesSettings
import io.lunes.utils.forceStopApplication
import scorex.block.Block
import io.lunes.transaction.{BlockchainUpdater, History}
import scorex.utils.ScorexLogging

package object lunes extends ScorexLogging {
  def checkGenesis(history: History, settings: LunesSettings, blockchainUpdater: BlockchainUpdater): Unit = if (history.isEmpty) {
    Block.genesis(settings.blockchainSettings.genesisSettings).flatMap(blockchainUpdater.processBlock)
      .left.foreach { value =>
      log.error(value.toString)
      forceStopApplication()
    }
    log.debug("Genesis block has been added to the state")
  }
}
