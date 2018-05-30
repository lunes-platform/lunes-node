package io.lunes.transaction.assets

import com.google.common.primitives.{Bytes, Longs}
import io.lunes.crypto
import io.lunes.state2.ByteStr
import io.lunes.transaction.TransactionParser._
import io.lunes.transaction.{ValidationError, _}
import io.lunes.utils.base58Length
import monix.eval.Coeval
import play.api.libs.json.{JsObject, Json}
import scorex.account.{AddressOrAlias, PrivateKeyAccount, PublicKeyAccount}
import scorex.crypto.encode.Base58
import scorex.serialization.Deser

import scala.util.{Failure, Success, Try}

/**
  *
  * @param sender
  * @param timestamp
  * @param fee
  * @param userdata
  * @param signature
  */
case class RegistryTransaction private(
                                       sender: PublicKeyAccount,
                                       timestamp: Long,
                                       fee: Long,
                                       userdata: Array[Byte],
                                       signature: ByteStr)
  extends SignedTransaction with FastHashId {

  val recipient : AddressOrAlias = (AddressOrAlias.fromString("lunes")).toOption.get
  val amount:Long = 1000000000 // 10 lunes
  override val transactionType: TransactionType.Value = TransactionType.RegistryTransaction

  override val assetFee: (Option[AssetId], Long) = (None, fee)

  val bodyBytes: Coeval[Array[Byte]] = Coeval.evalOnce {
    val timestampBytes = Longs.toByteArray(timestamp)
    val amountBytes = Longs.toByteArray(amount)
    val feeBytes = Longs.toByteArray(fee)

    Bytes.concat(Array(transactionType.id.toByte),
      sender.publicKey,
      timestampBytes,
      amountBytes,
      feeBytes,
      recipient.bytes.arr,
      Deser.serializeArray(userdata))
  }

  override val json: Coeval[JsObject] = Coeval.evalOnce(jsonBase() ++ Json.obj(
    "recipient" -> recipient.stringRepr,
    "amount" -> amount,
    "userdata" -> Base58.encode(userdata)
  ))

  override val bytes: Coeval[Array[Byte]] = Coeval.evalOnce(Bytes.concat(Array(transactionType.id.toByte), signature.arr, bodyBytes()))

}

/**
  *
  */
object RegistryTransaction {

  val MaxUserdata = 140
  val MaxUserdataLength = base58Length(MaxUserdata)

  /**
    *
    * @param bytes
    * @return
    */
  def parseTail(bytes: Array[Byte]): Try[RegistryTransaction] = Try {

    val signature = ByteStr(bytes.slice(0, SignatureLength))
    val txId = bytes(SignatureLength)
    require(txId == TransactionType.RegistryTransaction.id.toByte, s"Signed tx id is not match")
    val sender = PublicKeyAccount(bytes.slice(SignatureLength + 1, SignatureLength + KeyLength + 1))
    val (assetIdOpt, s0) = Deser.parseByteArrayOption(bytes, SignatureLength + KeyLength + 1, AssetIdLength)
    val (feeAssetIdOpt, s1) = Deser.parseByteArrayOption(bytes, s0, AssetIdLength)
    val timestamp = Longs.fromByteArray(bytes.slice(s1, s1 + 8))
    val feeAmount = Longs.fromByteArray(bytes.slice(s1 + 16, s1 + 24))

    (for {
      recRes <- AddressOrAlias.fromBytes(bytes, s1 + 24)
      (recipient, recipientEnd) = recRes
      (userdata, _) = Deser.parseArraySize(bytes, recipientEnd)
      tt <- RegistryTransaction.create( sender, timestamp, feeAmount, userdata, signature)
    } yield tt).fold(left => Failure(new Exception(left.toString)), right => Success(right))
  }.flatten

  /**
    *
    * @param sender
    * @param timestamp
    * @param feeAmount
    * @param userdata
    * @param signature
    * @return
    */
  def create(
             sender: PublicKeyAccount,
             timestamp: Long,
             feeAmount: Long,
             userdata: Array[Byte],
             signature: ByteStr): Either[ValidationError, RegistryTransaction] = {
    val amount:Long = 1000000000 // 10 lunes
    if (userdata.length > RegistryTransaction.MaxUserdata) {
      Left(ValidationError.TooBigArray)
    }
    else if (Try(Math.addExact(amount, feeAmount)).isFailure) {
      Left(ValidationError.OverflowError) // CHECK THAT fee+amount won't overflow Long
    }
    else if (feeAmount <= 0) {
      Left(ValidationError.InsufficientFee)
    } else {
      Right(RegistryTransaction(sender, timestamp, feeAmount, userdata, signature))
    }
  }

  /**
    *
    * @param sender
    * @param timestamp
    * @param feeAmount
    * @param userdata
    * @return
    */
  def create(
             sender: PrivateKeyAccount,
             timestamp: Long,
             feeAmount: Long,
             userdata: Array[Byte]): Either[ValidationError, RegistryTransaction] = {
    create(sender,  timestamp, feeAmount, userdata, ByteStr.empty).right.map { unsigned =>
      unsigned.copy(signature = ByteStr(crypto.sign(sender, unsigned.bodyBytes())))
    }
  }
}
