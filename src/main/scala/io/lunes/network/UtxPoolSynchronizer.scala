package io.lunes.network

import java.util.concurrent.TimeUnit

import com.google.common.cache.CacheBuilder
import io.lunes.utx.UtxPool
import io.lunes.settings.SynchronizationSettings.UtxSynchronizerSettings
import io.lunes.state2.ByteStr
import io.netty.channel.Channel
import io.netty.channel.group.{ChannelGroup, ChannelMatcher}
import monix.execution.{CancelableFuture, Scheduler}
import io.lunes.transaction.Transaction

/**
  *
  */
object UtxPoolSynchronizer {
  /**
    *
    * @param utx
    * @param settings
    * @param allChannels
    * @param txSource
    * @return
    */
  def start(utx: UtxPool, settings: UtxSynchronizerSettings, allChannels: ChannelGroup, txSource: ChannelObservable[Transaction]): CancelableFuture[Unit] = {
    implicit val scheduler: Scheduler = Scheduler.singleThread("utx-pool-sync")

    val dummy = new Object()
    val knownTransactions = CacheBuilder
      .newBuilder()
      .maximumSize(settings.networkTxCacheSize)
      .expireAfterWrite(settings.networkTxCacheTime.toMillis, TimeUnit.MILLISECONDS)
      .build[ByteStr, Object]

    txSource
      .observeOn(scheduler)
      .bufferTimedAndCounted(settings.maxBufferTime, settings.maxBufferSize)
      .foreach { txBuffer =>
        val toAdd = txBuffer.filter {
          case (_, tx) =>
            val isNew = Option(knownTransactions.getIfPresent(tx.id())).isEmpty
            if (isNew) knownTransactions.put(tx.id(), dummy)
            isNew
        }

        if (toAdd.nonEmpty) {
          utx.batched { ops =>
            toAdd
              .groupBy { case (channel, _) => channel }
              .foreach {
                case (sender, xs) =>
                  val channelMatcher: ChannelMatcher = { (_: Channel) != sender }
                  xs.foreach { case (_, tx) =>
                    ops.putIfNew(tx) match {
                      case Right(true) => allChannels.write(RawBytes(TransactionSpec.messageCode, tx.bytes()), channelMatcher)
                      case _ =>
                    }
                  }
              }
          }
          allChannels.flush()
        }
    }
  }
}
