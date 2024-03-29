package io.lunes.transaction.assets

import com.google.common.base.Charsets
import com.google.common.primitives.{Bytes, Longs}
import io.lunes.crypto
import io.lunes.state2.ByteStr
import monix.eval.Coeval
import play.api.libs.json.{JsObject, Json}
import scorex.account.{PrivateKeyAccount, PublicKeyAccount}
import scorex.serialization.Deser
import io.lunes.transaction.TransactionParser._
import io.lunes.transaction.{ValidationError, _}

import scala.util.{Failure, Success, Try}

/**
 * @param sender
 * @param name
 * @param description
 * @param quantity
 * @param decimals
 * @param reissuable
 * @param fee
 * @param timestamp
 * @param signature
 */
case class IssueTransaction private (
  sender: PublicKeyAccount,
  name: Array[Byte],
  description: Array[Byte],
  quantity: Long,
  decimals: Byte,
  reissuable: Boolean,
  fee: Long,
  timestamp: Long,
  signature: ByteStr
) extends SignedTransaction
    with FastHashId {

  override val assetFee: (Option[AssetId], Long) = (None, fee)
  override val transactionType: TransactionType.Value =
    TransactionType.IssueTransaction

  val assetId = id

  val bodyBytes: Coeval[Array[Byte]] = Coeval.evalOnce(
    Bytes.concat(
      Array(transactionType.id.toByte),
      sender.publicKey,
      Deser.serializeArray(name),
      Deser.serializeArray(description),
      Longs.toByteArray(quantity),
      Array(decimals),
      if (reissuable) Array(1: Byte) else Array(0: Byte),
      Longs.toByteArray(fee),
      Longs.toByteArray(timestamp)
    )
  )

  override val json: Coeval[JsObject] = Coeval.evalOnce(
    jsonBase() ++ Json.obj(
      "assetId"     -> assetId().base58,
      "name"        -> new String(name, Charsets.UTF_8),
      "description" -> new String(description, Charsets.UTF_8),
      "quantity"    -> quantity,
      "decimals"    -> decimals,
      "reissuable"  -> reissuable
    )
  )

  override val bytes = Coeval.evalOnce(
    Bytes.concat(Array(transactionType.id.toByte), signature.arr, bodyBytes())
  )

}

/**
 */
object IssueTransaction {
  val MaxDescriptionLength = 1000
  val MaxAssetNameLength   = 16
  val MinAssetNameLength   = 4
  val MaxDecimals          = 8

  /**
   * @param bytes
   * @return
   */
  def parseBytes(bytes: Array[Byte]): Try[IssueTransaction] = Try {
    require(bytes.head == TransactionType.IssueTransaction.id)
    parseTail(bytes.tail).get
  }

  /**
   * @param bytes
   * @return
   */
  def parseTail(bytes: Array[Byte]): Try[IssueTransaction] = Try {
    val signature = ByteStr(bytes.slice(0, SignatureLength))
    val txId      = bytes(SignatureLength)
    require(
      txId == TransactionType.IssueTransaction.id.toByte,
      s"Signed tx id is not match"
    )
    val sender = PublicKeyAccount(
      bytes.slice(SignatureLength + 1, SignatureLength + KeyLength + 1)
    )
    val (assetName, descriptionStart) =
      Deser.parseArraySize(bytes, SignatureLength + KeyLength + 1)
    val (description, quantityStart) =
      Deser.parseArraySize(bytes, descriptionStart)
    val quantity =
      Longs.fromByteArray(bytes.slice(quantityStart, quantityStart + 8))
    val decimals = bytes.slice(quantityStart + 8, quantityStart + 9).head
    val reissuable =
      bytes.slice(quantityStart + 9, quantityStart + 10).head == (1: Byte)
    val fee =
      Longs.fromByteArray(bytes.slice(quantityStart + 10, quantityStart + 18))
    val timestamp =
      Longs.fromByteArray(bytes.slice(quantityStart + 18, quantityStart + 26))
    IssueTransaction
      .create(
        sender,
        assetName,
        description,
        quantity,
        decimals,
        reissuable,
        fee,
        timestamp,
        signature
      )
      .fold(
        left => Failure(new Exception(left.toString)),
        right => Success(right)
      )
  }.flatten

  /**
   * @param sender
   * @param name
   * @param description
   * @param quantity
   * @param decimals
   * @param reissuable
   * @param fee
   * @param timestamp
   * @param signature
   * @return
   */
  def create(
    sender: PublicKeyAccount,
    name: Array[Byte],
    description: Array[Byte],
    quantity: Long,
    decimals: Byte,
    reissuable: Boolean,
    fee: Long,
    timestamp: Long,
    signature: ByteStr
  ): Either[ValidationError, IssueTransaction] =
    if (quantity <= 0) {
      Left(ValidationError.NegativeAmount(quantity, "assets"))
    } else if (description.length > MaxDescriptionLength) {
      Left(ValidationError.TooBigArray)
    } else if (name.length < MinAssetNameLength || name.length > MaxAssetNameLength) {
      Left(ValidationError.InvalidName)
    } else if (decimals < 0 || decimals > MaxDecimals) {
      Left(ValidationError.TooBigArray)
    } else if (fee <= 0) {
      Left(ValidationError.InsufficientFee)
    } else {
      Right(
        IssueTransaction(
          sender,
          name,
          description,
          quantity,
          decimals,
          reissuable,
          fee,
          timestamp,
          signature
        )
      )
    }

  /**
   * @param sender
   * @param name
   * @param description
   * @param quantity
   * @param decimals
   * @param reissuable
   * @param fee
   * @param timestamp
   * @return
   */
  def create(
    sender: PrivateKeyAccount,
    name: Array[Byte],
    description: Array[Byte],
    quantity: Long,
    decimals: Byte,
    reissuable: Boolean,
    fee: Long,
    timestamp: Long
  ): Either[ValidationError, IssueTransaction] =
    create(
      sender,
      name,
      description,
      quantity,
      decimals,
      reissuable,
      fee,
      timestamp,
      ByteStr.empty
    ).right.map { unverified =>
      unverified.copy(signature = ByteStr(crypto.sign(sender, unverified.bodyBytes())))
    }
}
