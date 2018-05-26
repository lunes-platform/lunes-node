package io.lunes.network

import java.util
import java.util.concurrent.{ConcurrentMap, TimeUnit}

import io.lunes.settings.Constants
import io.lunes.network.Handshake.InvalidHandshakeException
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel._
import io.netty.channel.group.ChannelGroup
import io.netty.handler.codec.ReplayingDecoder
import io.netty.util.AttributeKey
import io.netty.util.concurrent.ScheduledFuture
import scorex.utils.ScorexLogging

import scala.concurrent.duration.FiniteDuration

/**
  *
  * @param peerDatabase
  */
class HandshakeDecoder(peerDatabase: PeerDatabase) extends ReplayingDecoder[Void] with ScorexLogging {
  override def decode(ctx: ChannelHandlerContext, in: ByteBuf, out: util.List[AnyRef]): Unit = try {
    out.add(Handshake.decode(in))
    ctx.pipeline().remove(this)
  } catch {
    case e: InvalidHandshakeException => block(ctx, e)
  }

  protected def block(ctx: ChannelHandlerContext, e: Throwable): Unit = {
    peerDatabase.blacklistAndClose(ctx.channel(), e.getMessage)
  }
}

/**
  *
  */
case object HandshakeTimeoutExpired

/**
  *
  * @param handshakeTimeout
  */
class HandshakeTimeoutHandler(handshakeTimeout: FiniteDuration) extends ChannelInboundHandlerAdapter with ScorexLogging {
  private var timeout: Option[ScheduledFuture[_]] = None

  private def cancelTimeout(): Unit = timeout.foreach(_.cancel(true))

  /**
    *
    * @param ctx
    */
  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    log.trace(s"${id(ctx)} Scheduling handshake timeout, timeout = $handshakeTimeout")
    timeout = Some(ctx.channel().eventLoop().schedule({ () =>
      log.trace(s"${id(ctx)} Firing handshake timeout expired")
      ctx.fireChannelRead(HandshakeTimeoutExpired)
    }, handshakeTimeout.toMillis, TimeUnit.MILLISECONDS))

    super.channelActive(ctx)
  }

  /**
    *
    * @param ctx
    */
  override def channelInactive(ctx: ChannelHandlerContext): Unit = {
    cancelTimeout()
    super.channelInactive(ctx)
  }

  /**
    *
    * @param ctx
    * @param msg
    */
  override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef): Unit = msg match {
    case hs: Handshake =>
      cancelTimeout()
      super.channelRead(ctx, hs)
    case other =>
      super.channelRead(ctx, other)
  }
}

/**
  *
  * @param localHandshake
  * @param establishedConnections
  * @param peerConnections
  * @param peerDatabase
  * @param allChannels
  */
