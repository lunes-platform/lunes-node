package io.lunes.state2.reader

import com.google.common.base.Charsets
import io.lunes.state2._
import scorex.account.{Address, AddressOrAlias, Alias}
import io.lunes.transaction.ValidationError.AliasNotExists
import io.lunes.transaction._
import io.lunes.transaction.assets.IssueTransaction
import io.lunes.transaction.lease.LeaseTransaction
import scorex.utils.{ScorexLogging, Synchronized}

// import scala.annotation.tailrec
import scala.reflect.ClassTag
import scala.util.{Right, Try}

/** The Snapshot Reader Trait */
trait SnapshotStateReader extends Synchronized {

  /** Provides an interface for account Portifolios
    * @return Returns a Map for [[scorex.account.Address]] into [[io.lunes.state2.Portfolio]].
    */
  def accountPortfolios: Map[Address, Portfolio]

  /** Provides an interface for Transaciton Information Retrieval.
    * @param id Inputs the Transaction ID.
    * @return Returns an Option for a Tuple (Int, Option[Transaction]).
    */
  def transactionInfo(id: ByteStr): Option[(Int, Option[Transaction])]

  /** Provides an interface for Transaction containment check.
    * @param id Inputs the Transaction ID.
    * @return Return true if exists.
    */
  def containsTransaction(id: ByteStr): Boolean

  /** Provides an interface for Account Portfolio retrieaval.
    * @param a Inputs an [[scorex.account.Address]].
    * @return Returns the Portfolio object.
    */
  def accountPortfolio(a: Address): Portfolio

  /** Provides an interface for Asset Information retrieval.
    * @param id Inputs Asset ID.
    * @return Returns an Option for [[io.lunes.state2.AssetInfo]].
    */
  def assetInfo(id: ByteStr): Option[AssetInfo]

  /** Provides an interface for Lunes Balance retrieval.
    * @param a Inputs an [[scorex.account.Address]].
    * @return Returns a Tuple (Long, LeaseInfo).
    */
  def lunesBalance(a: Address): (Long, LeaseInfo)

  /** Provides an interface for Asset Balance.
    * @param a Inputs an [[scorex.account.Address]]
    * @param asset Inputs Asset ID.
    * @return Returns the Asset Balance.
    */
  def assetBalance(a: Address, asset: ByteStr): Long

  /** Provides an interface for Height retrieval.
    * @return Returns the height.
    */
  def height: Int

  /** Provides an interface for Account Transaction IDs retrieval.
    * @param a Inputs a [[scorex.account.Address]].
    * @param limit Sets a limit for transaction retrieval.
    * @return Returns a Seguence of IDs for Transactions.
    */
  def accountTransactionIds(a: Address, limit: Int): Seq[ByteStr]

  /** Provides an interface for retriving Aliases of Addresses.
    * @param a Inputs a [[scorex.account.Address]]
    * @return Returns a Sequence of [[scorex.account.Alias]].
    */
  def aliasesOfAddress(a: Address): Seq[Alias]

  /** Provides an interface for retrieving an Address with a given Alias.
    * @param a Inputs a [[scorex.account.Alias]].
    * @return Returns an Option for [[scorex.account.Address]].
    */
  def resolveAlias(a: Alias): Option[Address]

  /**Provides an interface for check if a given Lease is Active.
    * @param leaseTx Inpust a [[io.lunes.transaction.lease.LeaseTransaction]].
    * @return Returns true if the Lease is active.
    */
  def isLeaseActive(leaseTx: LeaseTransaction): Boolean

  /** Provides an interface for retriving all Active Leases.
    * @return Returns a Sequence of IDs for Active Leases.
    */
  def activeLeases(): Seq[ByteStr]

  /** Provides an interface for retrieving the Last Update's Height.
    * @param acc Inputs an [[scorex.account.Address]].
    * @return Returns an Option for the Height.
    */
  def lastUpdateHeight(acc: Address): Option[Int]

  /** Provides an interface for retrieving the Snapshot at a certain Height.
    * @param acc Inputs the [[scorex.account.Address]].
    * @param h Inputs the Height.
    * @return Returns an Option for [[io.lunes.state2.Snapshot]].
    */
  def snapshotAtHeight(acc: Address, h: Int): Option[Snapshot]

  /** Provides an interface for retrieving the Volume and Fee Filled in a OrderFillInfo
    * @param orderId Inpúts the order ID.
    * @return Return an [[io.lunes.state2.OrderFillInfo]].
    */
  def filledVolumeAndFee(orderId: ByteStr): OrderFillInfo
}

/** SnapshotStateReader Companion Object. */
object SnapshotStateReader {

