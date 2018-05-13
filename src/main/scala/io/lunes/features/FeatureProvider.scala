package io.lunes.features

import io.lunes.settings.FunctionalitySettings

/**
  *
  */
trait FeatureProvider {
  /**
    *
    * @param height
    * @return
    */
  protected def activationWindowSize(height: Int): Int

  /**
    *
    * @return
    */
  def approvedFeatures(): Map[Short, Int]

  /**
    *
    * @param height
    * @return
    */
  def featureVotesCountWithinActivationWindow(height: Int): Map[Short, Int]
}

/**
  *
  * @param functionalitySettings
  */
case class FeaturesProperties(functionalitySettings: FunctionalitySettings) {
  /**
    *
    * @param height
    * @return
    */
  def featureCheckBlocksPeriodAtHeight(height: Int): Int =
    doubleValueAtHeight(height, functionalitySettings.featureCheckBlocksPeriod)

  /**
    *
    * @param height
    * @return
    */
  def blocksForFeatureActivationAtHeight(height: Int): Int =
    doubleValueAtHeight(height, functionalitySettings.blocksForFeatureActivation)

  /**
    *
    * @param height
    * @param value
    * @return
    */
  private def doubleValueAtHeight(height: Int, value: Int): Int =
    if (height > functionalitySettings.doubleFeaturesPeriodsAfterHeight) value * 2 else value
}

/**
  *
  */
object FeatureProvider {

  /**
    *
    * @param provider
    */
  implicit class FeatureProviderExt(provider: FeatureProvider) {
    /**
      *
      * @param feature
      * @param height
      * @return
      */
    def isFeatureActivated(feature: BlockchainFeature, height: Int): Boolean = {
      provider.featureStatus(feature.id, height) == BlockchainFeatureStatus.Activated
    }

    /**
      *
      * @param feature
      * @param height
      * @return
      */
    def featureStatus(feature: Short, height: Int): BlockchainFeatureStatus = {
      featureApprovalHeight(feature).getOrElse(Int.MaxValue) match {
        case x if x <= height - provider.activationWindowSize(height) => BlockchainFeatureStatus.Activated
        case x if x <= height => BlockchainFeatureStatus.Approved
        case _ => BlockchainFeatureStatus.Undefined
      }
    }

    /**
      *
      * @param height
      * @return
      */
    def activatedFeatures(height: Int): Set[Short] = provider.approvedFeatures()
      .filter { case (_, acceptedHeight) => acceptedHeight <= height - provider.activationWindowSize(height) }.keySet

    /**
      *
      * @param feature
      * @return
      */
    def featureActivationHeight(feature: Short): Option[Int] = {
      featureApprovalHeight(feature).map(h => h + provider.activationWindowSize(h))
    }

    /**
      *
      * @param feature
      * @return
      */
    def featureApprovalHeight(feature: Short): Option[Int] = provider.approvedFeatures().get(feature)
  }

  /**
    *
    * @param height
    * @param activationWindowSize
    * @return
    */
  def votingWindowOpeningFromHeight(height: Int, activationWindowSize: Int): Int =
    ((height - 1) / activationWindowSize) * activationWindowSize + 1
}
