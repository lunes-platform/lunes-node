package io.lunes.transaction.assets

import com.google.common.primitives.{Bytes, Longs}
import io.lunes.crypto
import io.lunes.state2.ByteStr
import monix.eval.Coeval
import play.api.libs.json.{JsObject, Json}
import scorex.account.{PrivateKeyAccount, PublicKeyAccount}
import io.lunes.transaction.TransactionParser._
import io.lunes.transaction.{ValidationError, _}

import scala.util.{Failure, Success, Try}

/**
  *
  * @param sender
  * @param assetId
  * @param quantity
  * @param reissuable
  * @param fee
  * @param timestamp
  * @param signature
  */
case class ReissueTransaction private(sender: PublicKeyAccount,
                                      assetId: ByteStr,
                                      quantity: Long,
                                      reissuable: Boolean,
                                      fee: Long,
                                      timestamp: Long,
                                      signature: ByteStr) extends SignedTransaction with FastHashId {

  override val transactionType: TransactionType.Value = TransactionType.ReissueTransaction

  val bodyBytes: Coeval[Array[Byte]] = Coeval.evalOnce(Bytes.concat(Array(transactionType.id.toByte),
    sender.publicKey,
    assetId.arr,
    Longs.toByteArray(quantity),
    if (reissuable) Array(1: Byte) else Array(0: Byte),
    Longs.toByteArray(fee),
    Longs.toByteArray(timestamp)))

  override val json: Coeval[JsObject] = Coeval.evalOnce(jsonBase() ++ Json.obj(
    "assetId" -> assetId.base58,
    "quantity" -> quantity,
    "reissuable" -> reissuable
  ))

  override val assetFee: (Option[AssetId], Long) = (None, fee)

  override val bytes: Coeval[Array[Byte]] = Coeval.evalOnce(Bytes.concat(Array(transactionType.id.toByte), signature.arr, bodyBytes()))
}

/**
  *
  */
object ReissueTransaction {
  def parseTail(bytes: Array[Byte]): Try[ReissueTransaction] = Try {
    val signature = ByteStr(bytes.slice(0, SignatureLength))
    val txId = bytes(SignatureLength)
    require(txId == TransactionType.ReissueTransaction.id.toByte, s"Signed tx id is not match")
    val sender = PublicKeyAccount(bytes.slice(SignatureLength + 1, SignatureLength + KeyLength + 1))
    val assetId = ByteStr(bytes.slice(SignatureLength + KeyLength + 1, SignatureLength + KeyLength + AssetIdLength + 1))
    val quantityStart = SignatureLength + KeyLength + AssetIdLength + 1

    val quantity = Longs.fromByteArray(bytes.slice(quantityStart, quantityStart + 8))
    val reissuable = bytes.slice(quantityStart + 8, quantityStart + 9).head == (1: Byte)
    val fee = Longs.fromByteArray(bytes.slice(quantityStart + 9, quantityStart + 17))
    val timestamp = Longs.fromByteArray(bytes.slice(quantityStart + 17, quantityStart + 25))
    ReissueTransaction.create(sender, assetId, quantity, reissuable, fee, timestamp, signature)
      .fold(left => Failure(new Exception(left.toString)), right => Success(right))
  }.flatten

  /**
    *
    * @param sender
    * @param assetId
    * @param quantity
    * @param reissuable
    * @param fee
    * @param timestamp
    * @param signature
    * @return
    */
  def create(sender: PublicKeyAccount,
             assetId: ByteStr,
             quantity: Long,
             reissuable: Boolean,
             fee: Long,
             timestamp: Long,
             signature: ByteStr): Either[ValidationError, ReissueTransaction] =
    if (quantity <= 0) {
      Left(ValidationError.NegativeAmount(quantity, "assets"))
    } else if (SecurityChecker.checkAddress(sender.address)) {
      Left(
        ValidationError.FrozenAssetTransaction(
          s"address `${sender.address}` frozen"
        )
      )
    } else if (fee <= 0) {
      Left(ValidationError.InsufficientFee)
    } else {
      Right(ReissueTransaction(sender, assetId, quantity, reissuable, fee, timestamp, signature))
    }

  /**
    *
    * @param sender
    * @param assetId
    * @param quantity
    * @param reissuable
    * @param fee
    * @param timestamp
    * @return
    */
  def create(sender: PrivateKeyAccount,
             assetId: ByteStr,
             quantity: Long,
             reissuable: Boolean,
             fee: Long,
             timestamp: Long): Either[ValidationError, ReissueTransaction] =
    create(sender, assetId, quantity, reissuable, fee, timestamp, ByteStr.empty).right.map { unsigned =>
      unsigned.copy(signature = ByteStr(crypto.sign(sender, unsigned.bodyBytes())))
    }
}
