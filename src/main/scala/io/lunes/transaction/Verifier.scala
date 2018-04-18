package io.lunes.transaction

import io.lunes.state2.reader.SnapshotStateReader

object Verifier {

  def apply(s: SnapshotStateReader, currentBlockHeight: Int)(tx: Transaction): Either[ValidationError, Transaction] = tx match {
    case _: GenesisTransaction => Right(tx)
    case stx: SignedTransaction => stx.signaturesValid()
  }
}

