package io.lunes.core.network.utx

/**
  * Transactions Pool Package
  * = Overview =
  *
  * This package provides Transactions interfaces for the Lunes.io platform.
  */

import java.util.concurrent.ConcurrentHashMap

import cats._
import io.lunes.core.network.utx.UtxPoolImpl.PessimisticPortfolios
import io.lunes.features.FeatureProvider
import io.lunes.metrics.Instrumented
import io.lunes.mining.TwoDimensionalMiningConstraint
import io.lunes.settings.{FunctionalitySettings, UtxSettings}
import io.lunes.state2.diffs.TransactionDiffer
import io.lunes.state2.reader.CompositeStateReader.composite
import io.lunes.state2.reader.SnapshotStateReader
import io.lunes.state2.{ByteStr, Diff, Portfolio, StateReader}
import kamon.Kamon
import kamon.metric.instrument.{Time => KamonTime}
import monix.eval.Task
import monix.execution.Scheduler
import scorex.account.Address
import scorex.consensus.TransactionsOrdering
import io.lunes.transaction.ValidationError.{GenericError, SenderIsBlacklisted}
import io.lunes.transaction._
import io.lunes.transaction.assets.{MassTransferTransaction, TransferTransaction}
import scorex.utils.{ScorexLogging, Time}

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.util.{Left, Right}

/**
  * Transactions Pool.
  */
trait UtxPool {

  /**
    * Put New Transaction in a Pool.
    *
    * @param tx provides a [[io.lunes.transaction.Transaction]] input interface.
    * @return Returns Either a true or false value case the transaction is valid or a validation Error otherwise.
    */
  def putIfNew(tx: Transaction): Either[ValidationError, Boolean]

  /**
    * Remove all transactions in the Pool.
    *
    * @param txs provide a interface for [[scala.collection.mutable.Traversable]] of [[io.lunes.transaction.Transaction]].
    */
  def removeAll(txs: Traversable[Transaction]): Unit

  /**
    * Returns a [[io.lunes.state2.Portfolio]] given a Scorex [[scorex.account.Address]].
    * @param addr Scorex [[scorex.account.Address]].
    * @return the [[io.lunes.state2.Portfolio]].
    */
  def portfolio(addr: Address): Portfolio

  /**
    * List all Transactions in the Pool.
    * @return returns a [[scala.collection.immutable.Seq]] of [[io.lunes.transaction.Transaction]].
    */
  def all: Seq[Transaction]

  /**
    * @return returns the Pool size.
    */
  def size: Int

  /**
    * Provides a transaction based on its Id.
    * @param transactionId provides a ByteStr
    * @return returns a Option for a [[io.lunes.transaction.Transaction]].
    */
  def transactionById(transactionId: ByteStr): Option[Transaction]

  /**
    * Package Unconfirmed Constraints in a Tuple of Transactions and Constraints.
    * @param rest [[io.lunes.mining.TwoDimensionalMiningConstraint]] input
    * @param sortInBlock True if Sort for Block.
    * @return Returns a Tuple of a [[scala.collection.immutable.Seq]] of [[io.lunes.transaction.Transaction]] and a [[io.lunes.mining.TwoDimensionalMiningConstraint]].
    */
  def packUnconfirmed(rest: TwoDimensionalMiningConstraint, sortInBlock: Boolean): (Seq[Transaction], TwoDimensionalMiningConstraint)

  /**
    * Provides a interface for batched operations.
    * @param f Annonymous function for processing [[io.lunes.utx.UtxBatchOps]].
    */
  def batched(f: UtxBatchOps => Unit): Unit

}

/**
  * Transaction Batch Operations.
  */
trait UtxBatchOps {
  /** Insert in Batch Transaction if it is new.
    * @param tx Input Transaction.
    * @return Returns Eiher Boolean (case Success) or ValidationError (case Failure).
    */
  def putIfNew(tx: Transaction): Either[ValidationError, Boolean]
}

/** Transaction Pool with Implicit Scheduler.
  * @constructor Creates a Transaction Pool with Implicit Scheduler
  * @param time Sets Time o
  * @param stateReader Sets the StateReader object.
  * @param history Sets the Transaction History.
  * @param featureProvider Sets the FeatureProvider object.
  * @param feeCalculator Sets the FeeCalculator.
  * @param fs Sets the FuncionalitySettings.
  * @param utxSettings Sets Transaction Settings.
  */
