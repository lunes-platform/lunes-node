package io.lunes.state2.diffs

import io.lunes.state2.{Diff, LeaseInfo, Portfolio}
import io.lunes.transaction.ValidationError
import io.lunes.transaction.smart.SetScriptTransaction

import scala.util.Right

object SetScriptTransactionDiff {
  def apply(height: Int)(tx: SetScriptTransaction): Either[ValidationError, Diff] = {Right(Diff(
    height = height,
    tx = tx,
    portfolios = Map(tx.sender.toAddress -> Portfolio(-tx.fee, LeaseInfo.empty, Map.empty)),
    scripts = Map(tx.sender.toAddress -> tx.script)
  ))}
}