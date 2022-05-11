package io.lunes.core.storage.db

import com.google.common.base.Charsets
import com.google.common.primitives.{Ints, Longs, Shorts}
import io.lunes.network.{BlockCheckpoint, Checkpoint}
import io.lunes.state2.{AssetInfo, ByteStr, OrderFillInfo}
import scorex.account.Alias

import scala.util.Try

/** Codec Failure Class.
  * @constructor Creates a Codec Failure instance.
  * @param reason Gives a reason for the failure.
  */
case class CodecFailure(reason: String) {
  override def toString: String = s"codec failure: $reason"
}

/** Result Decoder
  * @constructor Creates a parametrized Result Decoder by a value of type A.
  * @param length Result length.
  * @param value Code value.
  * @tparam A Parametrized Type.
  */
case class DecodeResult[A](length: Int, value: A)

/** Code trait
  * @tparam A Parameter type.
  */
trait Codec[A] {

  import Codec._

  /** Runs Encoding.
    * @param value Value for encoding.
    * @return Returns a encoded Array of Byte.
    */
  def encode(value: A): Array[Byte]

  /** Runs Decoding
    * @param bytes Inputs data source.
    * @return Returns Either a DecodeResult of Type [A] (case Success) or a CodedFailure (case Failure).
    */
  def decode(bytes: Array[Byte]): Either[CodecFailure, DecodeResult[A]]

  /** Runs Decoding with a Short result instead of DecodeResult.
    * @param bytes Inputs data source.
    * @return Returns Either a Short (case Success) or a CodedFailure (case Failure).
    */
  protected def decodeShort(bytes: Array[Byte]): Either[CodecFailure, Short] =
    Try(Shorts.fromByteArray(bytes)).toEither.left.map(e => CodecFailure(e.getMessage))

  /** Runs Decoding with a Int result instead of DecodeResult.
    * @param bytes Inputs data source.
    * @return Returns Either a Int (case Success) or a CodedFailure (case Failure).
    */
  protected def decodeInt(bytes: Array[Byte]): Either[CodecFailure, Int] =
    Try(Ints.fromByteArray(bytes)).toEither.left.map(e => CodecFailure(e.getMessage))

  /** Runs Decoding with a Long result instead of DecodeResult.
    * @param bytes Inputs data source.
    * @return Returns Either a Long (case Success) or a CodedFailure (case Failure).
    */
  protected def decodeLong(bytes: Array[Byte]): Either[CodecFailure, Long] =
    Try(Longs.fromByteArray(bytes)).toEither.left.map(e => CodecFailure(e.getMessage))

  /** Runs Encode with Boolean Bytes.
    * @param value Input Boolean Value
    * @return Returns a True Array of Byte case value equals True, or a False Array of Byte otherwise.
    */
  protected def encodeBoolean(value: Boolean): Array[Byte] = if (value) TrueBytes else FalseBytes

  /** Runs Decoding with a Boolean result instead of DecodeResult.
    * @param bytes Inputs data source.
    * @return Returns Either a Boolean (case Success) or a CodedFailure (case Failure).
    */
  protected def decodeBoolean(bytes: Array[Byte]): Either[CodecFailure, Boolean] =
    Try(bytes.take(1).sameElements(TrueBytes)).toEither.left.map(e => CodecFailure(e.getMessage))

}

/** Codec Object */
object Codec {
  val SignatureLength: Int = 64
  val TrueBytes: Array[Byte] = Array[Byte](1.toByte)
  val FalseBytes: Array[Byte] = Array[Byte](0.toByte)
}

/** Block Checkpoint Codec. */
object BlockCheckpointCodec extends Codec[BlockCheckpoint] {
  /** Runs Encode.
    * @param bcp Inputs BlockCheckpoint object.
    * @return Returns a encoded Array of Byte.
    */
  override def encode(bcp: BlockCheckpoint): Array[Byte] = {
    val result = new Array[Byte](Ints.BYTES + Codec.SignatureLength)
    System.arraycopy(Ints.toByteArray(bcp.height), 0, result, 0, Ints.BYTES)
    System.arraycopy(bcp.signature, 0, result, Ints.BYTES, Codec.SignatureLength)
    result
  }

