package io.lunes.features

/**
  *
  */
sealed trait BlockchainFeatureStatus

/**
  *
  */
object BlockchainFeatureStatus{

  /**
    *
    */
  case object Undefined extends BlockchainFeatureStatus

  /**
    *
    */
  case object Approved extends BlockchainFeatureStatus

  /**
    *
    */
  case object Activated extends BlockchainFeatureStatus

  /**
    *
    * @param status
    * @return
    */
  def promote(status: BlockchainFeatureStatus): BlockchainFeatureStatus = {
    status match {
      case Undefined => Approved
      case Approved => Activated
      case Activated => Activated
    }
  }
}
