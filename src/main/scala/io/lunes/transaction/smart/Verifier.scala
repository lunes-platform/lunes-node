package io.lunes.transaction.smart

import cats.syntax.all._
import io.lunes.crypto
import io.lunes.state._
import io.lunes.transaction.ValidationError.{
  GenericError,
  ScriptExecutionError,
  TransactionNotAllowedByScript
}
import io.lunes.transaction._
import io.lunes.transaction.assets._
import io.lunes.transaction.smart.script.{Script, ScriptRunner}
import io.lunes.transaction.transfer._

object Verifier {

  def apply(blockchain: Blockchain, currentBlockHeight: Int)(
      tx: Transaction): Either[ValidationError, Transaction] =
    (tx match {
      case _: GenesisTransaction => Right(tx)
      case pt: ProvenTransaction =>
        (pt, blockchain.accountScript(pt.sender)) match {
          case (_, Some(script)) =>
            verify(blockchain, script, currentBlockHeight, pt)
          case (stx: SignedTransaction, None) => stx.signaturesValid()
          case _                              => verifyAsEllipticCurveSignature(pt)
        }
    }).flatMap(tx => {
      for {
        assetId <- tx match {
          case t: TransferTransaction     => t.assetId
          case t: MassTransferTransaction => t.assetId
          case t: BurnTransaction         => Some(t.assetId)
          case t: ReissueTransaction      => Some(t.assetId)
          case _                          => None
        }

        script <- blockchain.assetDescription(assetId).flatMap(_.script)
      } yield verify(blockchain, script, currentBlockHeight, tx)
    }.getOrElse(Either.right(tx)))

  def verify[T <: Transaction](blockchain: Blockchain,
                               script: Script,
                               height: Int,
                               transaction: T): Either[ValidationError, T] = {
    ScriptRunner[Boolean, T](height, transaction, blockchain, script) match {
      case (ctx, Left(execError)) =>
        Left(ScriptExecutionError(transaction, execError, ctx.letDefs))
      case (ctx, Right(false)) =>
        Left(TransactionNotAllowedByScript(transaction, ctx.letDefs.filter({
          case (_, lv) => lv.evaluated.read()
        })))
      case (_, Right(true)) => Right(transaction)
    }
  }

  def verifyAsEllipticCurveSignature[T <: ProvenTransaction](
      pt: T): Either[ValidationError, T] =
    pt.proofs.proofs match {
      case p :: Nil =>
        Either.cond(
          crypto.verify(p.arr, pt.bodyBytes(), pt.sender.publicKey),
          pt,
          GenericError(
            s"Script doesn't exist and proof doesn't validate as signature for $pt"))
      case _ =>
        Left(GenericError(
          "Transactions from non-scripted accounts must have exactly 1 proof"))
    }

}
