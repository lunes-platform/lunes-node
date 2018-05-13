package io.lunes.network

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelDuplexHandler, ChannelHandlerContext, ChannelPromise}
import scorex.block.Block
import scorex.network.message.{Message => ScorexMessage}
import io.lunes.transaction.Transaction
import scorex.utils.ScorexLogging

/**
  *
  * @param settings
  */
@Sharable
class TrafficLogger(settings: TrafficLogger.Settings) extends ChannelDuplexHandler with ScorexLogging {

  import BasicMessagesRepo.specsByClasses

  private val codeOf: AnyRef => Option[Byte] = {
    val aux: PartialFunction[AnyRef, Byte] = {
      case x: RawBytes => x.code
      case _: Transaction => TransactionSpec.messageCode
      case _: BigInt | _: LocalScoreChanged => ScoreSpec.messageCode
      case _: Block | _: BlockForged => BlockSpec.messageCode
      case x: Message => specsByClasses(x.getClass).messageCode
      case _: Handshake => HandshakeSpec.messageCode
    }

    aux.lift
  }

  /**
    *
    * @param ctx
    * @param msg
    * @param promise
    */
  override def write(ctx: ChannelHandlerContext, msg: AnyRef, promise: ChannelPromise): Unit = {
    codeOf(msg).filterNot(settings.ignoreTxMessages).foreach { code =>
      log.trace(s"${id(ctx)} <-- transmitted($code): $msg")
    }

    super.write(ctx, msg, promise)
  }

  /**
    *
    * @param ctx
    * @param msg
    */
  override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef): Unit = {
    codeOf(msg).filterNot(settings.ignoreRxMessages).foreach { code =>
      log.trace(s"${id(ctx)} --> received($code): $msg")
    }

    super.channelRead(ctx, msg)
  }

}

/**
  *
  */
object TrafficLogger {

  /**
    *
    * @param ignoreTxMessages
    * @param ignoreRxMessages
    */
  case class Settings(ignoreTxMessages: Set[ScorexMessage.MessageCode],
                      ignoreRxMessages: Set[ScorexMessage.MessageCode])

}