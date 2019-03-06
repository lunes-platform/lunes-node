package io.lunes.state.diffs

import io.lunes.features.BlockchainFeatures
import io.lunes.state.{Blockchain, Diff, LeaseBalance, Portfolio}
import io.lunes.transaction.ValidationError.GenericError
import io.lunes.transaction.{CreateAliasTransaction, ValidationError}
import io.lunes.features.FeatureProvider._

import scala.util.Right

object CreateAliasTransactionDiff {
  def apply(blockchain: Blockchain, height: Int)(
      tx: CreateAliasTransaction): Either[ValidationError, Diff] =
    if (blockchain.isFeatureActivated(
          BlockchainFeatures.DataTransaction,
          height) && !blockchain.canCreateAlias(tx.alias))
      Left(GenericError("Alias already claimed"))
    else
      Right(
        Diff(height = height,
             tx = tx,
             portfolios = Map(
               tx.sender.toAddress -> Portfolio(-tx.fee,
                                                LeaseBalance.empty,
                                                Map.empty)),
             aliases = Map(tx.alias -> tx.sender.toAddress)))
}
