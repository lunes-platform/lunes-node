package scorex.api.http.assets

import akka.http.scaladsl.server.Route
import io.lunes.settings.RestAPISettings
import io.lunes.state2.{ByteStr, StateReader}
import io.lunes.transaction.assets.exchange.Order
import io.lunes.transaction.assets.exchange.OrderJson._
import io.lunes.transaction.{AssetIdStringLength, TransactionFactory}
import io.lunes.utx.UtxPool
import io.netty.channel.group.ChannelGroup
import io.swagger.annotations._
import play.api.libs.json._
import scorex.BroadcastRoute
import scorex.account.Address
import scorex.api.http.AddressApiRoute.Balance
import scorex.api.http.{ApiError, ApiRoute, InvalidAddress}
import scorex.crypto.encode.Base58
import scorex.utils.Time
import scorex.wallet.Wallet

import javax.ws.rs.Path
import scala.util.{Failure, Success}

@Path("/assets")
@Api(value = "assets")
case class AssetsApiRoute(
    settings: RestAPISettings,
    wallet: Wallet,
    utx: UtxPool,
    allChannels: ChannelGroup,
    state: StateReader,
    time: Time
) extends ApiRoute
    with BroadcastRoute {
  val MaxAddressesPerRequest = 1000

  override lazy val route =
    pathPrefix("assets") {
      balance ~ balances ~ issue ~ reissue ~ registry ~ burnRoute ~ transfer ~ massTransfer ~ signOrder ~ balanceDistribution
    }

  @Path("/balance/{address}/{assetId}")
  @ApiOperation(
    value = "Asset's balance",
    notes = "Account's balance by given asset",
    httpMethod = "GET"
  )
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(
        name = "address",
        value = "Address",
        required = true,
        dataType = "string",
        paramType = "path"
      ),
      new ApiImplicitParam(
        name = "assetId",
        value = "Asset ID",
        required = true,
        dataType = "string",
        paramType = "path"
      )
    )
  )
  def balance: Route =
    (get & path("balance" / Segment / Segment)) { (address, assetId) =>
      complete(balanceJson(address, assetId))
    }

  @Path("/{assetId}/distribution")
  @ApiOperation(
    value = "Asset balance distribution",
    notes = "Asset balance distribution by account",
    httpMethod = "GET"
  )
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(
        name = "assetId",
        value = "Asset ID",
        required = true,
        dataType = "string",
        paramType = "path"
      )
    )
  )
  def balanceDistribution: Route =
    (get & path(Segment / "distribution")) { assetId =>
      complete {
        Success(assetId)
          .filter(_.length <= AssetIdStringLength)
          .flatMap(Base58.decode) match {
          case Success(byteArray) =>
            Json.toJson(state().assetDistribution(byteArray))
          case Failure(_) =>
            ApiError.fromValidationError(
              io.lunes.transaction.ValidationError
                .GenericError("Must be base58-encoded assetId")
            )
        }
      }
    }
  @Path("/balance/{address}")
  @ApiOperation(
    value = "Account's balance",
    notes = "Account's balances for all assets",
    httpMethod = "GET"
  )
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(
        name = "address",
        value = "Address",
        required = true,
        dataType = "string",
        paramType = "path"
      )
    )
  )
  def balances: Route =
    (get & path("balance" / Segment)) { address =>
      complete(fullAccountAssetsInfo(address))
    }

  @Path("/registry")
  @ApiOperation(
    value = "Registry data",
    notes = "Registry data in blockchain",
    httpMethod = "POST",
    produces = "application/json",
    consumes = "application/json"
  )
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(
        name = "body",
        value = "Json with data",
        required = true,
        paramType = "body",
        dataType = "scorex.api.http.assets.RegistryRequest",
        defaultValue =
          "{\"sender\":\"3Mn6xomsZZepJj1GL1QaW6CaCJAq8B3oPef\",\"recipient\":\"3Mciuup51AxRrpSz7XhutnQYTkNT9691HAk\",\"assetId\":null,\"amount\":5813874260609385500,\"feeAssetId\":\"3Z7T9SwMbcBuZgcn3mGu7MMp619CTgSWBT7wvEkPwYXGnoYzLeTyh3EqZu1ibUhbUHAsGK5tdv9vJL9pk4fzv9Gc\",\"fee\":1579331567487095949,\"timestamp\":4231642878298810008}"
      )
    )
  )
  def registry: Route =
    processRequest(
      "registry",
      (t: RegistryRequest) =>
        doBroadcast(TransactionFactory.registryData(t, wallet, time))
    )
  @Path("/transfer")
  @ApiOperation(
    value = "Transfer asset",
    notes = "Transfer asset to new address",
    httpMethod = "POST",
    produces = "application/json",
    consumes = "application/json"
  )
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(
        name = "body",
        value = "Json with data",
        required = true,
        paramType = "body",
        dataType = "scorex.api.http.assets.TransferRequest",
        defaultValue =
          "{\"sender\":\"3Mn6xomsZZepJj1GL1QaW6CaCJAq8B3oPef\",\"recipient\":\"3Mciuup51AxRrpSz7XhutnQYTkNT9691HAk\",\"assetId\":null,\"amount\":5813874260609385500,\"feeAssetId\":\"3Z7T9SwMbcBuZgcn3mGu7MMp619CTgSWBT7wvEkPwYXGnoYzLeTyh3EqZu1ibUhbUHAsGK5tdv9vJL9pk4fzv9Gc\",\"fee\":1579331567487095949,\"timestamp\":4231642878298810008}"
      )
    )
  )
  def transfer: Route =
    processRequest(
      "transfer",
      (t: TransferRequest) =>
        doBroadcast(TransactionFactory.transferAsset(t, wallet, time))
    )

  @Path("/masstransfer")
  @ApiOperation(
    value = "Mass Transfer",
    notes = "Mass transfer of assets",
    httpMethod = "POST",
    produces = "application/json",
    consumes = "application/json"
  )
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(
        name = "body",
        value = "Json with data",
        required = true,
        paramType = "body",
        dataType = "scorex.api.http.assets.MassTransferRequest",
        defaultValue =
          "{\"sender\":\"3Mn6xomsZZepJj1GL1QaW6CaCJAq8B3oPef\",\"transfers\":(\"3Mciuup51AxRrpSz7XhutnQYTkNT9691HAk\",100000000),\"fee\":100000,\"timestamp\":1517315595291}"
      )
    )
  )
  def massTransfer: Route =
    processRequest(
      "masstransfer",
      (t: MassTransferRequest) =>
        doBroadcast(TransactionFactory.massTransferAsset(t, wallet, time))
    )

  @Path("/issue")
  @ApiOperation(
    value = "Issue Asset",
    notes = "Issue new Asset",
    httpMethod = "POST",
    produces = "application/json",
    consumes = "application/json"
  )
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(
        name = "body",
        value = "Json with data",
        required = true,
        paramType = "body",
        dataType = "scorex.api.http.assets.IssueRequest",
        defaultValue =
          "{\"sender\":\"string\",\"name\":\"str\",\"description\":\"string\",\"quantity\":100000,\"decimals\":7,\"reissuable\":false,\"fee\":100000000}"
      )
    )
  )
  def issue: Route =
    processRequest(
      "issue",
      (r: IssueRequest) =>
        doBroadcast(
          TransactionFactory.issueAsset(r, wallet, time, balance(r.sender))
        )
    )

  @Path("/reissue")
  @ApiOperation(
    value = "Issue Asset",
    notes = "Reissue Asset",
    httpMethod = "POST",
    produces = "application/json",
    consumes = "application/json"
  )
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(
        name = "body",
        value = "Json with data",
        required = true,
        paramType = "body",
        dataType = "scorex.api.http.assets.ReissueRequest",
        defaultValue =
          "{\"sender\":\"string\",\"assetId\":\"Base58\",\"quantity\":100000,\"reissuable\":false,\"fee\":1}"
      )
    )
  )
  def reissue: Route =
    processRequest(
      "reissue",
      (r: ReissueRequest) =>
        doBroadcast(
          TransactionFactory.reissueAsset(r, wallet, time, balance(r.sender))
        )
    )

  private def balance(address: String, confirmations: Int = 4) = {
    val s = state()
    s.read { _ =>
      Address
        .fromString(address)
        .right
        .map(acc =>
          Balance(
            acc.address,
            confirmations,
            s.effectiveBalanceAtHeightWithConfirmations(
              acc,
              s.height,
              confirmations
            ).get
          )
        )
        .getOrElse(InvalidAddress)
    } match {
      case Balance(_, _, balance) => Some(balance)
      case _                      => None
    }
  }

  @Path("/burn")
  @ApiOperation(
    value = "Burn Asset",
    notes = "Burn some of your assets",
    httpMethod = "POST",
    produces = "application/json",
    consumes = "application/json"
  )
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(
        name = "body",
        value = "Json with data",
        required = true,
        paramType = "body",
        dataType = "scorex.api.http.assets.BurnRequest",
        defaultValue =
          "{\"sender\":\"string\",\"assetId\":\"Base58\",\"quantity\":100,\"fee\":100000}"
      )
    )
  )
  def burnRoute: Route =
    processRequest(
      "burn",
      (b: BurnRequest) =>
        doBroadcast(TransactionFactory.burnAsset(b, wallet, time))
    )

  private def balanceJson(
      address: String,
      assetIdStr: String
  ): Either[ApiError, JsObject] = {
    ByteStr.decodeBase58(assetIdStr) match {
      case Success(assetId) =>
        (for {
          acc <- Address.fromString(address)
        } yield Json.obj(
          "address" -> acc.address,
          "assetId" -> assetIdStr,
          "balance" -> state().assetBalance(acc, assetId)
        )).left.map(ApiError.fromValidationError)
      case _ => Left(InvalidAddress)
    }
  }

  private def fullAccountAssetsInfo(
      address: String
  ): Either[ApiError, JsObject] =
    (for {
      acc <- Address.fromString(address)
    } yield {
      val balances: Seq[JsObject] = state()
        .getAccountBalance(acc)
        .map { case ((assetId, (balance, reissuable, quantity, issueTx))) =>
          JsObject(
            Seq(
              "assetId" -> JsString(assetId.base58),
              "balance" -> JsNumber(balance),
              "reissuable" -> JsBoolean(reissuable),
              "quantity" -> JsNumber(quantity),
              "issueTransaction" -> issueTx.json()
            )
          )
        }
        .toSeq
      Json.obj("address" -> acc.address, "balances" -> JsArray(balances))
    }).left.map(ApiError.fromValidationError)
  @Path("/order")
  @ApiOperation(
    value = "Sign Order",
    notes = "Create order signed by address from wallet",
    httpMethod = "POST",
    produces = "application/json",
    consumes = "application/json"
  )
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(
        name = "body",
        value = "Order Json with data",
        required = true,
        paramType = "body",
        dataType = "io.lunes.transaction.assets.exchange.Order"
      )
    )
  )
  def signOrder: Route =
    processRequest(
      "order",
      (order: Order) => {
        wallet
          .privateKeyAccount(order.senderPublicKey)
          .map(pk => Order.sign(order, pk))
      }
    )
}
