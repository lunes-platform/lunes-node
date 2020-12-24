package io.lunes.http

import java.time.Instant
import javax.ws.rs.Path

import akka.http.scaladsl.server.Route
import io.lunes.utils.Shutdownable
import io.lunes.settings.{Constants, RestAPISettings}
import io.lunes.utils.HeightInfo
import io.swagger.annotations._
import monix.eval.Coeval
import play.api.libs.json.Json
import scorex.api.http.{ApiRoute, CommonApiFunctions}
import scorex.utils.ScorexLogging

/** Node API Route Class
  * @constructor Creates a Node API Route Class
  * @param settings Inputs a [[io.lunes.settings.RestAPISettings]] Object.
  * @param heights Inputs heights.
  * @param application Inputs the [[io.lunes.utils.Shutdownable]] object.
  */
@Path("/utils/lunesnode")
@Api(value = "utils")
case class NodeApiRoute(settings: RestAPISettings, heights: Coeval[(HeightInfo, HeightInfo)], application: Shutdownable)
  extends ApiRoute with CommonApiFunctions with ScorexLogging {

  override lazy val route = pathPrefix("utils" / "lunesnode") {
    status ~ version
  }

  /** Estabilishes the Version of the API Route.
    * @return Returns the Route for the version.
    */
  @Path("/version")
  @ApiOperation(value = "Version", notes = "Get Lunes node version", httpMethod = "GET")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Json Lunes node version")
  ))
  def version: Route = (get & path("version")) {
    complete(Json.obj("version" -> Constants.AgentName))
  }

  /** Estabilishes the Node Stopping Route.
    * @return Returns the fowarding Route.
    */
  //  @Path("/stop")
//  @ApiOperation(value = "Stop", notes = "Stop the node", httpMethod = "POST")
  def stop: Route = (post & path("stop") & withAuth) {
    log.info("Request to stop application")
    application.shutdown()
    complete(Json.obj("stopped" -> true))
  }

  /** Stabilishes the Status Route
    * @return Return the Route for the Node Status.
    */
  @Path("/status")
  @ApiOperation(value = "Status", notes = "Get status of the running core", httpMethod = "GET")
  def status: Route = (get & path("status")) {
    val ((bcHeight, bcTime), (stHeight, stTime)) = heights()
    val lastUpdated = bcTime max stTime
    complete(Json.obj(
      "blockchainHeight" -> bcHeight,
      "stateHeight" -> stHeight,
      "updatedTimestamp" -> lastUpdated,
      "updatedDate" -> Instant.ofEpochMilli(lastUpdated).toString
    ))
  }
}
