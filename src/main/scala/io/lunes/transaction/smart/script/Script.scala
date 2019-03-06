package io.lunes.transaction.smart.script

import io.lunes.lang.ScriptVersion.Versions.V1
import io.lunes.lang.Versioned
import io.lunes.lang.v1.compiler.Terms
import io.lunes.state.ByteStr
import monix.eval.Coeval
import io.lunes.utils.Base64
import io.lunes.transaction.ValidationError.ScriptParseError

trait Script extends Versioned {
  val expr: version.ExprT
  val text: String
  val bytes: Coeval[ByteStr]

  override def equals(obj: scala.Any): Boolean = obj match {
    case that: Script => version == that.version && expr == that.expr
    case _            => false
  }

  override def hashCode(): Int = version.value * 31 + expr.hashCode()
}

object Script {

  val checksumLength = 4

  def fromBase64String(str: String): Either[ScriptParseError, Script] =
    for {
      bytes <- Base64
        .decode(str)
        .toEither
        .left
        .map(ex =>
          ScriptParseError(s"Unable to decode base64: ${ex.getMessage}"))
      script <- ScriptReader.fromBytes(bytes)
    } yield script

  object Expr {
    def unapply(arg: Script): Option[Terms.EXPR] = {
      if (arg.version == V1) Some(arg.expr.asInstanceOf[Terms.EXPR])
      else None
    }
  }
}
