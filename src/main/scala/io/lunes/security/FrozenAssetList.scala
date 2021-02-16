package io.lunes.security

import io.lunes.transaction.AssetId

class FrozenAssetList(list: List[FrozenAsset]) {

  def frozenAssetsOf(input: String): List[AssetId] =
    list.filter(_.account.checksWith(input)).collect {
      case a: FrozenAsset => a.asset
    }

  def unfreeze(account: String): FrozenAssetList =
    FrozenAssetList(list.filter(!_.account.checksWith(account)))

  def unfreeze(account: BanAddress, assetId: AssetId): FrozenAssetList =
    FrozenAssetList(list.filter(!_.checksWith(account, assetId)))

  def unfreeze(account: String, assetId: AssetId): FrozenAssetList =
    FrozenAssetList(list.filter(!_.checksWith(account, assetId)))

  def checksWith(accountId: String, assetId: String): Boolean =
    list.exists(_.checksWith(accountId, assetId))
}

object FrozenAssetList {

  def apply(input: List[FrozenAsset]) = new FrozenAssetList(input)

  def allAccountsForAsset(accounts: List[String], asset: AssetId) =
    new FrozenAssetList(
      accounts.map(acc => FrozenAsset(BanAddress(acc), asset))
    )
}
