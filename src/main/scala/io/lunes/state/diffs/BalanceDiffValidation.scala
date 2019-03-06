package io.lunes.state.diffs

import cats.implicits._
import io.lunes.metrics.Instrumented
import io.lunes.settings.FunctionalitySettings
import io.lunes.state.{Blockchain, ByteStr, Diff, LeaseBalance, Portfolio}
import scorex.account.Address
import io.lunes.transaction.Transaction
import io.lunes.transaction.ValidationError.AccountBalanceError
import scorex.utils.ScorexLogging

import scala.util.{Left, Right}

object BalanceDiffValidation extends ScorexLogging with Instrumented {

  def apply[T <: Transaction](
      b: Blockchain,
      currentHeight: Int,
      fs: FunctionalitySettings)(d: Diff): Either[AccountBalanceError, Diff] = {

    val changedAccounts = d.portfolios.keySet

    val positiveBalanceErrors: Map[Address, String] = changedAccounts
      .flatMap(acc => {
        val portfolioDiff = d.portfolios(acc)
        val oldPortfolio = b.portfolio(acc)

        val newPortfolio = oldPortfolio.combine(portfolioDiff)

        val err = if (newPortfolio.balance < 0) {
          Some(
            s"negative lunes balance: $acc, old: ${oldPortfolio.balance}, new: ${newPortfolio.balance}")
        } else if (newPortfolio.assets.values.exists(_ < 0)) {
          Some(
            s"negative asset balance: $acc, new portfolio: ${negativeAssetsInfo(newPortfolio)}")
        } else if (newPortfolio.effectiveBalance < 0) {
          Some(s"negative effective balance: $acc, old: ${leaseLunesInfo(
            oldPortfolio)}, new: ${leaseLunesInfo(newPortfolio)}")
        } else if (newPortfolio.balance < newPortfolio.lease.out && currentHeight > fs.allowLeasedBalanceTransferUntilHeight) {
          Some(s"leased being more than own: $acc, old: ${leaseLunesInfo(
            oldPortfolio)}, new: ${leaseLunesInfo(newPortfolio)}")
        } else None
        err.map(acc -> _)
      })
      .toMap

    if (positiveBalanceErrors.isEmpty) {
      Right(d)
    } else {
      Left(AccountBalanceError(positiveBalanceErrors))
    }
  }

  private def leaseLunesInfo(p: Portfolio): (Long, LeaseBalance) =
    (p.balance, p.lease)

  private def negativeAssetsInfo(p: Portfolio): Map[ByteStr, Long] =
    p.assets.filter(_._2 < 0)
}
