package io.lunes.security

object SecurityChecker {

  var frozen: FrozenAssetList = FrozenAssetList(List.empty)

  var frozenAssetName: List[String] = List.empty

  val bannedAddress: BanAddressList = BanAddressList(
    List(
      BanAddress(""),
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
