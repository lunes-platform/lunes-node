package io.lunes.state2.diffs

import cats.implicits._
import io.lunes.settings.FunctionalitySettings
import io.lunes.state2._
import io.lunes.state2.reader.SnapshotStateReader
import scorex.account.Address
import io.lunes.transaction.ValidationError
import io.lunes.transaction.ValidationError.GenericError
import io.lunes.transaction.assets.TransferTransaction

import scala.util.Right

/**
  *
  */
object TransferTransactionDiff {
  def apply(state: SnapshotStateReader, s: FunctionalitySettings, blockTime: Long, height: Int)(tx: TransferTransaction): Either[ValidationError, Diff] = {
    val sender = Address.fromPublicKey(tx.sender.publicKey)

    val isInvalidEi = for {
      recipient <- state.resolveAliasEi(tx.recipient)
      portfolios = (
        tx.assetId match {
          case None => Map(sender -> Portfolio(-tx.amount, LeaseInfo.empty, Map.empty)).combine(
            Map(recipient -> Portfolio(tx.amount, LeaseInfo.empty, Map.empty))
          )
          case Some(aid) =>
            Map(sender -> Portfolio(0, LeaseInfo.empty, Map(aid -> -tx.amount))).combine(
              Map(recipient -> Portfolio(0, LeaseInfo.empty, Map(aid -> tx.amount)))
            )
        }).combine(
        tx.feeAssetId match {
          case None => Map(sender -> Portfolio(-tx.fee, LeaseInfo.empty, Map.empty))
          case Some(aid) =>
            Map(sender -> Portfolio(0, LeaseInfo.empty, Map(aid -> -tx.fee)))
        }
      )
      assetIssued = tx.assetId match {
        case None => true
        case Some(aid) => state.assetInfo(aid).isDefined
      }
      feeAssetIssued = tx.feeAssetId match {
        case None => true
        case Some(aid) => state.assetInfo(aid).isDefined
      }
    } yield (portfolios, blockTime > s.allowUnissuedAssetsUntil && !(assetIssued && feeAssetIssued))

    isInvalidEi match {
      case Left(e) => Left(e)
      case Right((portfolios, invalid)) =>
        if (invalid)
          Left(GenericError(s"Unissued assets are not allowed after allowUnissuedAssetsUntil=${s.allowUnissuedAssetsUntil}"))
        else
          Right(Diff(height, tx, portfolios))
    }
  }
}