package scorex.api.http

import javax.ws.rs.Path

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.server.{Route, StandardRoute}
import io.lunes.network._
import io.lunes.settings.RestAPISettings
import io.lunes.state2.ByteStr
import io.netty.channel.group.ChannelGroup
import io.swagger.annotations._
import monix.eval.{Coeval, Task}
import monix.execution.Scheduler.Implicits.global
import play.api.libs.json._
import scorex.block.BlockHeader
import io.lunes.transaction._

import scala.concurrent._

@Path("/blocks")
@Api(value = "/blocks")
case class BlocksApiRoute(
  settings: RestAPISettings,
  history: History,
  blockchainUpdater: BlockchainUpdater,
  allChannels: ChannelGroup,
  checkpointProc: Checkpoint => Task[Either[ValidationError, Option[BigInt]]]
) extends ApiRoute {

  // todo: make this configurable and fix integration tests
  var MaxBlocksPerRequest = 100000
  val rollbackExecutor =
    monix.execution.Scheduler.singleThread(name = "debug-rollback")

  private val lastHeight: Coeval[Option[Int]] = lastObserved(
    blockchainUpdater.lastBlockInfo.map(_.height)
  )

  override lazy val route =
    pathPrefix("blocks") {
      signature ~ genesis ~ last ~ lastHeaderOnly ~ at ~ atHeaderOnly ~ seq ~ seqHeaderOnly ~ height ~ heightEncoded ~ child ~ address ~ delay ~ checkpoint
    }

  @Path("/address/{address}/{from}/{to}")
  @ApiOperation(
    value = "Address",
    notes = "Get list of blocks size of MaxBlocksPerRequest generated by specified address",
    httpMethod = "GET"
  )
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(
        name = "from",
        value = "Start block height",
        required = true,
        dataType = "integer",
        paramType = "path"
      ),
      new ApiImplicitParam(
        name = "to",
        value = "End block height",
        required = true,
        dataType = "integer",
        paramType = "path"
      ),
      new ApiImplicitParam(
        name = "address",
        value = "Wallet address ",
        required = true,
        dataType = "string",
        paramType = "path"
      )
    )
  )
  def address: Route =
    (path("address" / Segment / IntNumber / IntNumber) & get) { case (address, start, end) =>
      if (end >= 0 && start >= 0 && end - start >= 0 && end - start < MaxBlocksPerRequest) {
        val blocks = JsArray(
          (start to end).map { height =>
            (history.blockAt(height), height)
          }
            .filter(_._1.isDefined)
            .map(pair => (pair._1.get, pair._2))
            .filter(_._1.signerData.generator.address == address)
            .map { pair =>
              pair._1.json() + ("height" -> Json.toJson(pair._2))
            }
        )
        complete(blocks)
      } else complete(TooBigArrayAllocation)
    }

  @Path("/child/{signature}")
  @ApiOperation(
    value = "Child",
    notes = "Get children of specified block",
    httpMethod = "GET"
  )
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(
        name = "signature",
        value = "Base58-encoded signature",
        required = true,
        dataType = "string",
        paramType = "path"
      )
    )
  )
  def child: Route = (path("child" / Segment) & get) { encodedSignature =>
    withBlock(history, encodedSignature) { block =>
      complete(
        history
          .child(block)
          .map(_.json())
          .getOrElse[JsObject](
            Json.obj("status" -> "error", "details" -> "No child blocks")
          )
      )
    }
  }

  @Path("/delay/{signature}/{blockNum}")
  @ApiOperation(
    value = "Average delay",
    notes = "Average delay in milliseconds between last `blockNum` blocks starting from block with `signature`",
    httpMethod = "GET"
  )
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(
        name = "signature",
        value = "Base58-encoded signature",
        required = true,
        dataType = "string",
        paramType = "path"
      ),
      new ApiImplicitParam(
        name = "blockNum",
        value = "Number of blocks to count delay",
        required = true,
        dataType = "string",
        paramType = "path"
      )
    )
  )
  def delay: Route = (path("delay" / Segment / IntNumber) & get) { (encodedSignature, count) =>
    withBlock(history, encodedSignature) { block =>
      complete(
        history
          .averageDelay(block, count)
          .map(d => Json.obj("delay" -> d))
          .getOrElse[JsObject](
            Json.obj("status" -> "error", "details" -> "Internal error")
          )
      )
    }
  }

  @Path("/height/{signature}")
  @ApiOperation(
    value = "Height",
    notes = "Get height of a block by its Base58-encoded signature",
    httpMethod = "GET"
  )
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(
        name = "signature",
        value = "Base58-encoded signature",
        required = true,
        dataType = "string",
        paramType = "path"
      )
    )
  )
  def heightEncoded: Route = (path("height" / Segment) & get) { encodedSignature =>
    if (encodedSignature.length > TransactionParser.SignatureStringLength)
      complete(InvalidSignature)
    else {
      ByteStr
        .decodeBase58(encodedSignature)
        .toOption
        .toRight(InvalidSignature)
        .flatMap(s => history.heightOf(s).toRight(BlockNotExists)) match {
        case Right(h) => complete(Json.obj("height" -> h))
        case Left(e)  => complete(e)
      }
    }
  }

  @Path("/height")
  @ApiOperation(
    value = "Height",
    notes = "Get blockchain height",
    httpMethod = "GET"
  )
  def height: Route = (path("height") & get) {
    val x = lastHeight().getOrElse(0)
    complete(Json.obj("height" -> x))
  }

  @Path("/at/{height}")
  @ApiOperation(
    value = "At",
    notes = "Get block at specified height",
    httpMethod = "GET"
  )
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(
        name = "height",
        value = "Block height",
        required = true,
        dataType = "integer",
        paramType = "path"
      )
    )
  )
  def at: Route =
    (path("at" / IntNumber) & get)(at(_, includeTransactions = true))

  @Path("/headers/at/{height}")
  @ApiOperation(
    value = "At(Block header only)",
    notes = "Get block at specified height without transactions payload",
    httpMethod = "GET"
  )
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(
        name = "height",
        value = "Block height",
        required = true,
        dataType = "integer",
        paramType = "path"
      )
    )
  )
  def atHeaderOnly: Route = (path("headers" / "at" / IntNumber) & get)(
    at(_, includeTransactions = false)
  )

  private def at(height: Int, includeTransactions: Boolean): StandardRoute =
    (if (includeTransactions) {
       history.blockAt(height).map(_.json())
     } else {
       history.blockHeaderAndSizeAt(height).map { case ((bh, s)) =>
         BlockHeader.json(bh, s)
       }
     }) match {
      case Some(json) => complete(json + ("height" -> JsNumber(height)))
      case None =>
        complete(
          Json.obj("status" -> "error", "details" -> "No block for this height")
        )
    }

  @Path("/seq/{from}/{to}")
  @ApiOperation(
    value = "Seq",
    notes = "Get list of block of size MaxBlocksPerRequest at specified heights",
    httpMethod = "GET"
  )
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(
        name = "from",
        value = "Start block height",
        required = true,
        dataType = "integer",
        paramType = "path"
      ),
      new ApiImplicitParam(
        name = "to",
        value = "End block height",
        required = true,
        dataType = "integer",
        paramType = "path"
      )
    )
  )
  def seq: Route = (path("seq" / IntNumber / IntNumber) & get) { (start, end) =>
    seq(start, end, includeTransactions = true)
  }

  @Path("/headers/seq/{from}/{to}")
  @ApiOperation(
    value = "Seq (Block header only)",
    notes = "Get list of block of size MaxBlocksPerRequest without transactions payload at specified heights",
    httpMethod = "GET"
  )
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(
        name = "from",
        value = "Start block height",
        required = true,
        dataType = "integer",
        paramType = "path"
      ),
      new ApiImplicitParam(
        name = "to",
        value = "End block height",
        required = true,
        dataType = "integer",
        paramType = "path"
      )
    )
  )
  def seqHeaderOnly: Route =
    (path("headers" / "seq" / IntNumber / IntNumber) & get) { (start, end) =>
      seq(start, end, includeTransactions = false)
    }

  private def seq(
    start: Int,
    end: Int,
    includeTransactions: Boolean
  ): StandardRoute =
    if (end >= 0 && start >= 0 && end - start >= 0 && end - start < MaxBlocksPerRequest) {
      val blocks = JsArray((start to end).flatMap { height =>
        (if (includeTransactions) {
           history.blockAt(height).map(_.json())
         } else {
           history.blockHeaderAndSizeAt(height).map { case ((bh, s)) =>
             BlockHeader.json(bh, s)
           }
         }).map(_ + ("height" -> Json.toJson(height)))
      })
      complete(blocks)
    } else complete(TooBigArrayAllocation)

  @Path("/last")
  @ApiOperation(
    value = "Last",
    notes = "Get last block data",
    httpMethod = "GET"
  )
  def last: Route = (path("last") & get)(last(includeTransactions = true))

  @Path("/headers/last")
  @ApiOperation(
    value = "Last",
    notes = "Get last block data without transactions payload",
    httpMethod = "GET"
  )
  def lastHeaderOnly: Route =
    (path("headers" / "last") & get)(last(includeTransactions = false))

  def last(includeTransactions: Boolean): StandardRoute =
    complete(Future {
      history.read { _ =>
        val height = history.height()

        (if (includeTransactions) {
           history.blockAt(height).get.json()
         } else {
           val bhs = history.blockHeaderAndSizeAt(height).get
           BlockHeader.json(bhs._1, bhs._2)
         }) + ("height" -> Json.toJson(height))
      }
    })

  @Path("/genesis")
  @ApiOperation(
    value = "First",
    notes = "Get genesis block data",
    httpMethod = "GET"
  )
  def genesis: Route = (path("genesis") & get) {
    complete(history.genesis.json() + ("height" -> Json.toJson(1)))
  }

  @Path("/signature/{signature}")
  @ApiOperation(
    value = "Signature",
    notes = "Get block by a specified Base58-encoded signature",
    httpMethod = "GET"
  )
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(
        name = "signature",
        value = "Base58-encoded signature",
        required = true,
        dataType = "string",
        paramType = "path"
      )
    )
  )
  def signature: Route = (path("signature" / Segment) & get) { encodedSignature =>
    if (encodedSignature.length > TransactionParser.SignatureStringLength)
      complete(InvalidSignature)
    else {
      ByteStr
        .decodeBase58(encodedSignature)
        .toOption
        .toRight(InvalidSignature)
        .flatMap(s => history.blockById(s).toRight(BlockNotExists)) match {
        case Right(block) =>
          complete(
            block.json() + ("height" -> history
              .heightOf(block.uniqueId)
              .map(Json.toJson(_))
              .getOrElse(JsNull))
          )
        case Left(e) => complete(e)
      }
    }
  }

  @Path("/checkpoint")
  @ApiOperation(
    value = "Checkpoint",
    notes = "Broadcast checkpoint of blocks",
    httpMethod = "POST"
  )
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(
        name = "message",
        value = "Checkpoint message",
        required = true,
        paramType = "body",
        dataType = "io.lunes.network.Checkpoint"
      )
    )
  )
  @ApiResponses(
    Array(
      new ApiResponse(code = 200, message = "Json with response or error")
    )
  )
  def checkpoint: Route = (path("checkpoint") & post) {
    json[Checkpoint] { checkpoint =>
      checkpointProc(checkpoint)
        .runAsync(rollbackExecutor)
        .map {
          _.map(score =>
            allChannels.broadcast(
              LocalScoreChanged(score.getOrElse(history.score()))
            )
          )
        }
        .map(
          _.fold(
            ApiError.fromValidationError,
            _ => Json.obj("" -> "")
          ): ToResponseMarshallable
        )
    }
  }
}
