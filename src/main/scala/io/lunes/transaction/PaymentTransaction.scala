package io.lunes.transaction

import java.util

import com.google.common.primitives.{Bytes, Ints, Longs}
import io.lunes.crypto
import io.lunes.state2.ByteStr
import monix.eval.Coeval
import play.api.libs.json.{JsObject, Json}
import scorex.account.{Address, PrivateKeyAccount, PublicKeyAccount}
import io.lunes.transaction.TransactionParser._

import scala.util.{Failure, Success, Try}

/**
  *
  * @param sender
  * @param recipient
  * @param amount
  * @param fee
  * @param timestamp
  * @param signature
  */
case class PaymentTransaction private(sender: PublicKeyAccount,
                                      recipient: Address,
                                      amount: Long,
                                      fee: Long,
                                      timestamp: Long,
                                      signature: ByteStr) extends SignedTransaction {
  override val transactionType = TransactionType.PaymentTransaction
  override val assetFee: (Option[AssetId], Long) = (None, fee)
  override val id: Coeval[AssetId] = Coeval.evalOnce(signature)

  override val json: Coeval[JsObject] = Coeval.evalOnce(jsonBase() ++ Json.obj(
    "recipient" -> recipient.address,
    "amount" -> amount))

  private val hashBytes: Coeval[Array[Byte]] = Coeval.evalOnce(Bytes.concat(Array(transactionType.id.toByte),
    Longs.toByteArray(timestamp), sender.publicKey, recipient.bytes.arr, Longs.toByteArray(amount), Longs.toByteArray(fee)))

  override val bodyBytes: Coeval[Array[Byte]] = Coeval.evalOnce(Bytes.concat(Ints.toByteArray(transactionType.id),
    Longs.toByteArray(timestamp), sender.publicKey, recipient.bytes.arr, Longs.toByteArray(amount), Longs.toByteArray(fee)))

  val hash: Coeval[Array[Byte]] = Coeval.evalOnce(crypto.fastHash(hashBytes()))

  override val bytes: Coeval[Array[Byte]] = Coeval.evalOnce(Bytes.concat(hashBytes(), signature.arr))

}

/**
  *
  */
object PaymentTransaction {

  val RecipientLength: Int = Address.AddressLength

  private val SenderLength = 32
  private val FeeLength = 8
  private val BaseLength = TimestampLength + SenderLength + RecipientLength + AmountLength + FeeLength + SignatureLength

  /**
    *
    * @param sender
    * @param recipient
    * @param amount
    * @param fee
    * @param timestamp
    * @return
    */
  def create(sender: PrivateKeyAccount, recipient: Address, amount: Long, fee: Long, timestamp: Long): Either[ValidationError, PaymentTransaction] = {
    create(sender, recipient, amount, fee, timestamp, ByteStr.empty).right.map(unsigned => {
      unsigned.copy(signature = ByteStr(crypto.sign(sender, unsigned.bodyBytes())))
    })
  }

  /**
    *
    * @param sender
    * @param recipient
    * @param amount
    * @param fee
    * @param timestamp
    * @param signature
    * @return
    */
  def create(sender: PublicKeyAccount,
             recipient: Address,
             amount: Long,
             fee: Long,
             timestamp: Long,
             signature: ByteStr): Either[ValidationError, PaymentTransaction] = {
    if (amount <= 0) {
      Left(ValidationError.NegativeAmount(amount, "lunes")) //CHECK IF AMOUNT IS POSITIVE
    } else if (fee <= 0) {
      Left(ValidationError.InsufficientFee) //CHECK IF FEE IS POSITIVE
    } else if (Try(Math.addExact(amount, fee)).isFailure) {
      Left(ValidationError.OverflowError) // CHECK THAT fee+amount won't overflow Long
    } else {
      Right(PaymentTransaction(sender, recipient, amount, fee, timestamp, signature))
    }
  }

  /**
    *
    * @param data
    * @return
    */
  def parseTail(data: Array[Byte]): Try[PaymentTransaction] = Try {
    require(data.length >= BaseLength, "Data does not match base length")

    var position = 0

    //READ TIMESTAMP
    val timestampBytes = data.take(TimestampLength)
    val timestamp = Longs.fromByteArray(timestampBytes)
    position += TimestampLength

    //READ SENDER
    val senderBytes = util.Arrays.copyOfRange(data, position, position + SenderLength)
    val sender = PublicKeyAccount(senderBytes)
    position += SenderLength

    //READ RECIPIENT
    val recipientBytes = util.Arrays.copyOfRange(data, position, position + RecipientLength)
    val recipient = Address.fromBytes(recipientBytes).right.get
    position += RecipientLength

    //READ AMOUNT
    val amountBytes = util.Arrays.copyOfRange(data, position, position + AmountLength)
    val amount = Longs.fromByteArray(amountBytes)
    position += AmountLength

    //READ FEE
    val feeBytes = util.Arrays.copyOfRange(data, position, position + FeeLength)
    val fee = Longs.fromByteArray(feeBytes)
    position += FeeLength

    //READ SIGNATURE
    val signatureBytes = util.Arrays.copyOfRange(data, position, position + SignatureLength)

    PaymentTransaction
      .create(sender, recipient, amount, fee, timestamp, ByteStr(signatureBytes))
      .fold(left => Failure(new Exception(left.toString)), right => Success(right))
  }.flatten

}