  /** Runs Decode.
    * @param arr Input data source.
    * @return Returns Either a DecodeResult of Type BlockCheckpoint (case Success) or a CodecFailure (case Failure).
    */
  override def decode(arr: Array[Byte]): Either[CodecFailure, DecodeResult[BlockCheckpoint]] = {
    val len = Ints.BYTES + Codec.SignatureLength
    for {
      height <- Try(Ints.fromByteArray(arr.take(Ints.BYTES))).toEither.left.map(e => CodecFailure(e.getMessage))
      signature <- Either.cond(arr.length >= len, arr.slice(Ints.BYTES, len), CodecFailure("not enough bytes for signature"))
    } yield DecodeResult(len, BlockCheckpoint(height, signature))
  }
}

/** Codec Checkpoint Object.  */
object CheckpointCodec extends Codec[Checkpoint] {
  private val itemsCodec = SeqCodec(BlockCheckpointCodec)

  /** Runs Encode.
    * @param value Inputs Checkpoint.
    * @return Returns a encoded Array of Byte.
    */
  override def encode(value: Checkpoint): Array[Byte] = {
    val r = itemsCodec.encode(value.items)
    val result = new Array[Byte](Codec.SignatureLength + r.length)
    System.arraycopy(value.signature, 0, result, 0, Codec.SignatureLength)
    System.arraycopy(r, 0, result, Codec.SignatureLength, r.length)
    result
  }

  /** Runs Decode.
    * @param bytes Input data source
    * @return Returns Either a DecodeResult of Type Checkpoint (case Success) or a CodecFailure (case Failure).
    */
  override def decode(bytes: Array[Byte]): Either[CodecFailure, DecodeResult[Checkpoint]] = {
    val signature = bytes.take(Codec.SignatureLength)
    for {
      _ <- Either.cond(signature.length == Codec.SignatureLength, (), CodecFailure("incorrect signature length"))
      items <- itemsCodec.decode(bytes.slice(Codec.SignatureLength, bytes.length))
    } yield DecodeResult(Codec.SignatureLength + items.length, Checkpoint(items.value, signature))
  }
}

/** Parametrized Class for Sequence Codec.
  * @param valueCodec Inputs Codec of Type A.
  * @tparam A Parametrized Type.
  */
case class SeqCodec[A](valueCodec: Codec[A]) extends Codec[Seq[A]] {
  /** Runs Encode.
    * @param value Inputs a Sequence of Parameter Type A.
    * @return Returns a encoded Array of Byte.
    */
  override def encode(value: Seq[A]): Array[Byte] = {
    val builder = Array.newBuilder[Byte]
    value.foreach { item =>
      builder.++=(valueCodec.encode(item))
    }
    val bytes = builder.result()
    val len = bytes.length
    val result = new Array[Byte](Ints.BYTES + len)
    System.arraycopy(Ints.toByteArray(value.length), 0, result, 0, Ints.BYTES)
    System.arraycopy(bytes, 0, result, Ints.BYTES, len)
    result
  }

  /** Runs Decode.
    * @param bytes Inputs data source.
    * @return Returns Either a DecodeResult of Sequence of Type A (case Success) or a CodecFailure (case Failure).
    */
  override def decode(bytes: Array[Byte]): Either[CodecFailure, DecodeResult[Seq[A]]] = {
    val n = Try(Ints.fromByteArray(bytes.take(Ints.BYTES))).toEither.left.map(e => CodecFailure(e.getMessage))
    if (n.isRight) {
      val expectedLength = n.right.get
      val builder = Seq.newBuilder[A]
      var i = Ints.BYTES
      var error = false
      while (i < bytes.length && !error) {
        val r = valueCodec.decode(bytes.slice(i, bytes.length))
        if (r.isRight) {
          val rr = r.right.get
          i = i + rr.length
          builder.+=(rr.value)
        } else {
          error = true
        }
      }
      val result = builder.result()
      Either.cond(!error && expectedLength == result.length, DecodeResult(i, result), CodecFailure(s"failed to deserialize $expectedLength items"))
    } else Left(n.left.get)
  }
}

