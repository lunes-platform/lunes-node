package scorex.api.http.assets

import io.swagger.annotations.{ApiModel, ApiModelProperty}
import play.api.libs.json.{Format, Json}
import scorex.account.PublicKeyAccount
import scorex.api.http.BroadcastRequest
import io.lunes.transaction.TransactionParser.SignatureStringLength
import io.lunes.transaction.assets.MassTransferTransaction
import io.lunes.transaction.{AssetIdStringLength, ValidationError}

object SignedMassTransferRequest {
  implicit val assetTransferRequestFormat: Format[SignedTransferRequest] = Json.format
}

@ApiModel(value = "Signed Asset transfer transaction")
case class SignedMassTransferRequest(@ApiModelProperty(value = "Base58 encoded sender public key", required = true)
                                     senderPublicKey: String,
                                     @ApiModelProperty(value = "Base58 encoded Asset ID")
                                     assetId: Option[String],
                                     @ApiModelProperty(value = "List of (recipient, amount) pairs", required = true)
                                     transfers: List[(String, Long)],
                                     @ApiModelProperty(required = true)
                                     fee: Long,
                                     @ApiModelProperty(required = true)
                                     timestamp: Long,
                                     @ApiModelProperty(required = true)
                                     signature: String) extends BroadcastRequest {
  def toTx: Either[ValidationError, MassTransferTransaction] = for {
    _sender <- PublicKeyAccount.fromBase58String(senderPublicKey)
    _assetId <- parseBase58ToOption(assetId.filter(_.length > 0), "invalid.assetId", AssetIdStringLength)
    _signature <- parseBase58(signature, "invalid.signature", SignatureStringLength)
    _transfers <- MassTransferTransaction.parseTransfersList(transfers)
    t <- MassTransferTransaction.create(_assetId, _sender, _transfers, timestamp, fee, _signature)
  } yield t
}
