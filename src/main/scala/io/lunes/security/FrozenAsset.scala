package io.lunes.security

import io.lunes.state2.ByteStr
import io.lunes.transaction.AssetId

case class FrozenAsset(account: BanAddress, asset: AssetId) {

  def checksWith(accountId: BanAddress, assetId: AssetId): Boolean =
    account.checksWith(accountId.account) && assetId == asset

  def checksWith(accountId: String, assetId: AssetId): Boolean =
    account.checksWith(accountId) && assetId == asset

  def checksWith(accountId: String, assetId: String): Boolean =
    account.checksWith(accountId) && asset == convertToAssetId(assetId)

  private def convertToAssetId(input: String): AssetId =
    ByteStr(input.toCharArray.map(_.toByte))
}