/** Lunes Balance Value Coded Object. */
object LunesBalanceValueCodec extends Codec[(Long, Long, Long)] {
  /** Runs Encode.
    * @param value Inputs a Tuple of (Long, Long, Long).
    * @return Returns a encoded Array of Byte.
    */
  override def encode(value: (Long, Long, Long)): Array[Byte] = {
    val result = new Array[Byte](3 * Longs.BYTES)
    System.arraycopy(Longs.toByteArray(value._1), 0, result, 0, Longs.BYTES)
    System.arraycopy(Longs.toByteArray(value._2), 0, result, Longs.BYTES, Longs.BYTES)
    System.arraycopy(Longs.toByteArray(value._3), 0, result, 2 * Longs.BYTES, Longs.BYTES)
    result
  }

  /** Runs Decode.
    * @param bytes Inputs data source.
    * @return Returns Either a DecodeResult of a Tuple (Long, Long, Long) (case Success) or a CodecFailure (case Failure).
    */
  override def decode(bytes: Array[Byte]): Either[CodecFailure, DecodeResult[(Long, Long, Long)]] = {
    for {
      v1 <- decodeLong(bytes.take(Longs.BYTES))
      v2 <- decodeLong(bytes.slice(Longs.BYTES, Longs.BYTES * 2))
      v3 <- decodeLong(bytes.slice(Longs.BYTES * 2, Longs.BYTES * 3))
    } yield DecodeResult(Longs.BYTES * 3, (v1, v2, v3))
  }
}

/** Balance Snapshot for Lunes Value Codec.  */
object BalanceSnapshotValueCodec extends Codec[(Int, Long, Long)] {
  /** Runs Encode.
    * @param value Inputs a Tuple(Int, Long, Long).
    * @return Returns a Encoded Array of Byte.
    */
  override def encode(value: (Int, Long, Long)): Array[Byte] = {
    val result = new Array[Byte](Ints.BYTES + 2 * Longs.BYTES)
    System.arraycopy(Ints.toByteArray(value._1), 0, result, 0, Ints.BYTES)
    System.arraycopy(Longs.toByteArray(value._2), 0, result, Ints.BYTES, Longs.BYTES)
    System.arraycopy(Longs.toByteArray(value._3), 0, result, Ints.BYTES + Longs.BYTES, Longs.BYTES)
    result
  }

  /** Runs Decode.
    * @param bytes Input data source.
    * @return Returns Either a DecodeResult of Tuple (Int, Long, Long) (case Success) or a CodecFailure (case Failure).
    */
  override def decode(bytes: Array[Byte]): Either[CodecFailure, DecodeResult[(Int, Long, Long)]] = {
    for {
      v1 <- decodeInt(bytes.take(Ints.BYTES))
      v2 <- decodeLong(bytes.slice(Ints.BYTES, Ints.BYTES + Longs.BYTES))
      v3 <- decodeLong(bytes.slice(Ints.BYTES + Longs.BYTES, Ints.BYTES + 2 * Longs.BYTES))
    } yield DecodeResult(Ints.BYTES + 2 * Longs.BYTES, (v1, v2, v3))
  }
}

/** Order Fill Infor Value Codec.  */
object OrderFillInfoValueCodec extends Codec[OrderFillInfo] {
  /** Runs Encode.
    * @param value Inputs a OrderFillInfo Object.
    * @return Returns a encoded Array of Byte.
    */
  override def encode(value: OrderFillInfo): Array[Byte] = {
    val result = new Array[Byte](2 * Longs.BYTES)
    System.arraycopy(Longs.toByteArray(value.volume), 0, result, 0, Longs.BYTES)
    System.arraycopy(Longs.toByteArray(value.fee), 0, result, Longs.BYTES, Longs.BYTES)
    result
  }

  /** Runs Decode.
    * @param bytes Input data source.
    * @returnReturns Either a DecodeResult of Type OrderFillInfo.(case Success) or a CodecFailure (case Failure).
    */
  override def decode(bytes: Array[Byte]): Either[CodecFailure, DecodeResult[OrderFillInfo]] = {
    for {
      vol <- decodeLong(bytes.take(Longs.BYTES))
      fee <- decodeLong(bytes.slice(Longs.BYTES, 2 * Longs.BYTES))
    } yield DecodeResult(Longs.BYTES * 2, OrderFillInfo(vol, fee))
  }
}

