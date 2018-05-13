package io
/** Provides the main classes for the Lunes Platform
  *  == Overview ==
  * The main object is [[io.lunes.LunesNode]] as defined.
  * 
 */

import io.lunes.settings.LunesSettings
import io.lunes.utils.forceStopApplication
import scorex.block.Block
import io.lunes.transaction.{BlockchainUpdater, History}
import scorex.utils.ScorexLogging
/** Package object for loggin extended from [[scorex.utils.ScorexLogging]] */
package object lunes extends ScorexLogging {
  /** Generates the Blockchain Network based on Scorex Block Genesis.
    * @param history inputs the history object for the generated Blockchains.
    * @param settings informs the [[io.lunes.settings.LunesSettings]] regarding the current settings.
    * @param blockchainUpdater informs the [[io.lunes.transaction.BlockchainUpdater]]
    */
  def checkGenesis(history: History, settings: LunesSettings, blockchainUpdater: BlockchainUpdater): Unit = if (history.isEmpty) {
    Block.genesis(settings.blockchainSettings.genesisSettings).flatMap(blockchainUpdater.processBlock)
      .left.foreach { value =>
      log.error(value.toString)
      forceStopApplication()
    }
    log.debug("Genesis block has been added to the state")
  }
}
