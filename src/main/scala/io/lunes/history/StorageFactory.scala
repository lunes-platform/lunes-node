package io.lunes.history

import java.util.concurrent.locks.{ReentrantReadWriteLock => RWL}

import io.lunes.features.FeatureProvider
import io.lunes.settings.LunesSettings
import io.lunes.state2._
import io.lunes.utils.HeightInfo
import monix.eval.Coeval
import org.iq80.leveldb.DB
import io.lunes.transaction._
import scorex.utils.{NTP, Time}

import scala.util.{Success, Try}

/**
  *
  */
object StorageFactory {

  type Storage = Coeval[(NgHistory with DebugNgHistory, FeatureProvider, StateReader, BlockchainUpdater, BlockchainDebugInfo)]
  type HeightInfos = Coeval[(HeightInfo, HeightInfo)]

  /**
    *
    * @param history
    * @param db
    * @param time
    * @return
    */
  private def createStateStorage(history: History with FeatureProvider, db: DB, time: Time): Try[StateStorage] =
    StateStorage(db, dropExisting = false).flatMap { ss =>
      if (ss.getHeight <= history.height()) Success(ss) else {
        StateStorage(db, dropExisting = true)
      }
    }

  /**
    *
    * @param db
    * @param settings
    * @param time
    * @tparam T
    * @return
    */
  def apply[T](db: DB, settings: LunesSettings, time: Time = NTP): Try[(Storage, HeightInfos)] = {
    val lock = new RWL(true)
    for {
      historyWriter <- HistoryWriterImpl(db, lock, settings.blockchainSettings.functionalitySettings, settings.featuresSettings)
      ss <- createStateStorage(historyWriter, db, time)
      stateWriter = new StateWriterImpl(ss, lock)
      bcu = BlockchainUpdaterImpl(stateWriter, historyWriter, settings, time, lock)
    } yield (
      Coeval {
        bcu.syncPersistedAndInMemory()
        val history: NgHistory with DebugNgHistory with FeatureProvider = bcu.historyReader
        (history, history, bcu.bestLiquidState, bcu, bcu)
      },
      Coeval {
        (historyWriter.debugInfo, bcu.lockfreeStateHeight)
      }
    )
  }
}
