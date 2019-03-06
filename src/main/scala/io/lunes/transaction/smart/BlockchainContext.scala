package io.lunes.transaction.smart

import cats.kernel.Monoid
import io.lunes.lang.Global
import io.lunes.lang.v1.evaluator.ctx.EvaluationContext
import io.lunes.lang.v1.evaluator.ctx.impl.lunes.LunesContext
import io.lunes.lang.v1.evaluator.ctx.impl.{CryptoContext, PureContext}
import io.lunes.state._
import monix.eval.Coeval
import io.lunes.transaction._

object BlockchainContext {

  private val baseContext =
    Monoid.combine(PureContext.instance, CryptoContext.build(Global))

  def build(nByte: Byte,
            tx: Coeval[Transaction],
            h: Coeval[Int],
            blockchain: Blockchain): EvaluationContext =
    Monoid.combine(
      baseContext,
      LunesContext.build(new LunesEnvironment(nByte, tx, h, blockchain)))
}
