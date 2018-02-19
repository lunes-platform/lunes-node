package io.lunes.state2.diffs

import cats.implicits._
import io.lunes.metrics.Instrumented
import io.lunes.settings.FunctionalitySettings
import io.lunes.state2.reader.SnapshotStateReader
import io.lunes.state2.{ByteStr, Diff, LeaseInfo, Portfolio}
import scorex.account.Address
import io.lunes.transaction.Transaction
import io.lunes.transaction.ValidationError.AccountBalanceError
import scorex.utils.ScorexLogging

import scala.util.{Left, Right}

object BalanceDiffValidation extends ScorexLogging with Instrumented {

  def apply[T <: Transaction](s: SnapshotStateReader, fs: FunctionalitySettings)(d: Diff): Either[AccountBalanceError, Diff] = {

    val changedAccounts = d.portfolios.keySet

    val positiveBalanceErrors: Map[Address, String] = changedAccounts.flatMap(acc => {

      val portfolioDiff = d.portfolios(acc)
      val oldPortfolio = s.partialPortfolio(acc, portfolioDiff.assets.keySet)
      val newPortfolio = oldPortfolio.combine(portfolioDiff)

      val err = if (newPortfolio.balance < 0) {
        Some(s"negative lunes balance: $acc, old: ${oldPortfolio.balance}, new: ${newPortfolio.balance}")
      } else if (newPortfolio.assets.values.exists(_ < 0)) {
        Some(s"negative asset balance: $acc, new portfolio: ${negativeAssetsInfo(newPortfolio)}")
      } else if (newPortfolio.effectiveBalance < 0) {
        Some(s"negative effective balance: $acc, old: ${leaseLunesInfo(oldPortfolio)}, new: ${leaseLunesInfo(newPortfolio)}")
      } else if (newPortfolio.balance < newPortfolio.leaseInfo.leaseOut && s.height > fs.allowLeasedBalanceTransferUntilHeight) {
        Some(s"leased being more than own: $acc, old: ${leaseLunesInfo(oldPortfolio)}, new: ${leaseLunesInfo(newPortfolio)}")
      } else None
      err.map(acc -> _)
    }).toMap

    if (positiveBalanceErrors.isEmpty) {
      Right(d)
    } else {
      Left(AccountBalanceError(positiveBalanceErrors))
    }
  }

  private def leaseLunesInfo(p: Portfolio): (Long, LeaseInfo) = (p.balance, p.leaseInfo)

  private def negativeAssetsInfo(p: Portfolio): Map[ByteStr, Long] = p.assets.filter(_._2 < 0)
}
