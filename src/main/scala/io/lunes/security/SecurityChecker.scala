package io.lunes.security

object SecurityChecker {

  var frozen: FrozenAssetList = FrozenAssetList(List.empty)

  var frozenAssetName: List[String] = List.empty

  val bannedAddress: BanAddressList = BanAddressList(
    List(
      BanAddress("37ms8U8BDPrC24DoUrivA8Lxuu1J1gWNb79"),
      BanAddress("37uDxz6BQX88fPCCEBwhY4GoCW6YWwZsAQS"),
      BanAddress("37ms8U8BDPrC24DoUrivA8Lxuu1J1gWNb79"),
      BanAddress("387LjpQ5fdBdcY4nRcfDU7gPYdesbc1Md4D"),
      BanAddress("37w7WprthdjVQ3vc9k6sfXX3DKWU5Xt6FFS"),
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