class UtxPoolImpl(time: Time,
                  stateReader: StateReader,
                  history: History,
                  featureProvider: FeatureProvider,
                  feeCalculator: FeeCalculator,
                  fs: FunctionalitySettings,
                  utxSettings: UtxSettings) extends ScorexLogging with Instrumented with AutoCloseable with UtxPool {
  outer =>

  private implicit val scheduler: Scheduler = Scheduler.singleThread("utx-pool-cleanup")

  private val transactions = new ConcurrentHashMap[ByteStr, Transaction]()
  private val pessimisticPortfolios = new PessimisticPortfolios

  private val removeInvalid = Task {
    val state = stateReader()
    val transactionsToRemove = transactions.values.asScala.filter(t => state.containsTransaction(t.id()))
    removeAll(transactionsToRemove)
  }.delayExecution(utxSettings.cleanupInterval)

  private val cleanup = removeInvalid.flatMap(_ => removeInvalid).runAsyncLogErr

  /**
    *
    */
  override def close(): Unit = cleanup.cancel()

  private val utxPoolSizeStats = Kamon.metrics.minMaxCounter("utx-pool-size", 500.millis)
  private val processingTimeStats = Kamon.metrics.histogram("utx-transaction-processing-time", KamonTime.Milliseconds)
  private val putRequestStats = Kamon.metrics.counter("utx-pool-put-if-new")

  /** Remove Expired Transactions from the Pool.
    * @param currentTs Sets Current Time Stamp.
    */
  private def removeExpired(currentTs: Long): Unit = {
    def isExpired(tx: Transaction) = (currentTs - tx.timestamp).millis > utxSettings.maxTransactionAge

    transactions
      .values
      .asScala
      .filter(isExpired)
      .foreach { tx =>
        transactions.remove(tx.id())
        pessimisticPortfolios.remove(tx.id())
        utxPoolSizeStats.decrement()
      }
  }

  /** Inserts if New Transaction.
    * @param tx provides a [[io.lunes.transaction.Transaction]] input interface.
    * @return Returns Either a Boolean for the Transaction validity (case Success) or a [[io.lunes.transaction.ValidationError]] (case Failure).
    */
  override def putIfNew(tx: Transaction): Either[ValidationError, Boolean] = putIfNew(stateReader(), tx)

  /** Check if the Transaction is not blacklisted.
    * @param tx Inputs Transaction
    * @return Returns Either a Unit (case Success) or a [[io.lunes.transaction.ValidationError]] (case Failure).
    */
  private def checkNotBlacklisted(tx: Transaction): Either[ValidationError, Unit] = {
    if (utxSettings.blacklistSenderAddresses.isEmpty) {
      Right(())
    } else {
      val sender: Option[String] = tx match {
        case x: Authorized => Some(x.sender.address)
        case _ => None
      }

      sender match {
        case Some(addr) if utxSettings.blacklistSenderAddresses.contains(addr) =>
          val recipients = tx match {
            case tt: TransferTransaction => Seq(tt.recipient)
            case mtt: MassTransferTransaction => mtt.transfers.map(_.address)
            case _ => Seq()
          }
          val allowed =
            recipients.nonEmpty &&
              recipients.forall(r => utxSettings.allowBlacklistedTransferTo.contains(r.stringRepr))
          Either.cond(allowed, (), SenderIsBlacklisted(addr))
        case _ => Right(())
      }
    }
  }

  /** Remove All Transactions in the Pool.
    * @param txs provide a interface for Transversable of [[io.lunes.transaction.Transaction]].
    */
  override def removeAll(txs: Traversable[Transaction]): Unit = {
    txs.view.map(_.id()).foreach { id =>
      Option(transactions.remove(id)).foreach(_ => utxPoolSizeStats.decrement())
      pessimisticPortfolios.remove(id)
    }

    removeExpired(time.correctedTime())
  }

  /** C
    * @param addr Scorex [[scorex.account.Address]].
    * @return the [[io.lunes.state2.Portfolio]].
    */
  override def portfolio(addr: Address): Portfolio = {
    val base = stateReader().accountPortfolio(addr)
    val foundInUtx = pessimisticPortfolios.getAggregated(addr)

    Monoid.combine(base, foundInUtx)
  }

  /**
    *
    * @return returns a [[scala.collection.immutable.Seq]] of [[io.lunes.transaction.Transaction]].
    */
  override def all: Seq[Transaction] = {
    transactions.values.asScala.toSeq.sorted(TransactionsOrdering.InUTXPool)
  }

  override def size: Int = transactions.size

  override def transactionById(transactionId: ByteStr): Option[Transaction] = Option(transactions.get(transactionId))

  override def packUnconfirmed(rest: TwoDimensionalMiningConstraint, sortInBlock: Boolean): (Seq[Transaction], TwoDimensionalMiningConstraint) = {
    val currentTs = time.correctedTime()
    removeExpired(currentTs)
    val s = stateReader()
    val differ = TransactionDiffer(fs, history.lastBlockTimestamp(), currentTs, s.height) _
    val (invalidTxs, reversedValidTxs, _, finalConstraint, _) = transactions
      .values.asScala.toSeq
      .sorted(TransactionsOrdering.InUTXPool)
      .foldLeft((Seq.empty[ByteStr], Seq.empty[Transaction], Monoid[Diff].empty, rest, false)) {
        case (curr@(_, _, _, _, skip), _) if skip => curr
        case ((invalid, valid, diff, currRest, _), tx) =>
          differ(composite(diff.asBlockDiff, s), featureProvider, tx) match {
            case Right(newDiff) =>
              val updatedRest = currRest.put(tx)
              if (updatedRest.isOverfilled) (invalid, valid, diff, currRest, true)
              else (invalid, tx +: valid, Monoid.combine(diff, newDiff), updatedRest, updatedRest.isEmpty)
            case Left(_) =>
              (tx.id() +: invalid, valid, diff, currRest, false)
          }
      }

    invalidTxs.foreach { itx =>
      transactions.remove(itx)
      pessimisticPortfolios.remove(itx)
    }
    val txs = if (sortInBlock) reversedValidTxs.sorted(TransactionsOrdering.InBlock) else reversedValidTxs.reverse
    (txs, finalConstraint)
  }

  override def batched(f: UtxBatchOps => Unit): Unit = f(new BatchOpsImpl(stateReader()))

  private class BatchOpsImpl(s: SnapshotStateReader) extends UtxBatchOps {
    override def putIfNew(tx: Transaction): Either[ValidationError, Boolean] = outer.putIfNew(s, tx)
  }

  private def putIfNew(s: SnapshotStateReader, tx: Transaction): Either[ValidationError, Boolean] = {
    putRequestStats.increment()
    measureSuccessful(processingTimeStats, {
      for {
        _ <- Either.cond(transactions.size < utxSettings.maxSize, (), GenericError("Transaction pool size limit is reached"))
        _ <- checkNotBlacklisted(tx)
        _ <- feeCalculator.enoughFee(tx)
        diff <- TransactionDiffer(fs, history.lastBlockTimestamp(), time.correctedTime(), s.height)(s, featureProvider, tx)
      } yield {
        utxPoolSizeStats.increment()
        pessimisticPortfolios.add(tx.id(), diff)
        Option(transactions.put(tx.id(), tx)).isEmpty
      }
    })
  }

}

