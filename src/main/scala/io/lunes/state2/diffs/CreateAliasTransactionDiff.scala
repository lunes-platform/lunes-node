package io.lunes.state2.diffs

import io.lunes.state2.{Diff, LeaseInfo, Portfolio}
import io.lunes.transaction.{CreateAliasTransaction, ValidationError}

import scala.util.Right

object CreateAliasTransactionDiff {
  def apply(height: Int)(tx: CreateAliasTransaction): Either[ValidationError, Diff] = {
    Right(Diff(height = height, tx = tx,
      portfolios = Map(tx.sender.toAddress -> Portfolio(-tx.fee, LeaseInfo.empty, Map.empty)),
      aliases = Map(tx.alias -> tx.sender.toAddress)
    ))
  }
}
