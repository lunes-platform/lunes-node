package io.lunes.settings

import com.typesafe.config.Config
import io.lunes.network.InvalidBlockStorageImpl.InvalidBlockStorageSettings
import io.lunes.settings.SynchronizationSettings._
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._

import scala.concurrent.duration.FiniteDuration

/**
  *
  * @param maxRollback
  * @param maxChainLength
  * @param synchronizationTimeout
  * @param scoreTTL
  * @param remoteScoreDebounce
  * @param invalidBlocksStorage
  * @param microBlockSynchronizer
  * @param historyReplierSettings
  * @param utxSynchronizerSettings
  */
case class SynchronizationSettings(maxRollback: Int,
                                   maxChainLength: Int,
                                   synchronizationTimeout: FiniteDuration,
                                   scoreTTL: FiniteDuration,
                                   remoteScoreDebounce: FiniteDuration,
                                   invalidBlocksStorage: InvalidBlockStorageSettings,
                                   microBlockSynchronizer: MicroblockSynchronizerSettings,
                                   historyReplierSettings: HistoryReplierSettings,
                                   utxSynchronizerSettings: UtxSynchronizerSettings)

/**
  *
  */
object SynchronizationSettings {

  /**
    *
    * @param waitResponseTimeout
    * @param processedMicroBlocksCacheTimeout
    * @param invCacheTimeout
    */
  case class MicroblockSynchronizerSettings(waitResponseTimeout: FiniteDuration,
                                            processedMicroBlocksCacheTimeout: FiniteDuration,
                                            invCacheTimeout: FiniteDuration)

  /**
    *
    * @param maxMicroBlockCacheSize
    * @param maxBlockCacheSize
    */
  case class HistoryReplierSettings(maxMicroBlockCacheSize: Int,
                                    maxBlockCacheSize: Int)

  /**
    *
    * @param networkTxCacheSize
    * @param networkTxCacheTime
    * @param maxBufferSize
    * @param maxBufferTime
    */
  case class UtxSynchronizerSettings(networkTxCacheSize: Int,
                                     networkTxCacheTime: FiniteDuration,
                                     maxBufferSize: Int,
                                     maxBufferTime: FiniteDuration)

  val configPath: String = "lunes.synchronization"

  /**
    *
    * @param config
    * @return
    */
  def fromConfig(config: Config): SynchronizationSettings = {
    val maxRollback = config.as[Int](s"$configPath.max-rollback")
    val maxChainLength = config.as[Int](s"$configPath.max-chain-length")
    val synchronizationTimeout = config.as[FiniteDuration](s"$configPath.synchronization-timeout")
    val scoreTTL = config.as[FiniteDuration](s"$configPath.score-ttl")
    val remoteScoreDebounce = config.as[FiniteDuration](s"$configPath.remote-score-debounce")
    val invalidBlocksStorage = config.as[InvalidBlockStorageSettings](s"$configPath.invalid-blocks-storage")
    val microBlockSynchronizer = config.as[MicroblockSynchronizerSettings](s"$configPath.micro-block-synchronizer")
    val historyReplierSettings = config.as[HistoryReplierSettings](s"$configPath.history-replier")
    val utxSynchronizerSettings = config.as[UtxSynchronizerSettings](s"$configPath.utx-synchronizer")

    SynchronizationSettings(maxRollback, maxChainLength, synchronizationTimeout, scoreTTL, remoteScoreDebounce,
      invalidBlocksStorage, microBlockSynchronizer, historyReplierSettings, utxSynchronizerSettings)
  }
}