/** Vote Codec Object.  */
object VoteCodec extends Codec[(Short, Int)] {
  /** Runs Encode.
    * @param value Inputs a Tuple of (Short, Int).
    * @return Returns a encoded Array of Byte.
    */
  override def encode(value: (Short, Int)): Array[Byte] = {
    val result = new Array[Byte](Shorts.BYTES + Ints.BYTES)
    System.arraycopy(Shorts.toByteArray(value._1), 0, result, 0, Shorts.BYTES)
    System.arraycopy(Ints.toByteArray(value._2), 0, result, Shorts.BYTES, Ints.BYTES)
    result
  }

  /** Runs Decode.
    * @param bytes Input data source.
    * @return Returns Either a DecodeResult of Tuple (Short, Int) (case Success) or a CodecFailure (case Failure).
    */
  override def decode(bytes: Array[Byte]): Either[CodecFailure, DecodeResult[(Short, Int)]] = {
    for {
      v1 <- decodeShort(bytes.take(Shorts.BYTES))
      v2 <- decodeInt(bytes.slice(Shorts.BYTES, Shorts.BYTES + Ints.BYTES))
    } yield DecodeResult(Shorts.BYTES + Ints.BYTES, (v1, v2))
  }
}

/** Votes Mapping Codec Object.  */
object VotesMapCodec extends Codec[Map[Short, Int]] {
  private val itemsCodec = SeqCodec(VoteCodec)

  /** Runs Encode.
    * @param value Inputs a Map Short -> Int.
    * @return Returns a encoded Array of Byte.
    */
  override def encode(value: Map[Short, Int]): Array[Byte] = itemsCodec.encode(value.toSeq)

  /** Runs Decode.
    * @param bytes Input data source.
    * @return Returns Either a DecodeResult of Map Short -> Int (case Success) or a CodecFailure (case Failure).
    */
  override def decode(bytes: Array[Byte]): Either[CodecFailure, DecodeResult[Map[Short, Int]]] = itemsCodec.decode(bytes)
    .map(r => DecodeResult(r.length, r.value.toMap))
}

/** Transaction Value Coded.  */
object TransactionsValueCodec extends Codec[(Int, Array[Byte])] {
  /** Runs Encode.
    * @param value Value for encoding.
    * @return Returns a encoded Array of Byte.
    */
  override def encode(value: (Int, Array[Byte])): Array[Byte] = {
    val len = value._2.length
    val result = new Array[Byte](2 * Ints.BYTES + len)
    System.arraycopy(Ints.toByteArray(value._1), 0, result, 0, Ints.BYTES)
    System.arraycopy(Ints.toByteArray(len), 0, result, Ints.BYTES, Ints.BYTES)
    System.arraycopy(value._2, 0, result, 2 * Ints.BYTES, len)
    result
  }

  /** Runs Decode.
    * @param bytes Inputs data source.
    * @return Returns Either a DecodeResult of Tuple (Int, Arra[Byte]) (case Success) or a CodecFailure (case Failure).
    */
  override def decode(bytes: Array[Byte]): Either[CodecFailure, DecodeResult[(Int, Array[Byte])]] = {
    for {
      v1 <- decodeInt(bytes.take(Ints.BYTES))
      l <- decodeInt(bytes.slice(Ints.BYTES, 2 * Ints.BYTES))
      a = bytes.slice(2 * Ints.BYTES, 2 * Ints.BYTES + l)
      _ <- Either.cond(a.length == l, (), CodecFailure("incorrect array length"))
    } yield DecodeResult(2 * Ints.BYTES + l, (v1, a))
  }
}

/** Asset Info Codec Object. */
object AssetInfoCodec extends Codec[AssetInfo] {
  /** Runs Encode.
    * @param value Inputs Asset Info.
    * @return Returns a encode Array of Byte.
    */
  override def encode(value: AssetInfo): Array[Byte] = {
    val result = new Array[Byte](1 + Longs.BYTES)
    System.arraycopy(encodeBoolean(value.isReissuable), 0, result, 0, 1)
    System.arraycopy(Longs.toByteArray(value.volume), 0, result, 1, Longs.BYTES)
    result
  }

