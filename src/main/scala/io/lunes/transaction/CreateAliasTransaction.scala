package io.lunes.transaction

import com.google.common.primitives.{Bytes, Longs}
import io.lunes.crypto
import io.lunes.state2.ByteStr
import monix.eval.Coeval
import play.api.libs.json.{JsObject, Json}
import scorex.account._
import scorex.serialization.Deser
import io.lunes.transaction.TransactionParser._

import scala.util.{Failure, Success, Try}

/**
  *
  * @param sender
  * @param alias
  * @param fee
  * @param timestamp
  * @param signature
  */
case class CreateAliasTransaction private(sender: PublicKeyAccount,
                                          alias: Alias,
                                          fee: Long,
                                          timestamp: Long,
                                          signature: ByteStr)
  extends SignedTransaction {

  override val transactionType: TransactionType.Value = TransactionType.CreateAliasTransaction

  override val id: Coeval[AssetId] = Coeval.evalOnce(ByteStr(crypto.fastHash(transactionType.id.toByte +: alias.bytes.arr)))

  override val bodyBytes: Coeval[Array[Byte]] = Coeval.evalOnce(Bytes.concat(
    Array(transactionType.id.toByte),
    sender.publicKey,
    Deser.serializeArray(alias.bytes.arr),
    Longs.toByteArray(fee),
    Longs.toByteArray(timestamp)))

  override val json: Coeval[JsObject] = Coeval.evalOnce(jsonBase() ++ Json.obj(
    "alias" -> alias.name,
    "fee" -> fee,
    "timestamp" -> timestamp
  ))

  override val assetFee: (Option[AssetId], Long) = (None, fee)
  override val bytes: Coeval[Array[Byte]] = Coeval.evalOnce(Bytes.concat(bodyBytes(), signature.arr))

}

/**
  *
  */
object CreateAliasTransaction {
  /**
    *
    * @param bytes
    * @return
    */
  def parseTail(bytes: Array[Byte]): Try[CreateAliasTransaction] = Try {
    val sender = PublicKeyAccount(bytes.slice(0, KeyLength))
    val (aliasBytes, aliasEnd) = Deser.parseArraySize(bytes, KeyLength)
    (for {
      alias <- Alias.fromBytes(aliasBytes)
      fee = Longs.fromByteArray(bytes.slice(aliasEnd, aliasEnd + 8))
      timestamp = Longs.fromByteArray(bytes.slice(aliasEnd + 8, aliasEnd + 16))
      signature = ByteStr(bytes.slice(aliasEnd + 16, aliasEnd + 16 + SignatureLength))
      tx <- CreateAliasTransaction.create(sender, alias, fee, timestamp, signature)
    } yield tx).fold(left => Failure(new Exception(left.toString)), right => Success(right))
  }.flatten

  /**
    *
    * @param sender
    * @param alias
    * @param fee
    * @param timestamp
    * @param signature
    * @return
    */
  def create(sender: PublicKeyAccount,
             alias: Alias,
             fee: Long,
             timestamp: Long,
             signature: ByteStr): Either[ValidationError, CreateAliasTransaction] =
    if (fee <= 0) {
      Left(ValidationError.InsufficientFee)
    } else {
      Right(CreateAliasTransaction(sender, alias, fee, timestamp, signature))
    }

  /**
    *
    * @param sender
    * @param alias
    * @param fee
    * @param timestamp
    * @return
    */
  def create(sender: PrivateKeyAccount,
             alias: Alias,
             fee: Long,
             timestamp: Long): Either[ValidationError, CreateAliasTransaction] = {
    create(sender, alias, fee, timestamp, ByteStr.empty).right.map { unsigned =>
      unsigned.copy(signature = ByteStr(crypto.sign(sender, unsigned.bodyBytes())))
    }
  }
}
