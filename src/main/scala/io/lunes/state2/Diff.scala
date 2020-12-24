package io.lunes.state2

import cats.Monoid
import cats.implicits._
import scorex.account.{Address, Alias}
import io.lunes.transaction.Transaction

/** Class for holding Snapshot data.
  * @constructor Creates a New Snapshot Object.
  * @param prevHeight Previous Height.
  * @param balance Account Balance.
  * @param effectiveBalance Effective Account Balance.
  */
case class Snapshot(prevHeight: Int, balance: Long, effectiveBalance: Long)

/** Class for holding Lease Information data.
  * @constructor Creates a new Lease Information Object.
  * @param leaseIn Lease Input.
  * @param leaseOut Lease Output.
  */
case class LeaseInfo(leaseIn: Long, leaseOut: Long)

/** LeaseInfo Companion Object.*/
object LeaseInfo {
  val empty = LeaseInfo(0, 0)
  implicit val leaseInfoMonoid = new Monoid[LeaseInfo] {
    override def empty: LeaseInfo = LeaseInfo.empty

    override def combine(x: LeaseInfo, y: LeaseInfo): LeaseInfo = LeaseInfo(safeSum(x.leaseIn, y.leaseIn), safeSum(x.leaseOut, y.leaseOut))
  }
}

/** Class for holding Order Filling Information data.
  * @param volume Order Volume.
  * @param fee Order Fee.
  */
case class OrderFillInfo(volume: Long, fee: Long)

/** OrderFillInfo companion Object*/
object OrderFillInfo {
  implicit val orderFillInfoMonoid = new Monoid[OrderFillInfo] {
    override def empty: OrderFillInfo = OrderFillInfo(0, 0)

    override def combine(x: OrderFillInfo, y: OrderFillInfo): OrderFillInfo = OrderFillInfo(x.volume + y.volume, x.fee + y.fee)
  }
}

/** Class for Holding Asset Information data.
  * @param isReissuable True if Asset is Reissuable.
  * @param volume Asset Volume.
  */
case class AssetInfo(isReissuable: Boolean, volume: Long)

/** AssetInfo Companion Object*/
object AssetInfo {
  implicit val assetInfoMonoid = new Monoid[AssetInfo] {
    override def empty: AssetInfo = AssetInfo(isReissuable = true, 0)

    override def combine(x: AssetInfo, y: AssetInfo): AssetInfo
    = AssetInfo(x.isReissuable && y.isReissuable, x.volume + y.volume)
  }
}

/** Class for holding Differences in Transaction data.
  * @constructor Creates a Standard Constructor.
  * @param transactions Maps ID into Tuple (Int, [[io.lunes.transaction.Transaction]], Set[Address]).
  * @param portfolios Maps [[scorex.account.Address]] into [[io.lunes.state2.Portfolio]].
  * @param issuedAssets Maps ID into [[io.lunes.state2.AssetInfo]]
  * @param aliases Maps [[scorex.account.Alias]] into [[scorex.account.Address]].
  * @param orderFills Maps ID into [[io.lunes.state2.OrderFillInfo]].
  * @param leaseState Maps ID into Boolean for LeaseState Check.
  */
case class Diff(transactions: Map[ByteStr, (Int, Transaction, Set[Address])],
                portfolios: Map[Address, Portfolio],
                issuedAssets: Map[ByteStr, AssetInfo],
                aliases: Map[Alias, Address],
                orderFills: Map[ByteStr, OrderFillInfo],
                leaseState: Map[ByteStr, Boolean]) {

  lazy val accountTransactionIds: Map[Address, List[ByteStr]] = {
    val map: List[(Address, Set[(Int, Long, ByteStr)])] = transactions.toList
      .flatMap { case (id, (h, tx, accs)) => accs.map(acc => acc -> Set((h, tx.timestamp, id))) }
    val groupedByAcc = map.foldLeft(Map.empty[Address, Set[(Int, Long, ByteStr)]]) { case (m, (acc, set)) =>
      m.combine(Map(acc -> set))
    }
    groupedByAcc
      .mapValues(l => l.toList.sortBy { case ((h, t, _)) => (-h, -t) }) // fresh head ([h=2, h=1, h=0])
      .mapValues(_.map(_._3))
  }
}

/** case class Diff Companion Object */
object Diff {
  //TODO: tailrec
  /** Diff Class Alternative Constructor.
    * @param height Record Height.
    * @param tx Inputs Transaction.
    * @param portfolios Maps [[scorex.account.Address]] into [[io.lunes.state2.Portfolio]].
    * @param assetInfos Maps IDs into [[io.lunes.state2.AssetInfo]].
    * @param aliases Maps [[scorex.account.Alias]] into [[scorex.account.Address]].
    * @param orderFills Maps ID into [[io.lunes.state2.OrderFillInfo]].
    * @param leaseState Maps ID into Boolean for LeaseState Check.
    * @return Returns the Created Object.
    */
  //TODO: tailrec
  def apply(height: Int, tx: Transaction,
            portfolios: Map[Address, Portfolio] = Map.empty,
            assetInfos: Map[ByteStr, AssetInfo] = Map.empty,
            aliases: Map[Alias, Address] = Map.empty,
            orderFills: Map[ByteStr, OrderFillInfo] = Map.empty,
            leaseState: Map[ByteStr, Boolean] = Map.empty
           ): Diff = Diff(
    transactions = Map((tx.id(), (height, tx, portfolios.keys.toSet))),
    portfolios = portfolios,
    issuedAssets = assetInfos,
    aliases = aliases,
    orderFills = orderFills,
    leaseState = leaseState)

  val empty = new Diff(Map.empty, Map.empty, Map.empty, Map.empty, Map.empty, Map.empty)

  /** Difference Extension Class
    * @constructor Creates a Difference Extension Object.
    * @param d The Original [[Diff]] object.
    */
  implicit class DiffExt(d: Diff) {
    /** Returns the Difference Extension as a [[io.lunes.state2.BlockDiff]].
      * @return Returns the Created Object.
      */
    def asBlockDiff: BlockDiff = BlockDiff(d, 0, Map.empty)
  }

  implicit val diffMonoid = new Monoid[Diff] {
    override def empty: Diff = Diff.empty

    /** Combines two Diff Objects.
      * @param older Older [[Diff]].
      * @param newer Newer [[Diff]].
      * @return Returns combined Diff Object.
      */
    override def combine(older: Diff, newer: Diff): Diff = Diff(
      transactions = older.transactions ++ newer.transactions,
      portfolios = older.portfolios.combine(newer.portfolios),
      issuedAssets = older.issuedAssets.combine(newer.issuedAssets),
      aliases = older.aliases ++ newer.aliases,
      orderFills = older.orderFills.combine(newer.orderFills),
      leaseState = older.leaseState ++ newer.leaseState,
    )
  }
}
