package io.lunes.network.client

import java.io.IOException
import java.net.InetSocketAddress
import java.nio.channels.ClosedChannelException
import java.util.concurrent.atomic.AtomicLong

import io.lunes.network.RawBytes
import io.netty.channel.Channel
import io.netty.channel.group.DefaultChannelGroup
import io.netty.util.concurrent.GlobalEventExecutor
import scorex.utils.ScorexLogging

import scala.concurrent.{Future, Promise}

/**
  *
  * @param chainId
  * @param name
  * @param nonce
  */
class NetworkSender(chainId: Char, name: String, nonce: Long) extends ScorexLogging {

  private val allChannels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE)
  private val client = new NetworkClient(chainId, name, nonce, allChannels)

  /**
    *
    * @param address
    * @return
    */
  def connect(address: InetSocketAddress): Future[Channel] = {
    client.connect(address)
  }

  /**
    *
    * @param channel
    * @param messages
    * @return
    */
  def send(channel: Channel, messages: RawBytes*): Future[Unit] = {
    if (channel.isOpen) {
      val p = Promise[Unit]
      val counter = new AtomicLong(messages.size)

      messages.foreach { msg =>
        channel.write(msg).addListener { (f: io.netty.util.concurrent.Future[Void]) =>
          if (!f.isSuccess) {
            val cause = Option(f.cause()).getOrElse(new IOException("Can't send a message to the channel"))
            log.error(s"Can't send a message to the channel: $msg", cause)
          }

          if (counter.decrementAndGet() == 0) {
            p.success(())
          }
        }
      }
      channel.flush()

      p.future
    } else {
      Future.failed(new ClosedChannelException)
    }
  }

  /**
    *
    */
  def close(): Unit = client.shutdown()
}
