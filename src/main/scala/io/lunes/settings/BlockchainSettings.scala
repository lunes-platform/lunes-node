package io.lunes.settings

import com.typesafe.config.Config
import io.lunes.state.ByteStr
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.ceedubs.ficus.readers.EnumerationReader._
import scala.concurrent.duration._

/**
  *
  * @param featureCheckBlocksPeriod
  * @param blocksForFeatureActivation
  * @param allowTemporaryNegativeUntil
  * @param requireSortedTransactionsAfter
  * @param generationBalanceDepthFrom50To1000AfterHeight
  * @param minimalGeneratingBalanceAfter
  * @param allowTransactionsFromFutureUntil
  * @param allowUnissuedAssetsUntil
  * @param allowInvalidReissueInSameBlockUntilTimestamp
  * @param allowMultipleLeaseCancelTransactionUntilTimestamp
  * @param resetEffectiveBalancesAtHeight
  * @param blockVersion3AfterHeight
  * @param preActivatedFeatures
  * @param doubleFeaturesPeriodsAfterHeight
  */
case class FunctionalitySettings(
    featureCheckBlocksPeriod: Int,
    blocksForFeatureActivation: Int,
    allowTemporaryNegativeUntil: Long,
    requireSortedTransactionsAfter: Long,
    generationBalanceDepthFrom50To1000AfterHeight: Int,
    minimalGeneratingBalanceAfter: Long,
    allowTransactionsFromFutureUntil: Long,
    allowUnissuedAssetsUntil: Long,
    allowInvalidReissueInSameBlockUntilTimestamp: Long,
    allowMultipleLeaseCancelTransactionUntilTimestamp: Long,
    resetEffectiveBalancesAtHeight: Int,
    blockVersion3AfterHeight: Int,
    preActivatedFeatures: Map[Short, Int],
    doubleFeaturesPeriodsAfterHeight: Int) {
  val dontRequireSortedTransactionsAfter = blockVersion3AfterHeight
  val allowLeasedBalanceTransferUntilHeight = blockVersion3AfterHeight

  require(featureCheckBlocksPeriod > 0,
          "featureCheckBlocksPeriod must be greater than 0")
  require(
    (blocksForFeatureActivation > 0) && (blocksForFeatureActivation <= featureCheckBlocksPeriod),
    s"blocksForFeatureActivation must be in range 1 to $featureCheckBlocksPeriod"
  )

  def activationWindowSize(height: Int): Int =
    featureCheckBlocksPeriod * (if (height <= doubleFeaturesPeriodsAfterHeight)
                                  1
                                else 2)

  def activationWindow(height: Int): Range =
    if (height < 1) Range(0, 0)
    else {
      val ws = activationWindowSize(height)
      Range.inclusive((height - 1) / ws * ws + 1, ((height - 1) / ws + 1) * ws)
    }

  def blocksForFeatureActivation(height: Int): Int =
    blocksForFeatureActivation * (if (height <= doubleFeaturesPeriodsAfterHeight)
                                    1
                                  else 2)

  def generatingBalanceDepth(height: Int): Int =
    if (height >= generationBalanceDepthFrom50To1000AfterHeight) 1000 else 50
}

/**
  *
  */
object FunctionalitySettings {
  val MAINNET = apply(
    featureCheckBlocksPeriod = 5000,
    blocksForFeatureActivation = 4000,
    allowTemporaryNegativeUntil = Constants.MainTimestamp,
    requireSortedTransactionsAfter = Constants.MainTimestamp,
    generationBalanceDepthFrom50To1000AfterHeight = 0,
    minimalGeneratingBalanceAfter = 0,
    allowTransactionsFromFutureUntil = 0,
    allowUnissuedAssetsUntil = 0,
    allowInvalidReissueInSameBlockUntilTimestamp = 0,
    allowMultipleLeaseCancelTransactionUntilTimestamp = 0,
    resetEffectiveBalancesAtHeight = 1,
    blockVersion3AfterHeight = 0,
    preActivatedFeatures = Map.empty,
    doubleFeaturesPeriodsAfterHeight = 810000
  )

  val TESTNET = apply(
    featureCheckBlocksPeriod = 3000,
    blocksForFeatureActivation = 2700,
    allowTemporaryNegativeUntil = Constants.TestTimestamp,
    requireSortedTransactionsAfter = Constants.TestTimestamp,
    generationBalanceDepthFrom50To1000AfterHeight = 0,
    minimalGeneratingBalanceAfter = 0,
    allowTransactionsFromFutureUntil = 0,
    allowUnissuedAssetsUntil = 0,
    allowInvalidReissueInSameBlockUntilTimestamp = 0,
    allowMultipleLeaseCancelTransactionUntilTimestamp = 0,
    resetEffectiveBalancesAtHeight = 1,
    blockVersion3AfterHeight = 0,
    preActivatedFeatures = Map.empty,
    doubleFeaturesPeriodsAfterHeight = Int.MaxValue
  )