object UtxPoolImpl {

  private class PessimisticPortfolios {
    private type Portfolios = Map[Address, Portfolio]
    private val transactionPortfolios = new ConcurrentHashMap[ByteStr, Portfolios]()
    private val transactions = new ConcurrentHashMap[Address, Set[ByteStr]]()

    def add(txId: ByteStr, txDiff: Diff): Unit = {
      val nonEmptyPessimisticPortfolios = txDiff.portfolios
        .mapValues(_.pessimistic)
        .filterNot {
          case (_, portfolio) => portfolio.isEmpty
        }

      if (nonEmptyPessimisticPortfolios.nonEmpty &&
        Option(transactionPortfolios.put(txId, nonEmptyPessimisticPortfolios)).isEmpty) {
        nonEmptyPessimisticPortfolios.keys.foreach { address =>
          transactions.put(address, transactions.getOrDefault(address, Set.empty) + txId)
        }
      }
    }

    def getAggregated(accountAddr: Address): Portfolio = {
      val portfolios = for {
        txId <- transactions.getOrDefault(accountAddr, Set.empty).toSeq
        txPortfolios = transactionPortfolios.getOrDefault(txId, Map.empty[Address, Portfolio])
        txAccountPortfolio <- txPortfolios.get(accountAddr).toSeq
      } yield txAccountPortfolio

      Monoid.combineAll[Portfolio](portfolios)
    }

    def remove(txId: ByteStr): Unit = {
      if (Option(transactionPortfolios.remove(txId)).isDefined) {
        transactions.keySet().asScala.foreach { addr =>
          transactions.put(addr, transactions.getOrDefault(addr, Set.empty) - txId)
        }
      }
    }
  }

}
