package io.lunes.features

/**
  *
  * @param id
  */
case class BlockchainFeature private(id: Short)

/**
  *
  */
object BlockchainFeatures {

  val NG = BlockchainFeature(2)
  val MassTransfer = BlockchainFeature(3)
  val SmartAccounts = BlockchainFeature(4)

  val implemented: Set[Short] = Set(MassTransfer,NG).map(_.id)
}
