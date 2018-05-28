package io.lunes.state2

import cats.Monoid
import cats.implicits._
import com.google.common.primitives.{Bytes, Ints, Longs}
import io.lunes.db._
import io.lunes.utils._
import org.iq80.leveldb.{DB, WriteBatch}
import scorex.account.{Address, Alias}
import scorex.utils.{NTP, Time}

import scala.util.Try

/** State Storage class which extends SubStorage.
  * @param db LevelDB db input object.
  * @param time [[scorex.utils.Time]] Timestamp input.
  */
class StateStorage private(db: DB, time: Time) extends SubStorage(db, "state") with PropertiesStorage with VersionedStorage {

  import StateStorage._

  override protected val Version = 2

  private val HeightPrefix = "state-height".getBytes(Charset)
  private val TransactionsPrefix = "txs".getBytes(Charset)
  private val AccountTransactionsLengthsPrefix = "acc-txs-len".getBytes(Charset)
  private val LunesBalancePrefix = "lunes-bal".getBytes(Charset)
  private val AddressesIndexPrefix = "add-idx".getBytes(Charset)
  private val AssetBalancePrefix = "asset-bal".getBytes(Charset)
  private val AddressAssetsPrefix = "address-assets".getBytes(Charset)
  private val AssetsPrefix = "assets".getBytes(Charset)
  private val OrderFillsPrefix = "ord-fls".getBytes(Charset)
  private val AccountTransactionIdsPrefix = "acc-ids".getBytes(Charset)
  private val BalanceSnapshotsPrefix = "bal-snap".getBytes(Charset)
  private val LastBalanceHeightPrefix = "bal-h".getBytes(Charset)
  private val AliasToAddressPrefix = "alias-address".getBytes(Charset)
  private val AddressToAliasPrefix = "address-alias".getBytes(Charset)
  private val LeaseStatePrefix = "lease-state".getBytes(Charset)
  private val LeaseIndexPrefix = "lease-idx".getBytes(Charset)
  private val MaxAddress = "max-address"
  private val LeasesCount = "leases-count"

  /** Get the Key Height.
    * @return Returns Height.
    */
  def getHeight: Int = get(makeKey(HeightPrefix, 0)).map(Ints.fromByteArray).getOrElse(0)

  /** Sets Key Height.
    * @param b Option for LevelDB WriteBatch.
    * @param height Input Height.
    */
  def setHeight(b: Option[WriteBatch], height: Int): Unit = {
    put(makeKey(HeightPrefix, 0), Ints.toByteArray(height), b)
  }

  /** Gets a Transaction given its ID.
    * @param id Input ID.
    * @return Returns an Option for a Tuple (Int, Array[Byte]).
    */
  def getTransaction(id: ByteStr): Option[(Int, Array[Byte])] =
    get(makeKey(TransactionsPrefix, id.arr)).map(TransactionsValueCodec.decode).map(_.explicitGet().value)

  /** Puts Transactions.
    * @param b Option for LevelDB WriteBatch.
    * @param diff Inputs [[io.lunes.state2.Diff]].
    */
  def putTransactions(b: Option[WriteBatch], diff: Diff): Unit = {
    diff.transactions.foreach { case (id, (h, tx, _)) =>
      put(makeKey(TransactionsPrefix, id.arr), TransactionsValueCodec.encode((h, tx.bytes())), b)
    }

    putOrderFills(b, diff.orderFills)
    putPortfolios(b, diff.portfolios)
    putIssuedAssets(b, diff.issuedAssets)
    putAccountTransactionsIds(b, diff.accountTransactionIds)
    putAliases(b, diff.aliases)
    putLeases(b, diff.leaseState)
  }

  /** Gets a Lunes Balance given an [[scorex.account.Address]].
    * @param address Inputs [[scorex.account.Address]].
    * @return Returns an Option for a Tuple (Long, Long, Long).
    */
  def getLunesBalance(address: Address): Option[(Long, Long, Long)] =
    get(makeKey(LunesBalancePrefix, address.bytes.arr)).map(LunesBalanceValueCodec.decode).map(_.explicitGet().value)