  /** Runs Decode.
    * @param bytes Inputs data source.
    * @return Returns Either a DecodeResult of Type AssetInfo (case Success) or a CodecFailure (case Failure).
    */
  override def decode(bytes: Array[Byte]): Either[CodecFailure, DecodeResult[AssetInfo]] = {
    for {
      v1 <- decodeBoolean(bytes.take(1))
      v2 <- decodeLong(bytes.slice(1, Longs.BYTES + 1))
    } yield DecodeResult(Longs.BYTES + 1, AssetInfo(v1, v2))
  }
}

/** Alias Codec Object */
object AliasCodec extends Codec[Alias] {
  /** Runs Encode.
    * @param value Input Alias.
    * @return Returns encoded Array of Byte.
    */
  override def encode(value: Alias): Array[Byte] = ByteStrCodec.encode(value.bytes)

  /** Runs Decode.
    * @param bytes Inputs data source.
    * @return Returns Either a DecodeResult of Type Alias (case Success) or a CodecFailure (case Failure).
    */
  override def decode(bytes: Array[Byte]): Either[CodecFailure, DecodeResult[Alias]] = {
    for {
      r <- ByteStrCodec.decode(bytes)
      a <- Alias.fromBytes(r.value.arr).left.map(e => CodecFailure(e.toString))
    } yield DecodeResult(r.length, a)
  }
}

/** Alias Sequence Codec Object. */
object AliasSeqCodec extends Codec[Seq[Alias]] {
  private val itemsCodec = SeqCodec(AliasCodec)

  /** Runs Encode.
    * @param value Inputs a Sequence of Alias.
    * @return Returns a encoded Array of Byte.
    */
  override def encode(value: Seq[Alias]): Array[Byte] = itemsCodec.encode(value)

  /** Runs Decode.
    * @param bytes Input data source.
    * @return Returns Either a DecodeResult of Sequence of Alias (case Success) or a CodecFailure (case Failure).
    */
  override def decode(bytes: Array[Byte]): Either[CodecFailure, DecodeResult[Seq[Alias]]] = itemsCodec.decode(bytes)
}

/** String Codec Object */
object StringCodec extends Codec[String] {
  /** Runs Encode.
    * @param value Input String.
    * @return Returns encoded Array of Byte.
    */
  override def encode(value: String): Array[Byte] = {
    val bytes = value.getBytes(Charsets.UTF_8)
    val len = bytes.length
    val result = new Array[Byte](Ints.BYTES + len)
    System.arraycopy(Ints.toByteArray(len), 0, result, 0, Ints.BYTES)
    System.arraycopy(bytes, 0, result, Ints.BYTES, len)
    result
  }

  /** Runs Decode.
    * @param bytes Input data source.
    * @return Returns Either a DecodeResult of Type String (case Success) or a CodecFailure (case Failure).
    */
  override def decode(bytes: Array[Byte]): Either[CodecFailure, DecodeResult[String]] = {
    for {
      s <- decodeInt(bytes.take(Ints.BYTES))
      a = bytes.slice(Ints.BYTES, Ints.BYTES + s)
      _ <- Either.cond(a.length == s, (), CodecFailure("incorrect string byte representation length"))
      v <- Try(new String(a, Charsets.UTF_8)).toEither.left.map(e => CodecFailure(e.getMessage))
    } yield DecodeResult(Ints.BYTES + s, v)
  }
}

/** Order to Transaction ID Codec Object. */
object OrderToTxIdsCodec extends Codec[Set[String]] {
  private val itemsCodec = SeqCodec(StringCodec)

  /** Runs Encode.
    * @param value Input a Set of String.
    * @return Returns a encoded Array of Byte.
    */
  override def encode(value: Set[String]): Array[Byte] = itemsCodec.encode(value.toSeq)

  /** Runs Decode.
    * @param bytes Input data source.
    * @return Returns Either a DecodeResult of Set String (case Success) or a CodecFailure (case Failure).
    */
  override def decode(bytes: Array[Byte]): Either[CodecFailure, DecodeResult[Set[String]]] = itemsCodec.decode(bytes)
    .right.map(r => DecodeResult(r.length, r.value.toSet))
}

/** Order IDs Codec Object.*/
object OrderIdsCodec extends Codec[Array[String]] {
  private val itemsCodec = SeqCodec(StringCodec)