abstract class HandshakeHandler(
                                 localHandshake: Handshake,
                                 establishedConnections: ConcurrentMap[Channel, PeerInfo],
                                 peerConnections: ConcurrentMap[PeerKey, Channel],
                                 peerDatabase: PeerDatabase,
                                 allChannels: ChannelGroup) extends ChannelInboundHandlerAdapter with ScorexLogging {

  import HandshakeHandler._

  /**
    *
    * @param ctx
    * @param msg
    */
  override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef): Unit = msg match {
    case HandshakeTimeoutExpired =>
      peerDatabase.blacklistAndClose(ctx.channel(), "Timeout expired while waiting for handshake")
    case remoteHandshake: Handshake =>
      if (localHandshake.applicationName != remoteHandshake.applicationName)
        peerDatabase.blacklistAndClose(ctx.channel(), s"Remote application name ${remoteHandshake.applicationName} does not match local ${localHandshake.applicationName}")
      else if (!versionIsSupported(remoteHandshake.applicationVersion))
        peerDatabase.blacklistAndClose(ctx.channel(), s"Remote application version ${remoteHandshake.applicationVersion} is not supported")
      else {
        PeerKey(ctx, remoteHandshake.nodeNonce) match {
          case None =>
            log.warn(s"Can't get PeerKey from ${id(ctx)}")
            ctx.close()

          case Some(key) =>
            val previousPeer = peerConnections.putIfAbsent(key, ctx.channel())
            if (previousPeer == null) {
              log.info(s"${id(ctx)} Accepted handshake $remoteHandshake")
              removeHandshakeHandlers(ctx, this)
              establishedConnections.put(ctx.channel(), peerInfo(remoteHandshake, ctx.channel()))

              ctx.channel().attr(NodeNameAttributeKey).set(remoteHandshake.nodeName)
              ctx.channel().closeFuture().addListener { f: ChannelFuture =>
                peerConnections.remove(key, f.channel())
                establishedConnections.remove(f.channel())
                log.trace(s"${id(f.channel())} was closed")
              }

              connectionNegotiated(ctx)
              ctx.fireChannelRead(msg)
            } else {
              val peerAddress = ctx.remoteAddress.getOrElse("unknown")
              log.debug(s"${id(ctx)} Already connected to peer $peerAddress with nonce ${remoteHandshake.nodeNonce} on channel ${id(previousPeer)}")
              ctx.close()
            }
        }
      }
    case _ => super.channelRead(ctx, msg)
  }

  protected def connectionNegotiated(ctx: ChannelHandlerContext): Unit = {
    ctx.channel().closeFuture().addListener((_: ChannelFuture) => allChannels.remove(ctx.channel()))
    allChannels.add(ctx.channel())
  }

  protected def sendLocalHandshake(ctx: ChannelHandlerContext): Unit = {
    ctx.writeAndFlush(localHandshake.encode(ctx.alloc().buffer()))
  }
}

/**
  *
  */
object HandshakeHandler extends ScorexLogging {

  val NodeNameAttributeKey = AttributeKey.newInstance[String]("name")

  /**
    *
    * @param remoteVersion
    * @return
    */
  def versionIsSupported(remoteVersion: (Int, Int, Int)): Boolean =
    remoteVersion._1 == Constants.MinimalVersion._1 && remoteVersion._2 >= Constants.MinimalVersion._2 // && remoteVersion._3 >= Constants.MinimalVersion._3

  /**
    *
    * @param ctx
    * @param thisHandler
    */
  def removeHandshakeHandlers(ctx: ChannelHandlerContext, thisHandler: ChannelHandler): Unit = {
    ctx.pipeline().remove(classOf[HandshakeTimeoutHandler])
    ctx.pipeline().remove(thisHandler)
  }

  /**
    *
    * @param remoteHandshake
    * @param channel
    * @return
    */
  def peerInfo(remoteHandshake: Handshake, channel: Channel): PeerInfo = PeerInfo(
    channel.remoteAddress(),
    remoteHandshake.declaredAddress,
    remoteHandshake.applicationName,
    remoteHandshake.applicationVersion,
    remoteHandshake.nodeName,
    remoteHandshake.nodeNonce)

  /**
    *
    * @param handshake
    * @param establishedConnections
    * @param peerConnections
    * @param peerDatabase
    * @param allChannels
    */
  @Sharable
  class Server(
                handshake: Handshake,
                establishedConnections: ConcurrentMap[Channel, PeerInfo],
                peerConnections: ConcurrentMap[PeerKey, Channel],
                peerDatabase: PeerDatabase,
                allChannels: ChannelGroup)
    extends HandshakeHandler(handshake, establishedConnections, peerConnections, peerDatabase, allChannels) {
    override protected def connectionNegotiated(ctx: ChannelHandlerContext): Unit = {
      sendLocalHandshake(ctx)
      super.connectionNegotiated(ctx)
    }
  }

  /**
    *
    * @param handshake
    * @param establishedConnections
    * @param peerConnections
    * @param peerDatabase
    * @param allChannels
    */
  @Sharable
  class Client(
                handshake: Handshake,
                establishedConnections: ConcurrentMap[Channel, PeerInfo],
                peerConnections: ConcurrentMap[PeerKey, Channel],
                peerDatabase: PeerDatabase,
                allChannels: ChannelGroup)
    extends HandshakeHandler(handshake, establishedConnections, peerConnections, peerDatabase, allChannels) {
    override protected def channelActive(ctx: ChannelHandlerContext): Unit = {
      sendLocalHandshake(ctx)
      super.channelActive(ctx)
    }
  }

}
