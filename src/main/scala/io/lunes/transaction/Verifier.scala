package io.lunes.transaction


import io.lunes.crypto
import io.lunes.transaction.ValidationError.GenericError
import io.lunes.state2.reader.SnapshotStateReader

/**
  *
  */
object Verifier {
  def apply(s: SnapshotStateReader, currentBlockHeight: Int)(tx: Transaction): Either[ValidationError, Transaction] = tx match {
    case _: GenesisTransaction => Right(tx)
    case pt: ProvenTransaction => (pt, None) match {
        case (stx: SignedTransaction, None)     => stx.signaturesValid()
        case _                          => verifyAsEllipticCurveSignature(pt)
      }
  }

 def verifyAsEllipticCurveSignature[T <: ProvenTransaction](pt: T): Either[ValidationError, T] =
    Either.cond(
      crypto.verify(pt.proofs.proofs(0).arr, pt.bodyBytes(), pt.sender.publicKey),
      pt,
      GenericError(s"proof doesn't validate as signature for $pt"))

}