  /** Gets a Asset Balance given an [[scorex.account.Address]].
    * @param address Inputs [[scorex.account.Address]].
    * @param asset The requested Asset.
    * @return Returns an Option of Long informing the current Balance for the Asset.
    */
  def getAssetBalance(address: Address, asset: ByteStr): Option[Long] =
    get(makeKey(AssetBalancePrefix, Bytes.concat(address.bytes.arr, asset.arr))).map(Longs.fromByteArray)

  /** Gets an Asset Information.
    * @param asset Input Asset.
    * @return Returns an Option for [[io.lunes.state2.AssetInfo]].
    */
  def getAssetInfo(asset: ByteStr): Option[AssetInfo] =
    get(makeKey(AssetsPrefix, asset.arr)).map(AssetInfoCodec.decode).map(_.explicitGet().value)

  /** Get the Account Transaction IDs given an [[scorex.account.Address]].
    * @param address Inputs [[scorex.account.Address]].
    * @param index The Transcation index.
    * @return Returns an Option for ByteStr for the Transaction ID.
    */
  def getAccountTransactionIds(address: Address, index: Int): Option[ByteStr] =
    get(makeKey(AccountTransactionIdsPrefix, accountIntKey(address, index))).map(b => ByteStr(b))

  /** Get the Account Transaction Length given an [[scorex.account.Address]]
    * @param address Inputs [[scorex.account.Address]].
    * @return Returns an Option for Int representing the Length of Transactions.
    */
  def getAccountTransactionsLengths(address: Address): Option[Int] =
    get(makeKey(AccountTransactionsLengthsPrefix, address.bytes.arr)).map(Ints.fromByteArray)

  /** Get Balance Snapshots given an [[scorex.account.Address]].
    * @param address Inputs [[scorex.account.Address]].
    * @param height Inputs the Height.
    * @return Returns an Option for Tuple (Int, Long, Long) as the Snapshot.
    */
  def getBalanceSnapshots(address: Address, height: Int): Option[(Int, Long, Long)] =
    get(makeKey(BalanceSnapshotsPrefix, accountIntKey(address, height))).map(BalanceSnapshotValueCodec.decode).map(_.explicitGet().value)

  /** Inserts a Balance Snapshot given an [[scorex.account.Address]].
    * @param b Inputs an Option for a LevelDB WriteBatch.
    * @param address Inputs [[scorex.account.Address]].
    * @param height Inputs Height.
    * @param value Snapshot Value.
    */
  def putBalanceSnapshots(b: Option[WriteBatch], address: Address, height: Int, value: (Int, Long, Long)): Unit =
    put(makeKey(BalanceSnapshotsPrefix, accountIntKey(address, height)), BalanceSnapshotValueCodec.encode(value), b)

  /** Inserts Lunes Balance given an [[scorex.account.Address]].
    * @param b Inputs an Option for a LevelDB WriteBatch.
    * @param address Inputs [[scorex.account.Address]].
    * @param value The Lunes Balance Tuple.
    */
  def putLunesBalance(b: Option[WriteBatch], address: Address, value: (Long, Long, Long)): Unit = {
    updateAddressesIndex(Seq(address.bytes.arr), b)
    put(makeKey(LunesBalancePrefix, address.bytes.arr), LunesBalanceValueCodec.encode(value), b)
  }

  /** Gets an [[scorex.account.Address]] given a Alias.
    * @param alias The given Alias.
    * @return Returns an Option for an [[scorex.account.Address]].
    */
  def getAddressOfAlias(alias: Alias): Option[Address] =
    get(makeKey(AliasToAddressPrefix, alias.bytes.arr)).map(b => Address.fromBytes(b).explicitGet())

  /** Gets a Sequence of Aliases given an [[scorex.account.Address]].
    * @param address Inputs [[scorex.account.Address]].
    * @return Returns an Option of Sequence Alias.
    */
  def getAddressAliases(address: Address): Option[Seq[Alias]] =
    get(makeKey(AddressToAliasPrefix, address.bytes.arr)).map(AliasSeqCodec.decode).map(_.explicitGet().value)