  val DEVNET = apply(
    featureCheckBlocksPeriod = 4,
    blocksForFeatureActivation = 3,
    allowTemporaryNegativeUntil = Constants.DevTimestamp,
    requireSortedTransactionsAfter = Constants.DevTimestamp,
    generationBalanceDepthFrom50To1000AfterHeight = 0,
    minimalGeneratingBalanceAfter = 0,
    allowTransactionsFromFutureUntil = 0,
    allowUnissuedAssetsUntil = 0,
    allowInvalidReissueInSameBlockUntilTimestamp = 0,
    allowMultipleLeaseCancelTransactionUntilTimestamp = 0,
    resetEffectiveBalancesAtHeight = 1,
    blockVersion3AfterHeight = 0,
    preActivatedFeatures = Map.empty,
    doubleFeaturesPeriodsAfterHeight = Int.MaxValue
  )

  val configPath = "lunes.blockchain.custom.functionality"
}

/**
  *
  * @param recipient
  * @param amount
  */
case class GenesisTransactionSettings(recipient: String, amount: Long)

/**
  *
  * @param blockTimestamp
  * @param timestamp
  * @param initialBalance
  * @param signature
  * @param transactions
  * @param initialBaseTarget
  * @param averageBlockDelay
  */
case class GenesisSettings(blockTimestamp: Long,
                           timestamp: Long,
                           initialBalance: Long,
                           signature: Option[ByteStr],
                           transactions: Seq[GenesisTransactionSettings],
                           initialBaseTarget: Long,
                           averageBlockDelay: FiniteDuration)

/**
  *
  */
object GenesisSettings {
  val MAINNET = GenesisSettings(
    Constants.MainTimestamp,
    Constants.MainTimestamp,
    Constants.InitialBalance,
    ByteStr.decodeBase58(Constants.MainSignature).toOption,
    Constants.MainTransactions,
    153722867L,
    Constants.MainDelay.seconds
  )

  val TESTNET = GenesisSettings(
    Constants.TestTimestamp,
    Constants.TestTimestamp,
    Constants.InitialBalance,
    ByteStr.decodeBase58(Constants.TestSignature).toOption,
    Constants.TestTransactions,
    153722867L,
    Constants.TestDelay.seconds
  )

  val DEVNET = GenesisSettings(
    Constants.DevTimestamp,
    Constants.DevTimestamp,
    Constants.InitialBalance,
    ByteStr.decodeBase58(Constants.DevSignature).toOption,
    Constants.DevTransactions,
    153722867L,
    Constants.DevDelay.seconds
  )
}

/**
  *
  * @param addressSchemeCharacter
  * @param maxTransactionsPerBlockDiff
  * @param minBlocksInMemory
  * @param functionalitySettings
  * @param genesisSettings
  */
case class BlockchainSettings(addressSchemeCharacter: Char,
                              maxTransactionsPerBlockDiff: Int,
                              minBlocksInMemory: Int,
                              functionalitySettings: FunctionalitySettings,
                              genesisSettings: GenesisSettings)

/**
  *
  */
object BlockchainType extends Enumeration {
  val MAINNET = Value("MAINNET")
  val TESTNET = Value("TESTNET")
  val DEVNET = Value("DEVNET")
}

/**
  *
  */
object BlockchainSettings {
  val configPath: String = "lunes.blockchain"

  /**
    *
    * @param config
    * @return
    */
  def fromConfig(config: Config): BlockchainSettings = {
    val blockchainType = config.as[BlockchainType.Value](s"$configPath.type")
    val (addressSchemeCharacter, functionalitySettings, genesisSettings) =
      blockchainType match {
        case BlockchainType.MAINNET =>
          (Constants.MainSchemeCharacter,
           FunctionalitySettings.MAINNET,
           GenesisSettings.MAINNET)
        case BlockchainType.TESTNET =>
          (Constants.TestSchemeCharacter,
           FunctionalitySettings.TESTNET,
           GenesisSettings.TESTNET)
        case BlockchainType.DEVNET =>
          (Constants.DevSchemeCharacter,
           FunctionalitySettings.DEVNET,
           GenesisSettings.DEVNET)
      }

    BlockchainSettings(
      addressSchemeCharacter = addressSchemeCharacter,
      maxTransactionsPerBlockDiff =
        config.as[Int](s"$configPath.max-transactions-per-block-diff"),
      minBlocksInMemory = config.as[Int](s"$configPath.min-blocks-in-memory"),
      functionalitySettings = functionalitySettings,
      genesisSettings = genesisSettings
    )
  }
}