  /** StateReader Extension Class
    * @constructor Creates the Extension provideded a SnapshotStateReader Object.
    * @param s Inputs the [[SnapshotStateReader]].
    */
  implicit class StateReaderExt(s: SnapshotStateReader) extends ScorexLogging {
    def assetDistribution(assetId: ByteStr): Map[Address, Long] =
      s.accountPortfolios
        .mapValues(portfolio => portfolio.assets.get(assetId))
        .collect { case (acc, Some(amt)) => acc -> amt }

    /** Find Transaction given signature.
      * @param signature The [[ByteStr]] for the signature.
      * @tparam T The Parameter Type which must be a Class Descendant of [[io.lunes.transaction.Transaction]].
      * @return Returs an Option
      */
    def findTransaction[T <: Transaction : ClassTag](signature: ByteStr): Option[T] =
      s.transactionInfo(signature) match {
        case Some((_, Some(t: T))) => Some(t)
        case _ => None
      }

    /** Resolve Alias returning a Either.
      * @param aoa The Addres or Alias object.
      * @tparam T The Parametrized Type which must be a descendant of [[io.lunes.transaction.Transaction]].
      * @return Returns Either a Address (case Success) of a ValidationError (case Failure).
      */
    def resolveAliasEi[T <: Transaction](aoa: AddressOrAlias): Either[ValidationError, Address] = {
      aoa match {
        case a: Address => Right(a)
        case a: Alias => s.resolveAlias(a) match {
          case None => Left(AliasNotExists(a))
          case Some(acc) => Right(acc)
        }
      }
    }

    /** Check How many elements are included given a signature.
      * @param signature The [[ByteStr]] signature.
      * @return Returns an Option for the Number of elements.
      */
    def included(signature: ByteStr): Option[Int] = s.transactionInfo(signature).map(_._1)

    /** Gets the Sequence of Transaction for an account with a limit.
      * @param account The [[scorex.account.Address]] of the node.
      * @param limit The limit of search.
      * @return Returns a Sequence of Tuples (Int, SomeClass). Such SomeClass must be a descendant of Transaction.
      */
    def accountTransactions(account: Address, limit: Int): Seq[(Int, _ <: Transaction)] = s.read { _ =>
      s.accountTransactionIds(account, limit)
        .flatMap(s.transactionInfo)
        .flatMap { case (h, txopt) => txopt.map((h, _)) }
    }

    /** Returns the Account Balance.
      * @param account The [[scorex.account.Address] of the node.
      * @return The Balance.
      */
    def balance(account: Address): Long = s.lunesBalance(account)._1

    /** Gets a Map for AssetId into a Tuple (Long, Boolean, Long, IssueTransaction).
      * @param account The [[scorex.account.Address]] of the node.
      * @return Returns the map.
      */
    def getAccountBalance(account: Address): Map[AssetId, (Long, Boolean, Long, IssueTransaction)] = s.read { _ =>
      s.accountPortfolio(account).assets.collect { case (id, amt) if amt > 0 =>
        val assetInfo = s.assetInfo(id).get
        val issueTransaction = findTransaction[IssueTransaction](id).get
        id -> ((amt, assetInfo.isReissuable, assetInfo.volume, issueTransaction))
      }
    }

    /** Gets a Map for Asset Distribution given an Asset ID.
      * @param assetId The Asset ID.
      * @return The Distribution Map.
      */
    def assetDistribution(assetId: Array[Byte]): Map[String, Long] =
      s.assetDistribution(ByteStr(assetId))
        .map { case (acc, amt) => (acc.address, amt) }

    /** Gets the Effective Balance for a Account.
      * @param account The [[scorex.account.Address]].
      * @return The Effective Balance.
      */
    def effectiveBalance(account: Address): Long = s.partialPortfolio(account).effectiveBalance

    /** Gets the Balance Available to Spend given a [[io.lunes.transaction.AssetAcc]].
      * @param assetAcc The Asset Account input.
      * @return The Spendable Balance Available.
      */
    def spendableBalance(assetAcc: AssetAcc): Long = {
      val accountPortfolio = s.partialPortfolio(assetAcc.account, assetAcc.assetId.toSet)
      assetAcc.assetId match {
        case Some(assetId) => accountPortfolio.assets.getOrElse(assetId, 0)
        case None => accountPortfolio.spendableBalance
      }
    }

    /** Check if Asset is Reissuable given its ID.
      * @param id The Asset ID.
      * @return Returns true if the Asset is Reissuable.
      */
    def isReissuable(id: Array[Byte]): Boolean = s.assetInfo(ByteStr(id)).get.isReissuable

    /** Gets the total Asset Quantity given an [[io.lunes.transaction.AssetId]].
      * @param assetId The Asset Id.
      * @return The Amount of Assets.
      */
    def totalAssetQuantity(assetId: AssetId): Long = s.assetInfo(assetId).get.volume

    /** Check if Asset Exists.
      * @param assetId The Asset ID.
      * @return Returns true if the Asset exists.
      */
    def assetExists(assetId: AssetId): Boolean = s.findTransaction[IssueTransaction](assetId).nonEmpty

    /** Gets the Name of the Asst given its ID.
      * @param assetId The Asset ID.
      * @return The Asset's Name.
      */
    def getAssetName(assetId: AssetId): String = {
      s.findTransaction[IssueTransaction](assetId)
        .map(tx => new String(tx.name, Charsets.UTF_8))
        .getOrElse("Unknown")
    }

    /** Gets an Issued Transaction.
      * @param assetId The Asset Id.
      * @return Returns the Issue Transaction.
      */
    def getIssueTransaction(assetId: AssetId): Option[IssueTransaction] = {
      s.findTransaction[IssueTransaction](assetId)
    }

    /** Get the minimun of a Snapshot.
      * @param acc The Account [[scorex.account.Address]].
      * @param atHeight The Starting Height.
      * @param confirmations Number of Confirmations.
      * @param extractor An Extrator Map for Snapshot into Long.
      * @return The Minimum.
      */
    private def minBySnapshot(acc: Address, atHeight: Int, confirmations: Int)(extractor: Snapshot => Long): Long = s.read { _ =>
      val bottomNotIncluded = atHeight - confirmations

      // @tailrec
      def loop(deeperHeight: Int, list: Seq[Snapshot]): Seq[Snapshot] = {
        if (deeperHeight == 0) {
          s.snapshotAtHeight(acc, 1) match {
            case Some(genesisSnapshot) =>
              genesisSnapshot +: list
            case None =>
              Snapshot(0, 0, 0) +: list
          }
        } else {
          s.snapshotAtHeight(acc, deeperHeight) match {
            case Some(snapshot) =>
              if (deeperHeight <= bottomNotIncluded)
                snapshot +: list
              else if (snapshot.prevHeight == deeperHeight) {
                throw new Exception(s"CRITICAL: Infinite loop detected while calculating minBySnapshot: acc=$acc, atHeight=$atHeight, " +
                  s"confirmations=$confirmations; lastUpdateHeight=${s.lastUpdateHeight(acc)}; current step: deeperHeight=$deeperHeight, list.size=${list.size}")
              } else if (deeperHeight > atHeight && snapshot.prevHeight > atHeight) {
                loop(snapshot.prevHeight, list)
              } else
                loop(snapshot.prevHeight, snapshot +: list)
            case None =>
              throw new Exception(s"CRITICAL: Cannot lookup referenced height: acc=$acc, atHeight=$atHeight, " +
                s"confirmations=$confirmations; lastUpdateHeight=${s.lastUpdateHeight(acc)}; current step: deeperHeight=$deeperHeight, list.size=${list.size}")
          }
        }
      }

      val snapshots: Seq[Snapshot] = s.lastUpdateHeight(acc) match {
        case None => Seq(Snapshot(0, 0, 0))
        case Some(h) if h < bottomNotIncluded =>
          val pf = s.partialPortfolio(acc)
          Seq(Snapshot(h, pf.balance, pf.effectiveBalance))
        case Some(h) => loop(h, Seq.empty)
      }

      snapshots.map(extractor).min
    }

    /** Gets the effective Balance at a given Height and some Confirmations.
      * @param acc The Account [[scorex.account.Address]].
      * @param atHeight The Input Height.
      * @param confirmations The Confirmations.
      * @return Returns a Try object for the effective balance.
      */
    def effectiveBalanceAtHeightWithConfirmations(acc: Address, atHeight: Int, confirmations: Int): Try[Long] = Try { // Perhaps Option should be better.
      minBySnapshot(acc, atHeight, confirmations)(_.effectiveBalance)
    }

    /** Gets the balance given confirmations.
      * @param acc The Account [[scorex.account.Address]].
      * @param confirmations The Confirmations.
      * @return Returns the Balance.
      */
    def balanceWithConfirmations(acc: Address, confirmations: Int): Long =
      minBySnapshot(acc, s.height, confirmations)(_.balance)

    /** Returns the Balance at a given height.
	    * @param acc The Account [[scorex.account.Address]].
      * @param height The Input Height.
      * @return Returns the Balance.
      */
    def balanceAtHeight(acc: Address, height: Int): Long = s.read { _ =>
      // @tailrec
      def loop(lookupHeight: Int): Long = s.snapshotAtHeight(acc, lookupHeight) match {
        case None if lookupHeight == 0 => 0
        case Some(snapshot) if lookupHeight <= height => snapshot.balance
        case Some(snapshot) if snapshot.prevHeight == lookupHeight =>
          throw new Exception(s"CRITICAL: Cannot lookup account $acc for height $height(current=${s.height}). Infinite loop detected. " +
            s"This indicates snapshots processing error.")
        case Some(snapshot) => loop(snapshot.prevHeight)
        case None =>
          throw new Exception(s"CRITICAL: Cannot lookup account $acc for height $height(current=${s.height}). " +
            s"No history found at requested lookupHeight=$lookupHeight")
      }


      loop(s.lastUpdateHeight(acc).getOrElse(0))
    }

    /** Returns a [[Portfolio]] partially filled.
	    * @param a The Account [[scorex.account.Address]].
      * @param assets Inputs a Set of [[AssetId]].
      * @return Returns the partial portifolio.
      */
    def partialPortfolio(a: Address, assets: Set[AssetId] = Set.empty): Portfolio = {
      val (w, l) = s.lunesBalance(a)
      Portfolio(w, l, assets.map(id => id -> s.assetBalance(a, id)).toMap)
    }
  }

}
