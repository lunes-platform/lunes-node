package scorex.api.http.assets

import io.lunes.transaction.TransactionParser.SignatureStringLength
import io.lunes.transaction.assets.RegistryTransaction
import io.lunes.transaction.{AssetIdStringLength, ValidationError}
import io.swagger.annotations.{ApiModel, ApiModelProperty}
import scorex.account.{AddressOrAlias, PublicKeyAccount}
import scorex.api.http.BroadcastRequest


@ApiModel(value = "Signed Asset data transaction")
case class SignedRegistryRequest(@ApiModelProperty(value = "Base58 encoded sender public key", required = true)
                             senderPublicKey: String,
                                 @ApiModelProperty(value = "Base58 encoded Asset ID")
                             assetId: Option[String],
                                 @ApiModelProperty(value = "Recipient address", required = true)
                             recipient: String,
                                 @ApiModelProperty(required = true, example = "1000000")
                             amount: Long,
                                 @ApiModelProperty(required = true)
                             fee: Long,
                                 @ApiModelProperty(value = "Fee asset ID")
                             feeAssetId: Option[String],
                                 @ApiModelProperty(required = true)
                             timestamp: Long,
                                 @ApiModelProperty(value = "Base58 encoded userdata")
                             userdata: Option[String],
                                 @ApiModelProperty(required = true)
                             signature: String) extends BroadcastRequest {
  def toTx: Either[ValidationError, RegistryTransaction] = for {
    _sender <- PublicKeyAccount.fromBase58String(senderPublicKey)
    _assetId <- parseBase58ToOption(assetId.filter(_.length > 0), "invalid.assetId", AssetIdStringLength)
    _feeAssetId <- parseBase58ToOption(feeAssetId.filter(_.length > 0), "invalid.feeAssetId", AssetIdStringLength)
    _signature <- parseBase58(signature, "invalid.signature", SignatureStringLength)
    _userdata <- parseBase58(userdata.filter(_.length > 0), "invalid.userdata", RegistryTransaction.MaxUserdataLength)
    _account <- AddressOrAlias.fromString(recipient)
    t <- RegistryTransaction.create(_sender, timestamp, fee, _userdata.arr, _signature)
  } yield t
}