  /** Runs Encode.
    * @param value Input generic Array of Byte.
    * @return Returns encoded Array of Byte.
    */
  override def encode(value: Array[String]): Array[Byte] = itemsCodec.encode(value.toSeq)

  /** Runs Decode.
    * @param bytes Input data Source.
    * @returnReturns Either a DecodeResult of Array of String (case Success) or a CodecFailure (case Failure).
    */
  override def decode(bytes: Array[Byte]): Either[CodecFailure, DecodeResult[Array[String]]] = itemsCodec.decode(bytes)
    .right.map(r => DecodeResult(r.length, r.value.toArray))
}

/** Portfolio Item Codec. */
object PortfolioItemCodec extends Codec[(String, Long)] {
  /** Runs Encode.
    * @param value Inuts a Tuple (String, Long).
    * @return Returns a encoded Array of Byte.
    */
  override def encode(value: (String, Long)): Array[Byte] = {
    val r = StringCodec.encode(value._1)
    val len = r.length
    val result = new Array[Byte](len + Longs.BYTES)
    System.arraycopy(r, 0, result, 0, len)
    System.arraycopy(Longs.toByteArray(value._2), 0, result, len, Longs.BYTES)
    result
  }

  /** Runs Decode.
    * @param bytes Input data source.
    * @return Returns Either a DecodeResult of Tuple (String, Long) (case Success) or a CodecFailure (case Failure).
    */
  override def decode(bytes: Array[Byte]): Either[CodecFailure, DecodeResult[(String, Long)]] = {
    for {
      r <- StringCodec.decode(bytes)
      v2 <- decodeLong(bytes.slice(r.length, r.length + Longs.BYTES))
    } yield DecodeResult(r.length + Longs.BYTES, (r.value, v2))
  }
}

/** Portfolio Codec Object. */
object PortfolioCodec extends Codec[Map[String, Long]] {
  private val itemsCodec = SeqCodec(PortfolioItemCodec)

  /** Runs Encode.
    * @param value Inputs a Map String -> Long.
    * @return Returns a encoded Array of Byte.
    */
  override def encode(value: Map[String, Long]): Array[Byte] = itemsCodec.encode(value.toSeq)

  /** Runs Decode.
    * @param bytes Input data source.
    * @return Returns Either a DecodeResult of a Map String -> Long (case Success) or a CodecFailure (case Failure).
    */
  override def decode(bytes: Array[Byte]): Either[CodecFailure, DecodeResult[Map[String, Long]]] = itemsCodec.decode(bytes)
    .right.map(r => DecodeResult(r.length, r.value.toMap))
}

/** Byte String Codec Object. */
object ByteStrCodec extends Codec[ByteStr] {
  /** Runs Encode.
    * @param value Value for encoding.
    * @return Returns a encoded Array of Byte.
    */
  override def encode(value: ByteStr): Array[Byte] = {
    val len = value.arr.length
    val result = new Array[Byte](Ints.BYTES + len)
    System.arraycopy(Ints.toByteArray(len), 0, result, 0, Ints.BYTES)
    System.arraycopy(value.arr, 0, result, Ints.BYTES, len)
    result
  }

  /** Runs Decode.
    * @param bytes Inputs data source.
    * @return Returns Either a DecodeResult of Type BytStr (case Success) or a CodedFailure (case Failure).
    */
  override def decode(bytes: Array[Byte]): Either[CodecFailure, DecodeResult[ByteStr]] = {
    for {
      l <- decodeInt(bytes.take(Ints.BYTES))
      a = bytes.slice(Ints.BYTES, Ints.BYTES + l)
      _ <- Either.cond(a.length == l, (), CodecFailure("incorrect ByteStr length"))
    } yield DecodeResult(Ints.BYTES + l, ByteStr(a))
  }
}

/** Short Codec Object. */
object ShortCodec extends Codec[Short] {
  /** Runs Encode.
    * @param value Value for encoding.
    * @return Returns a encoded Array of Byte.
    */
  override def encode(value: Short): Array[Byte] = Shorts.toByteArray(value)

