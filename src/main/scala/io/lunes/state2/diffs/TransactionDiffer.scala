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

  def typeOfTx[T <: Transaction](tx: T): String =
    tx match {
      case _: MassTransferTransaction => "Mass Transfer"
      case _: CreateAliasTransaction  => "Create Alias"
      case _: LeaseCancelTransaction  => "Lease Cancel"
      case _: ExchangeTransaction     => "Exchange"
      case _: RegistryTransaction     => "Registry"
      case _: TransferTransaction     => "Transfer"
      case _: GenesisTransaction      => "Genesis"
      case _: PaymentTransaction      => "Payment"
      case _: ReissueTransaction      => "Reissue"
      case _: IssueTransaction        => "Issue"
      case _: LeaseTransaction        => "Lease"
      case _: BurnTransaction         => "Burn"
    }

  def apply(
    settings: FunctionalitySettings,
    prevBlockTimestamp: Option[Long],
    currentBlockTimestamp: Long,
    currentBlockHeight: Int
  )(s: SnapshotStateReader, fp: FeatureProvider, tx: Transaction): Either[ValidationError, Diff] = {
    println("NEW TRANSACTION")
    println(s"[+1] ${typeOfTx(tx)}")
    for {
      t0 <- Verifier(s, currentBlockHeight)(tx)
      t1 <- CommonValidation.disallowTxFromFuture(settings, currentBlockTimestamp, t0)
      t2 <- CommonValidation.disallowTxFromPast(prevBlockTimestamp, t1)
      t3 <- CommonValidation.disallowBeforeActivationTime(fp, currentBlockHeight, t2)
      t4 <- CommonValidation.disallowDuplicateIds(s, settings, currentBlockHeight, t3)
      t5 <- CommonValidation.disallowSendingGreaterThanBalance(s, settings, currentBlockTimestamp, t4)
      t6 <- CommonValidation.banAddress(currentBlockHeight, tx)
      diff <- t6 match {
                case gtx: GenesisTransaction =>
                  GenesisTransactionDiff(
                    currentBlockHeight
                  )(gtx)
                case ptx: PaymentTransaction =>
                  PaymentTransactionDiff(
                    s,
                    currentBlockHeight,
                    settings,
                    currentBlockTimestamp
                  )(ptx)
                case itx: IssueTransaction =>
                  AssetTransactionsDiff.issue(
                    currentBlockHeight
                  )(itx)
                case rtx: ReissueTransaction =>
                  AssetTransactionsDiff.reissue(
                    s,
                    settings,
                    currentBlockTimestamp,
                    currentBlockHeight
                  )(rtx)
                case btx: BurnTransaction =>
                  AssetTransactionsDiff.burn(
                    s,
                    currentBlockHeight
                  )(btx)
                case ttx: TransferTransaction =>
                  TransferTransactionDiff(
                    s,
                    settings,
                    currentBlockTimestamp,
                    currentBlockHeight
                  )(ttx)
                case rdtx: RegistryTransaction =>
                  RegistryTransactionDiff(
                    s,
                    settings,
                    currentBlockTimestamp,
                    currentBlockHeight
                  )(rdtx)
                case mtx: MassTransferTransaction =>
                  MassTransferTransactionDiff(
                    s,
                    currentBlockTimestamp,
                    currentBlockHeight
                  )(mtx)
                case ltx: LeaseTransaction =>
                  LeaseTransactionsDiff.lease(
                    s,
                    currentBlockHeight
                  )(ltx)
                case ltx: LeaseCancelTransaction =>
                  LeaseTransactionsDiff.leaseCancel(
                    s,
                    settings,
                    currentBlockTimestamp,
                    currentBlockHeight
                  )(ltx)
                case etx: ExchangeTransaction =>
                  ExchangeTransactionDiff(
                    s,
                    currentBlockHeight
                  )(etx)
                case atx: CreateAliasTransaction =>
                  CreateAliasTransactionDiff(
                    currentBlockHeight
                  )(atx)
                case _ => Left(UnsupportedTransactionType)
              }
      positiveDiff <- BalanceDiffValidation(s, settings)(diff)
    } yield positiveDiff
  }.left.map(TransactionValidationError(_, tx))
}
