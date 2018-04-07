package scorex.api.http.assets

import play.api.libs.json.{Format, Json}

case class RegistryRequest(assetId: Option[String],
                           feeAssetId: Option[String],
                           amount: Long,
                           fee: Long,
                           sender: String,
                           userdata: Option[String],
                           recipient: String,
                           timestamp: Option[Long] = None)
object RegistryRequest {
  implicit val transferFormat: Format[RegistryRequest] = Json.format
}
