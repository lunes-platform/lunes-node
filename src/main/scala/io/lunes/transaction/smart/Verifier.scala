package io.lunes.transaction.smart

import io.lunes.crypto
import io.lunes.lang.Evaluator
import io.lunes.state2.reader.SnapshotStateReader
import monix.eval.Coeval
import io.lunes.transaction.ValidationError.{GenericError, TransactionNotAllowedByScript}
import io.lunes.transaction._

object Verifier {

  def apply(s: SnapshotStateReader, currentBlockHeight: Int)(tx: Transaction): Either[ValidationError, Transaction] = tx match {
    case _: GenesisTransaction => Right(tx)
    case pt: ProvenTransaction =>
      (pt, s.accountScript(pt.sender)) match {
        case (_, Some(script))              => verify(s, script, currentBlockHeight, pt)
        case (stx: SignedTransaction, None) => stx.signaturesValid()
        case _                              => verifyAsEllipticCurveSignature(pt)
      }
  }

  def verify[T <: ProvenTransaction](s: SnapshotStateReader, script: Script, height: Int, transaction: T): Either[ValidationError, T] =
    Evaluator[Boolean](SmartContext.build(Coeval.evalOnce(transaction), Coeval.evalOnce(height), s), script.script) match {
      case Left(execError) => Left(GenericError(s"Script execution error: $execError"))
      case Right(false)    => Left(TransactionNotAllowedByScript(transaction))
      case Right(true)     => Right(transaction)
    }

  def verifyAsEllipticCurveSignature[T <: ProvenTransaction](pt: T): Either[ValidationError, T] =
    Either.cond(
      crypto.verify(pt.proofs.proofs(0).arr, pt.bodyBytes(), pt.sender.publicKey),
      pt,
      GenericError(s"Script doesn't exist and proof doesn't validate as signature for $pt")
    )
}
