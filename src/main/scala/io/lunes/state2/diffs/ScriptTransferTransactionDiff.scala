package io.lunes.state2.diffs

import cats.implicits._
import io.lunes.state2._
import io.lunes.state2.reader.SnapshotStateReader
import scorex.account.Address
import io.lunes.transaction.ValidationError
import io.lunes.transaction.ValidationError.GenericError
import io.lunes.transaction.assets.ScriptTransferTransaction

object ScriptTransferTransactionDiff {
  def apply(state: SnapshotStateReader, height: Int)(tx: ScriptTransferTransaction): Either[ValidationError, Diff] = {
    val sender = Address.fromPublicKey(tx.sender.publicKey)
    for {
      recipient <- state.resolveAliasEi(tx.recipient)
      portfolios = (tx.assetId match {
        case None =>
          Map(sender -> Portfolio(-tx.amount, LeaseInfo.empty, Map.empty)).combine(
            Map(recipient -> Portfolio(tx.amount, LeaseInfo.empty, Map.empty))
          )
        case Some(aid) =>
          Map(sender -> Portfolio(0, LeaseInfo.empty, Map(aid -> -tx.amount))).combine(
            Map(recipient -> Portfolio(0, LeaseInfo.empty, Map(aid -> tx.amount)))
          )
      }).combine(Map(sender -> Portfolio(-tx.fee, LeaseInfo.empty, Map.empty)))
      assetIssued = tx.assetId match {
        case None => true
        case Some(aid) => state.assetInfo(aid).isDefined
      }
      _ <- Either.cond(assetIssued, (), GenericError(s"Unissued assets are not allowed"))
    } yield Diff(height, tx, portfolios)
  }
}