  /** Gets an Order Fills.
    * @param id Order ID.
    * @return Returns an Option for OrderFillInfo.
    */
  def getOrderFills(id: ByteStr): Option[OrderFillInfo] =
    get(makeKey(OrderFillsPrefix, id.arr)).map(OrderFillInfoValueCodec.decode).map(_.explicitGet().value)

  /** Gets a Lease State given an ID.
    * @param id Order ID.
    * @return Returns an Option for Boolean.
    */
  def getLeaseState(id: ByteStr): Option[Boolean] = get(makeKey(LeaseStatePrefix, id.arr)).map(b => b.head == 1.toByte)

  /** Gets Active Leases.
    * @return Returns an Option for Sequence for ByteStr.
    */
  def getActiveLeases: Option[Seq[ByteStr]] = {
    val count = getIntProperty(LeasesCount).getOrElse(0)
    val result = (0 until count).foldLeft(Seq.empty[ByteStr]) { (r, i) =>
      val maybeLease = get(makeKey(LeaseIndexPrefix, i))
      val maybeLeaseState = maybeLease.flatMap(l => get(makeKey(LeaseStatePrefix, l)).map(b => b.head == 1.toByte))
      if (maybeLeaseState.contains(true)) r :+ ByteStr(maybeLease.get) else r
    }
    if (result.isEmpty) None else Some(result)
  }

  /** Gets the Last Balance Snapshot Height given an [[scorex.account.Address]].
    * @param address Inputs [[scorex.account.Address]].
    * @return Returns an Option for Int representing the height.
    */
  def getLastBalanceSnapshotHeight(address: Address): Option[Int] =
    get(makeKey(LastBalanceHeightPrefix, address.bytes.arr)).map(Ints.fromByteArray)

  /** Puts Last Balance Snapshot Height given an [[scorex.account.Address]].
    * @param b Input an Option for a LevelDB WriteBatch.
    * @param address Inputs [[scorex.account.Address]].
    * @param height Inputs the height.
    */
  def putLastBalanceSnapshotHeight(b: Option[WriteBatch], address: Address, height: Int): Unit =
    put(makeKey(LastBalanceHeightPrefix, address.bytes.arr), Ints.toByteArray(height), b)

  /** Maps Addresses into Portfolios.
    * @return Returns the Map
    */
  def allPortfolios: Map[Address, Portfolio] = {
    val maxAddressIndex = getIntProperty(MaxAddress).getOrElse(0)
    log.debug(s"Accounts count: $maxAddressIndex")
    (0 until maxAddressIndex).flatMap({ i =>
      val maybeAddressBytes = get(makeKey(AddressesIndexPrefix, i))
      val maybeAddress = maybeAddressBytes.flatMap { addressBytes => Address.fromBytes(addressBytes).toOption }
      val maybeBalances = maybeAddress.flatMap { address => getLunesBalance(address) }
      val maybeAssetsBalances = maybeAddress.flatMap { address => getAssetBalanceMap(address) }

      (maybeAddress, maybeBalances, maybeAssetsBalances) match {
        case (Some(a), Some(b), Some(c)) =>
          Some(a -> Portfolio(b._1, LeaseInfo(b._2, b._3), c))
        case _ => None
      }
    })(scala.collection.breakOut)
  }

  /** Get a Map for ID into Balance given an [[scorex.account.Address]].
    * @param address Inputs [[scorex.account.Address]].
    * @return Returns an Option for Map an ID into a Balance.
    */
  def getAssetBalanceMap(address: Address): Option[Map[ByteStr, Long]] = {
    val assets = get(makeKey(AddressAssetsPrefix, address.bytes.arr)).map(Id32SeqCodec.decode).map(_.explicitGet().value)
    if (assets.isDefined) {
      val result = assets.get.foldLeft(Map.empty[ByteStr, Long]) { (m, a) =>
        val key = makeKey(AssetBalancePrefix, Bytes.concat(address.bytes.arr, a.arr))
        val balance = get(key).map(Longs.fromByteArray).getOrElse(0L)
        m.updated(a, balance)
      }
      Some(result)
    } else None
  }

