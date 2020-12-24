package io.lunes.network

import java.net.InetSocketAddress

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandlerAdapter}
import scorex.utils.ScorexLogging

import scala.concurrent.duration.FiniteDuration

/** Class for Peer-to-Peer Sunchronization.
  * @constructor Creates a Peer-to-Peer Synchronizer object.
  * @param peerDatabase Sets de Peer Database.
  * @param peerRequestInterval Sets the Requests Time Out Interval.
  */
class PeerSynchronizer(peerDatabase: PeerDatabase, peerRequestInterval: FiniteDuration)
  extends ChannelInboundHandlerAdapter with ScorexLogging {

  private var peersRequested = false
  private var declaredAddress = Option.empty[InetSocketAddress]

  /** Requests for Peer Connections.
    * @param ctx Inputs Channel Handler Context.
    */
  //TODO: TailRec
  def requestPeers(ctx: ChannelHandlerContext): Unit = if (ctx.channel().isActive) {
    peersRequested = true
    ctx.writeAndFlush(GetPeers)

    ctx.executor().schedule(peerRequestInterval) {
      requestPeers(ctx)
    }
  }

  /** Reads from Channel Context.
    * @param ctx Inputs Channel Context.
    * @param msg Reference Message Object.
    */
  override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef): Unit = {
    declaredAddress.foreach(peerDatabase.touch)
    msg match {
      case hs: Handshake =>
        val rda = for {
          rda <- hs.declaredAddress
          rdaAddress <- Option(rda.getAddress)
          ctxAddress <- ctx.remoteAddress.map(_.getAddress)
          if rdaAddress == ctxAddress
        } yield rda

        rda match {
          case None => log.debug(s"${id(ctx)} Declared address $rda does not match actual remote address ${ctx.remoteAddress.map(_.getAddress)}")
          case Some(x) =>
            log.trace(s"${id(ctx)} Touching declared address")
            peerDatabase.touch(x)
            declaredAddress = Some(x)
        }

        requestPeers(ctx)
        super.channelRead(ctx, msg)
      case GetPeers =>
        ctx.writeAndFlush(KnownPeers(peerDatabase.knownPeers.keys.toSeq))
      case KnownPeers(peers) if peersRequested =>
        peersRequested = false
        val (added, notAdded) = peers.partition(peerDatabase.addCandidate)
        log.trace(s"${id(ctx)} Added peers: ${format(added)}, not added peers: ${format(notAdded)}")
      case KnownPeers(peers) =>
        log.trace(s"${id(ctx)} Got unexpected list of known peers containing ${peers.size} entries")
      case _ =>
        super.channelRead(ctx, msg)
    }
  }

  private def format[T](xs: Iterable[T]): String = xs.mkString("[", ", ", "]")
}

/** PeerSynchronizer Companion Object */
object PeerSynchronizer {

  @Sharable
  class NoopPeerSynchronizer extends ChannelInboundHandlerAdapter {

    override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef): Unit = {
      msg match {
        case GetPeers =>
        case KnownPeers(_) =>
        case _ =>
          super.channelRead(ctx, msg)
      }
    }
  }

  val Disabled = new NoopPeerSynchronizer()

}