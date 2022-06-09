package io.lunes.security

object SecurityChecker {

  var frozen: FrozenAssetList = FrozenAssetList(List.empty)

  var frozenAssetName: List[String] = List.empty

  val bannedAddress: BanAddressList = BanAddressList(
    List(
      // BanAddress("37ms8U8BDPrC24DoUrivA8Lxuu1J1gWNb79"),
      // BanAddress("37uDxz6BQX88fPCCEBwhY4GoCW6YWwZsAQS"),
      // BanAddress("37w7WprthdjVQ3vc9k6sfXX3DKWU5Xt6FFS"),
      // BanAddress("387LjpQ5fdBdcY4nRcfDU7gPYdesbc1Md4D"),
      // BanAddress("387V6kA1mG2HMmepbjcFr5eF1QMQiTXvDGq"),
      // BanAddress("3887fR55i8i4xwk4jXSGJ5DkoyuEdrbyAZx"),
      BanAddress("37QDCbeJkYJ9oBxqneiAfarH168dJaMasFW"),
      BanAddress("37N1YLXmLxKiSWxqjuxrBpzt3vCYuo6e3gS"),
      BanAddress("37TPtNeUdc9FuEayWUnudHH43c5cCeECnua"),
    )
  )

  val essentialAssetName: List[String] = List("LUNES")
  addFrozenAssetName(essentialAssetName.head)

  def checkAddress(input: String): Boolean = bannedAddress.banned(input)

  def addFrozenAssetName(input: String): Unit =
    if (!frozenAssetName.exists(_ == input))
      frozenAssetName = frozenAssetName ++ List(input)

  def checkFrozenAsset(account: String, assetId: String): Boolean =
    frozen.checksWith(account, assetId)

}
