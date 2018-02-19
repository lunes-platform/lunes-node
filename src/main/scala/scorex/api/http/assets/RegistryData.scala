package scorex.api.http.assets

import play.api.libs.json.{Format, Json}

case class RegistryData(assetId: Option[String],
                           feeAssetId: Option[String],
                           amount: Long,
                           fee: Long,
                           sender: String,
                           userdata: Option[String],
                           recipient: String,
                           timestamp: Option[Long] = None)

object RegistryData {
  implicit val transferFormat: Format[TransferRequest] = Json.format
}
