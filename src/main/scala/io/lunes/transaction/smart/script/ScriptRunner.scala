package io.lunes.transaction.smart.script

import cats.implicits._
import io.lunes.lang.v1.evaluator.EvaluatorV1
import io.lunes.lang.v1.evaluator.ctx.EvaluationContext
import io.lunes.lang.ExecutionError
import io.lunes.state._
import monix.eval.Coeval
import scorex.account.AddressScheme
import io.lunes.transaction.Transaction
import io.lunes.transaction.smart.BlockchainContext

object ScriptRunner {

  def apply[A, T <: Transaction](
      height: Int,
      tx: T,
      blockchain: Blockchain,
      script: Script): (EvaluationContext, Either[ExecutionError, A]) =
    script match {
      case Script.Expr(expr) =>
        val ctx = BlockchainContext.build(
          AddressScheme.current.chainId,
          Coeval.evalOnce(tx),
          Coeval.evalOnce(height),
          blockchain
        )
        EvaluatorV1[A](ctx, expr)

      case _ =>
        (EvaluationContext.empty, "Unsupported script version".asLeft[A])
    }

}