  /** Remove all data.
    * @param b Input an Option for a LevelDB WriteBatch.
    */
  override def removeEverything(b: Option[WriteBatch]): Unit = {
    putIntProperty(MaxAddress, 0, b)
    putIntProperty(LeasesCount, 0, b)
    super.removeEverything(b)
  }

  /** Input Order Fills.
    * @param b Input an Option for a LevelDB WriteBatch.
    * @param fills Input a Map for ID into OrderFillInfo.
    */
  private def putOrderFills(b: Option[WriteBatch], fills: Map[ByteStr, OrderFillInfo]): Unit =
    fills.foreach { case (id, info) =>
      val key = makeKey(OrderFillsPrefix, id.arr)
      val existing = get(key).map(OrderFillInfoValueCodec.decode).map(_.explicitGet().value).getOrElse(Monoid[OrderFillInfo].empty)
      val updated = existing.combine(info)
      put(key, OrderFillInfoValueCodec.encode(updated), b)
    }

  /** Update Addresses Indexes.
    * @param addresses An Iteratable of Array Byte.
    * @param b Input an Option for a LevelDB WriteBatch.
    */
  private def updateAddressesIndex(addresses: Iterable[Array[Byte]], b: Option[WriteBatch]): Unit = {
    val n = getIntProperty(MaxAddress).getOrElse(0)
    val newAddresses = addresses.foldLeft(n) { (c, a) =>
      val key = makeKey(LunesBalancePrefix, a)
      if (get(key).isEmpty) {
        put(makeKey(AddressesIndexPrefix, c), a, b)
        c + 1
      } else c
    }
    putIntProperty(MaxAddress, newAddresses, b)
  }

  /** Insert Portfolios.
    * @param b Input an Option for a LevelDB WriteBatch.
    * @param portfolios Input a Map for Address into Portfolios.
    */
  private def putPortfolios(b: Option[WriteBatch], portfolios: Map[Address, Portfolio]): Unit = {
    updateAddressesIndex(portfolios.keys.map(_.bytes.arr), b)
    portfolios.foreach { case (a, d) =>
      val addressBytes = a.bytes.arr

      val lunesKey = makeKey(LunesBalancePrefix, addressBytes)
      val existingLunesBalance = get(lunesKey).map(LunesBalanceValueCodec.decode).map(_.explicitGet().value).getOrElse((0L, 0L, 0L))
      val lunesBalanceValue = (
        existingLunesBalance._1 + d.balance,
        existingLunesBalance._2 + d.leaseInfo.leaseIn,
        existingLunesBalance._3 + d.leaseInfo.leaseOut
      )
      put(lunesKey, LunesBalanceValueCodec.encode(lunesBalanceValue), b)

      val addressKey = makeKey(AddressAssetsPrefix, addressBytes)
      val existingAssets = get(addressKey).map(Id32SeqCodec.decode).map(_.explicitGet().value).getOrElse(Seq.empty[ByteStr])
      val updatedAssets = existingAssets ++ d.assets.keySet
      put(addressKey, Id32SeqCodec.encode(updatedAssets.distinct), b)

      d.assets.foreach { case (as, df) =>
        val addressAssetKey = makeKey(AssetBalancePrefix, Bytes.concat(addressBytes, as.arr))
        val existingValue = get(addressAssetKey).map(Longs.fromByteArray).getOrElse(0L)
        val updatedValue = existingValue + df
        put(addressAssetKey, Longs.toByteArray(updatedValue), b)
      }
    }
  }