  /** Runs Decode.
    * @param bytes Inputs data source.
    * @return Returns Either a DecodeResult of Type [A] (case Success) or a CodedFailure (case Failure).
    */
  override def decode(bytes: Array[Byte]): Either[CodecFailure, DecodeResult[Short]] = {
    for {
      v <- Try(Shorts.fromByteArray(bytes.take(Shorts.BYTES))).toEither.left.map(e => CodecFailure(e.getMessage))
    } yield DecodeResult(Shorts.BYTES, v)
  }
}

/** Short Sequence Codec Object*/
object ShortSeqCodec extends Codec[Seq[Short]] {
  private val itemsCodec = SeqCodec(ShortCodec)

  /** Runs Encode.
    * @param value Value for encoding.
    * @return Returns a encoded Array of Byte.
    */
  override def encode(value: Seq[Short]): Array[Byte] = itemsCodec.encode(value)

  /** Runs Decode.
    * @param bytes Inputs data source.
    * @return Returns Either a DecodeResult of Sequence of Short (case Success) or a CodedFailure (case Failure).
    */
  override def decode(bytes: Array[Byte]): Either[CodecFailure, DecodeResult[Seq[Short]]] = itemsCodec.decode(bytes)
}

/** Key codec Object.*/
object KeyCodec extends Codec[ByteStr] {
  private val KeySize = 8

  /** Runs Encode.
    * @param value Value for encoding.
    * @return Returns a encoded Array of Byte.
    */
  override def encode(value: ByteStr): Array[Byte] = value.arr.take(KeySize)

  /** Runs Decode.
    * @param bytes Inputs data source.
    * @return Returns Either a DecodeResult of Type ByteStr (case Success) or a CodedFailure (case Failure).
    */
  override def decode(bytes: Array[Byte]): Either[CodecFailure, DecodeResult[ByteStr]] = {
    val a = bytes.take(KeySize)
    Either.cond(a.length == KeySize, DecodeResult(KeySize, ByteStr(a)), CodecFailure("incorrect key size"))
  }
}

/** Key Sequence Codec Object. */
object KeySeqCodec extends Codec[Seq[ByteStr]] {
  val itemsCodec = SeqCodec(KeyCodec)

  /** Runs Encode.
    * @param value Value for encoding.
    * @return Returns a encoded Array of Byte.
    */
  override def encode(value: Seq[ByteStr]): Array[Byte] = itemsCodec.encode(value)

  /** Runs Decode.
    * @param bytes Inputs data source.
    * @return Returns Either a DecodeResult of Sequence of ByteStr (case Success) or a CodedFailure (case Failure).
    */
  override def decode(bytes: Array[Byte]): Either[CodecFailure, DecodeResult[Seq[ByteStr]]] = itemsCodec.decode(bytes)
}

/** ID32 Codec Object. */
object Id32Codec extends Codec[ByteStr] {
  private val IdSize = 32

  /** Runs Encode.
    * @param value Value for encoding.
    * @return Returns a encoded Array of Byte.
    */
  override def encode(value: ByteStr): Array[Byte] = value.arr.take(IdSize)

  /** Runs Decode.
    * @param bytes Inputs data source.
    * @return Returns Either a DecodeResult of Type ByteStr (case Success) or a CodedFailure (case Failure).
    */
  override def decode(bytes: Array[Byte]): Either[CodecFailure, DecodeResult[ByteStr]] = {
    val a = bytes.take(IdSize)
    Either.cond(a.length == IdSize, DecodeResult(IdSize, ByteStr(a)), CodecFailure("incorrect id32 size"))
  }
}

/** ID32 Sequence Codec Object.*/
object Id32SeqCodec extends Codec[Seq[ByteStr]] {
  val itemsCodec = SeqCodec(Id32Codec)

  /** Runs Encode.
    * @param value Value for encoding.
    * @return Returns a encoded Array of Byte.
    */
  override def encode(value: Seq[ByteStr]): Array[Byte] = itemsCodec.encode(value)

  /** Runs Decode.
    * @param bytes Inputs data source.
    * @return Returns Either a DecodeResult of Sequence of ByteStr (case Success) or a CodedFailure (case Failure).
    */
  override def decode(bytes: Array[Byte]): Either[CodecFailure, DecodeResult[Seq[ByteStr]]] = itemsCodec.decode(bytes)
}
