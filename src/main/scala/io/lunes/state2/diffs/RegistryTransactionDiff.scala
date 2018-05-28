package io.lunes.state2.diffs

import cats.implicits._
import io.lunes.settings.FunctionalitySettings
import io.lunes.state2._
import io.lunes.state2.reader.SnapshotStateReader
import io.lunes.transaction.ValidationError
import io.lunes.transaction.ValidationError.GenericError
import io.lunes.transaction.assets.RegistryTransaction
import scorex.account.Address

import scala.util.Right

/**
  *
  */
object RegistryTransactionDiff {
  def apply(state: SnapshotStateReader, s: FunctionalitySettings, blockTime: Long, height: Int)(tx: RegistryTransaction): Either[ValidationError, Diff] = {
    val sender = Address.fromPublicKey(tx.sender.publicKey)

    val isInvalidEi = for {
      recipient <- state.resolveAliasEi(tx.recipient)
      portfolios = Map(sender -> Portfolio(-tx.amount, LeaseInfo.empty, Map.empty)).combine( Map(recipient -> Portfolio(tx.amount, LeaseInfo.empty, Map.empty))).combine( Map(sender -> Portfolio(-tx.fee, LeaseInfo.empty, Map.empty)))
      assetIssued = true
      feeAssetIssued = true
    } yield (portfolios, (blockTime > s.allowUnissuedAssetsUntil && !(assetIssued && feeAssetIssued)))

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