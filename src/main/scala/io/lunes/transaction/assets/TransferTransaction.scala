package io.lunes.transaction.assets

import com.google.common.primitives.{Bytes, Longs}
import io.lunes.crypto
import io.lunes.state2.ByteStr
import io.lunes.utils.base58Length
import monix.eval.Coeval
import play.api.libs.json.{JsObject, Json}
import scorex.account.{AddressOrAlias, PrivateKeyAccount, PublicKeyAccount}
import scorex.crypto.encode.Base58
import scorex.serialization.Deser
import io.lunes.transaction.TransactionParser._
import io.lunes.transaction.{ValidationError, _}

import scala.util.{Failure, Success, Try}

/**
  *
  * @param assetId
  * @param sender
  * @param recipient
  * @param amount
  * @param timestamp
  * @param feeAssetId
  * @param fee
  * @param attachment
  * @param signature
  */
case class TransferTransaction private(assetId: Option[AssetId],
                                       sender: PublicKeyAccount,
                                       recipient: AddressOrAlias,
                                       amount: Long,
                                       timestamp: Long,
                                       feeAssetId: Option[AssetId],
                                       fee: Long,
                                       attachment: Array[Byte],
                                       signature: ByteStr)
  extends SignedTransaction with FastHashId {
  override val transactionType: TransactionType.Value = TransactionType.TransferTransaction

  override val assetFee: (Option[AssetId], Long) = (feeAssetId, fee)

  val bodyBytes: Coeval[Array[Byte]] = Coeval.evalOnce {
    val timestampBytes = Longs.toByteArray(timestamp)
    val assetIdBytes = assetId.map(a => (1: Byte) +: a.arr).getOrElse(Array(0: Byte))
    val amountBytes = Longs.toByteArray(amount)
    val feeAssetIdBytes = feeAssetId.map(a => (1: Byte) +: a.arr).getOrElse(Array(0: Byte))
    val feeBytes = Longs.toByteArray(fee)

    Bytes.concat(Array(transactionType.id.toByte),
      sender.publicKey,
      assetIdBytes,
      feeAssetIdBytes,
      timestampBytes,
      amountBytes,
      feeBytes,
      recipient.bytes.arr,
      Deser.serializeArray(attachment))
  }

  override val json: Coeval[JsObject] = Coeval.evalOnce(jsonBase() ++ Json.obj(
    "recipient" -> recipient.stringRepr,
    "assetId" -> assetId.map(_.base58),
    "amount" -> amount,
    "feeAsset" -> feeAssetId.map(_.base58),
    "attachment" -> Base58.encode(attachment)
  ))

  override val bytes: Coeval[Array[Byte]] = Coeval.evalOnce(Bytes.concat(Array(transactionType.id.toByte), signature.arr, bodyBytes()))

}

/**
  *
  */
object TransferTransaction {

  val MaxAttachmentSize = 140
  val MaxAttachmentStringSize = base58Length(MaxAttachmentSize)

  /**
    *
    * @param bytes
    * @return
    */
  def parseTail(bytes: Array[Byte]): Try[TransferTransaction] = Try {

    val signature = ByteStr(bytes.slice(0, SignatureLength))
    val txId = bytes(SignatureLength)
    require(txId == TransactionType.TransferTransaction.id.toByte, s"Signed tx id is not match")
    val sender = PublicKeyAccount(bytes.slice(SignatureLength + 1, SignatureLength + KeyLength + 1))
    val (assetIdOpt, s0) = Deser.parseByteArrayOption(bytes, SignatureLength + KeyLength + 1, AssetIdLength)
    val (feeAssetIdOpt, s1) = Deser.parseByteArrayOption(bytes, s0, AssetIdLength)
    val timestamp = Longs.fromByteArray(bytes.slice(s1, s1 + 8))
    val amount = Longs.fromByteArray(bytes.slice(s1 + 8, s1 + 16))
    val feeAmount = Longs.fromByteArray(bytes.slice(s1 + 16, s1 + 24))

    (for {
      recRes <- AddressOrAlias.fromBytes(bytes, s1 + 24)
      (recipient, recipientEnd) = recRes
      (attachment, _) = Deser.parseArraySize(bytes, recipientEnd)
      tt <- TransferTransaction.create(assetIdOpt.map(ByteStr(_)), sender, recipient, amount, timestamp, feeAssetIdOpt.map(ByteStr(_)), feeAmount, attachment, signature)
    } yield tt).fold(left => Failure(new Exception(left.toString)), right => Success(right))
  }.flatten

  /**
    *
    * @param assetId
    * @param sender
    * @param recipient
    * @param amount
    * @param timestamp
    * @param feeAssetId
    * @param feeAmount
    * @param attachment
    * @param signature
    * @return
    */
  def create(assetId: Option[AssetId],
             sender: PublicKeyAccount,
             recipient: AddressOrAlias,
             amount: Long,
             timestamp: Long,
             feeAssetId: Option[AssetId],
             feeAmount: Long,
             attachment: Array[Byte],
             signature: ByteStr): Either[ValidationError, TransferTransaction] = {
    if (attachment.length > TransferTransaction.MaxAttachmentSize) {
      Left(ValidationError.TooBigArray)
    } else if (amount <= 0) {
      Left(ValidationError.NegativeAmount(amount, "lunes")) //CHECK IF AMOUNT IS POSITIVE
    } else if (Try(Math.addExact(amount, feeAmount)).isFailure) {
      Left(ValidationError.OverflowError) // CHECK THAT fee+amount won't overflow Long
    } else if (feeAmount <= 0) {
      Left(ValidationError.InsufficientFee)
    } else {
      Right(TransferTransaction(assetId, sender, recipient, amount, timestamp, feeAssetId, feeAmount, attachment, signature))
    }
  }

  /**
    *
    * @param assetId
    * @param sender
    * @param recipient
    * @param amount
    * @param timestamp
    * @param feeAssetId
    * @param feeAmount
    * @param attachment
    * @return
    */
  def create(assetId: Option[AssetId],
             sender: PrivateKeyAccount,
             recipient: AddressOrAlias,
             amount: Long,
             timestamp: Long,
             feeAssetId: Option[AssetId],
             feeAmount: Long,
             attachment: Array[Byte]): Either[ValidationError, TransferTransaction] = {
    create(assetId, sender, recipient, amount, timestamp, feeAssetId, feeAmount, attachment, ByteStr.empty).right.map { unsigned =>
      unsigned.copy(signature = ByteStr(crypto.sign(sender, unsigned.bodyBytes())))
    }
  }
}