  /** Inserts Issued Assets.
    * @param b Input an Option for a LevelDB WriteBatch.
    * @param issues Inputs a Map for ID into AssetInfo.
    */
  private def putIssuedAssets(b: Option[WriteBatch], issues: Map[ByteStr, AssetInfo]): Unit =
    issues.foreach { case (id, info) =>
      val key = makeKey(AssetsPrefix, id.arr)
      val existing = get(key).map(AssetInfoCodec.decode).map(_.explicitGet().value).getOrElse(Monoid[AssetInfo].empty)
      put(key, AssetInfoCodec.encode(existing.combine(info)), b)
    }

  /** Inserts Account Transactions Ids.
    * @param b Input an Option for a LevelDB WriteBatch.
    * @param accountIds Inputs a Map for Netty Address into a List of IDs.
    */
  private def putAccountTransactionsIds(b: Option[WriteBatch], accountIds: Map[Address, List[ByteStr]]): Unit =
    accountIds.foreach { case (a, ids) =>
      val key = makeKey(AccountTransactionsLengthsPrefix, a.bytes.arr)
      val start = get(key).map(Ints.fromByteArray).getOrElse(0)
      val end = ids.reverse.foldLeft(start) { case (i, id) =>
        put(makeKey(AccountTransactionIdsPrefix, accountIntKey(a, i)), id.arr, b)
        i + 1
      }
      put(key, Ints.toByteArray(end), b)
    }

  /** Insert Aliases.
    * @param b Input an Option for a LevelDB WriteBatch.
    * @param aliases Inputs a Map for [[scorex.account.Alias]] into an [[scorex.account.Address]].
    */
  private def putAliases(b: Option[WriteBatch], aliases: Map[Alias, Address]): Unit = {
    val map = aliases.foldLeft(Map.empty[Address, Seq[Alias]]) { (m, e) =>
      put(makeKey(AliasToAddressPrefix, e._1.bytes.arr), e._2.bytes.arr, b)
      val existingAliases = m.getOrElse(e._2, Seq.empty[Alias])
      val aliases = existingAliases :+ e._1
      m.updated(e._2, aliases.distinct)
    }
    map.foreach { e =>
      val key = makeKey(AddressToAliasPrefix, e._1.bytes.arr)
      val existing = get(key).map(AliasSeqCodec.decode).map(_.explicitGet().value).getOrElse(Seq.empty[Alias])
      val updated = existing ++ e._2
      put(key, AliasSeqCodec.encode(updated.distinct), b)
    }
  }

  /** Inserts Leases.
    * @param b Input an Option for a LevelDB WriteBatch.
    * @param leases Inputs a Map for ID into Boolean.
    */
  private def putLeases(b: Option[WriteBatch], leases: Map[ByteStr, Boolean]): Unit = {
    val n = getIntProperty(LeasesCount).getOrElse(0)
    val count = leases.foldLeft(n) { (c, e) =>
      val key = makeKey(LeaseStatePrefix, e._1.arr)
      if (get(key).isDefined) {
        put(key, if (e._2) Codec.TrueBytes else Codec.FalseBytes, b)
        c
      } else {
        put(key, if (e._2) Codec.TrueBytes else Codec.FalseBytes, b)
        put(makeKey(LeaseIndexPrefix, c), e._1.arr, b)
        c + 1
      }
    }
    putIntProperty(LeasesCount, count, b)
  }
}

/** State Storage Object*/
object StateStorage {
  /** The Application Factory Function.
    * @param db LevelDB db object.
    * @param dropExisting If true drop database.
    * @param time Timestamp
    * @return Returns the StateStorage created.
    */
  def apply(db: DB, dropExisting: Boolean, time: Time = NTP): Try[StateStorage] = createWithVerification[StateStorage](new StateStorage(db, time))

  /** Gets the Integer representing the Accounting Key given [[scorex.account.Address]].
    * @param acc Inputs [[scorex.account.Address]].
    * @param index Inputs the Account index.
    * @return Returns the Array Byte Key.
    */
  def accountIntKey(acc: Address, index: Int): Array[Byte] = Bytes.concat(acc.bytes.arr, Ints.toByteArray(index))
}
