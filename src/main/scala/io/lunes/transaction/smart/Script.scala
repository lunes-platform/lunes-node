package io.lunes.transaction.smart

import io.lunes.crypto
import io.lunes.lang.Serde
import io.lunes.lang.Terms.Typed
import io.lunes.state2.ByteStr
import monix.eval.Coeval
import scodec.Attempt.{Failure, Successful}
import scodec.DecodeResult
import io.lunes.transaction.ValidationError.ScriptParseError
import io.lunes.transaction.smart.Script._

case class Script(script: Typed.EXPR) {

  val version: Byte = 1

  val text: String = script.toString

  val bytes: Coeval[ByteStr] = Coeval.evalOnce {
    val s = Array(version) ++ Serde.codec.encode(script).require.toByteArray
    ByteStr(s ++ crypto.secureHash(s).take(checksumLength))
  }

  override def toString: String = s"Script(base58=${bytes()}, $text"
}

object Script {

  val checksumLength = 4

  def fromBytes(bytes: Array[Byte]): Either[ScriptParseError, Script] = {
    val checkSum = bytes.takeRight(checksumLength)
    val computedCheckSum = crypto.secureHash(bytes.dropRight(checksumLength)).take(checksumLength)
    val version = bytes.head
    val scriptBytes = bytes.drop(1).dropRight(checksumLength)

    for {
      _ <- Either.cond(checkSum.sameElements(computedCheckSum), (), ScriptParseError("Invalid checksum"))
      _ <- Either.cond(version == 1, (), ScriptParseError(s"Invalid version: $version"))
      r <- Serde.codec.decode(scodec.bits.BitVector(scriptBytes)) match {
        case Successful(value: DecodeResult[Typed.EXPR]) => Right(Script(value.value))
        case Failure(cause) => Left(ScriptParseError(cause.toString))
      }
    } yield r
  }
}
