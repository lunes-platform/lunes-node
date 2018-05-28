package scorex.api.http.assets

import play.api.libs.json.{Format, Json}
import io.lunes.transaction.assets.MassTransferTransaction.Transfer

case class MassTransferRequest(assetId: Option[String],
                               sender: String,
                               transfers: List[Transfer],
                               fee: Long,
                               timestamp: Option[Long] = None)

object MassTransferRequest {
  implicit val jsonFormat: Format[MassTransferRequest] = Json.format
}
