package io.lunes.state2.diffs

import io.lunes.transaction.lease.{LeaseCancelTransaction, LeaseTransaction}
import io.lunes.transaction.ValidationError.UnsupportedTransactionType
import io.lunes.transaction.assets.exchange.ExchangeTransaction
import io.lunes.state2.reader.SnapshotStateReader
import io.lunes.settings.FunctionalitySettings
import io.lunes.features.FeatureProvider
import io.lunes.security.SecurityChecker
import io.lunes.transaction.assets._
import io.lunes.transaction._
import io.lunes.state2._

object TransactionDiffer {

  case class TransactionValidationError(cause: ValidationError, tx: Transaction) extends ValidationError

  def typeOfTx[T <: Transaction](tx: T) =
    tx match {
      case _: MassTransferTransaction => println("[+1] Mass Transfer")
      case _: CreateAliasTransaction  => println("[+1] Create Alias")
      case _: LeaseCancelTransaction  => println("[+1] Lease Cancel")
      case _: ExchangeTransaction     => println("[+1] Exchange")
      case _: RegistryTransaction     => println("[+1] Registry")
      case _: TransferTransaction     => println("[+1] Transfer")
      case _: GenesisTransaction      => println("[+1] Genesis")
      case _: PaymentTransaction      => println("[+1] Payment")
      case _: ReissueTransaction      => println("[+1] Reissue")
      case _: IssueTransaction        => println("[+1] Issue")
      case _: LeaseTransaction        => println("[+1] Lease")
      case _: BurnTransaction         => println("[+1] Burn")
    }

  def apply(
    settings: FunctionalitySettings,
    prevBlockTimestamp: Option[Long],
    currentBlockTimestamp: Long,
    currentBlockHeight: Int
  )(s: SnapshotStateReader, fp: FeatureProvider, tx: Transaction): Either[ValidationError, Diff] = {
    typeOfTx(tx)

    for {
      t0 <- Verifier(s, currentBlockHeight)(tx)
      t1 <- CommonValidation.disallowTxFromFuture(settings, currentBlockTimestamp, t0)
      t2 <- CommonValidation.disallowTxFromPast(prevBlockTimestamp, t1)
      t3 <- CommonValidation.disallowBeforeActivationTime(fp, currentBlockHeight, t2)
      t4 <- CommonValidation.disallowDuplicateIds(s, settings, currentBlockHeight, t3)
      t5 <- CommonValidation.disallowSendingGreaterThanBalance(s, settings, currentBlockTimestamp, t4)
      t6 <- CommonValidation.commonCheckBanAddress(currentBlockHeight, tx)
      diff <- t6 match {
                case tx: GenesisTransaction =>
                  GenesisTransactionDiff(
                    currentBlockHeight
                  )(tx)
                case tx: PaymentTransaction =>
                  PaymentTransactionDiff(
                    s,
                    currentBlockHeight,
                    settings,
                    currentBlockTimestamp
                  )(tx)
                case tx: IssueTransaction =>
                  AssetTransactionsDiff.issue(
                    currentBlockHeight
                  )(tx)
                case tx: ReissueTransaction =>
                  AssetTransactionsDiff.reissue(
                    s,
                    settings,
                    currentBlockTimestamp,
                    currentBlockHeight
                  )(tx)
                case tx: BurnTransaction =>
                  AssetTransactionsDiff.burn(
                    s,
                    currentBlockHeight
                  )(tx)
                case tx: TransferTransaction =>
                  TransferTransactionDiff(
                    s,
                    settings,
                    currentBlockTimestamp,
                    currentBlockHeight
                  )(tx)
                case tx: RegistryTransaction =>
                  RegistryTransactionDiff(
                    s,
                    settings,
                    currentBlockTimestamp,
                    currentBlockHeight
                  )(tx)
                case tx: MassTransferTransaction =>
                  MassTransferTransactionDiff(
                    s,
                    currentBlockTimestamp,
                    currentBlockHeight
                  )(tx)
                case tx: LeaseTransaction =>
                  LeaseTransactionsDiff.lease(
                    s,
                    currentBlockHeight
                  )(tx)
                case tx: LeaseCancelTransaction =>
                  LeaseTransactionsDiff.leaseCancel(
                    s,
                    settings,
                    currentBlockTimestamp,
                    currentBlockHeight
                  )(tx)
                case tx: ExchangeTransaction =>
                  ExchangeTransactionDiff(
                    s,
                    currentBlockHeight
                  )(tx)
                case tx: CreateAliasTransaction =>
                  CreateAliasTransactionDiff(
                    currentBlockHeight
                  )(tx)
                case _ => Left(UnsupportedTransactionType)
              }
      positiveDiff <- BalanceDiffValidation(s, settings)(diff)
    } yield positiveDiff
  }.left.map(TransactionValidationError(_, tx))
}
