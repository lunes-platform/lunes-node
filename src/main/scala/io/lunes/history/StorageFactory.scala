package io.lunes.history

import io.lunes.database.LevelDBWriter
import io.lunes.settings.LunesSettings
import io.lunes.state.{BlockchainUpdaterImpl, NG}
import org.iq80.leveldb.DB
import io.lunes.transaction.BlockchainUpdater
import scorex.utils.Time

object StorageFactory {
  def apply(settings: LunesSettings,
            db: DB,
            time: Time): BlockchainUpdater with NG = {
    val levelDBWriter = new LevelDBWriter(
      db,
      settings.blockchainSettings.functionalitySettings,
      settings.maxCacheSize)
    new BlockchainUpdaterImpl(levelDBWriter, settings, time)
  }
}
