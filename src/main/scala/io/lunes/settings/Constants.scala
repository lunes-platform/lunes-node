package io.lunes.settings

import scorex.utils.ScorexLogging

/**
  * System constants here.
  */
object Constants extends ScorexLogging {
  val VersionStr = "0.0.3"
  val VersionTuple = (0, 0, 3)
  val ApplicationName = "lunesnode"
  val CoinName = "LUNES"
  val CoinAbr = "LNS"
  val AgentName = s"Lunes v${VersionStr}"
  val UnitsInLunes = 100000000L
  val TotalLunes = 600000000L
  val InitialBalance = TotalLunes * UnitsInLunes

  val MainSchemeCharacter = '0'
  val MainDelay = 60
  val MainTimestamp = 1522368000000L
  val MainSignature = "3YCC7s9kaXK5fEHjUtSNrVpbtzkU4YJqSUUsMCw5eo6ZS7TmqS5zcgyRSFuNS4hdiQFteE9tDTDafXPxDqsFQKwJ"

  val MainTransactions = List(
    GenesisTransactionSettings("37cjwk5WavHeoCjNUi92vba6KsNAd1uwAso", 30000000000000000L),
    GenesisTransactionSettings("37SGwubdwB1T8ri2Wh6XwcfkzhisqoLH45g", 30000000000000000L)
  )

  val TestSchemeCharacter = '1'
  val TestDelay = 60
  val TestTimestamp = 1522368000000L
  val TestSignature = "TwtrT2Q7zNNTsGSdVjZkpb3YzGBExqCuhUY4HFrFQZrQ5ZGNPKRn25QGaywgBfxVvUn132C5w5GoNf8SBA1bGsk"
  val TestInitialBalance = TotalLunes * UnitsInLunes
  val TestTransactions = List(
    GenesisTransactionSettings("3825YjBosdU7g2AWZjZNF5hN7VsRZg35RcA", 30000000000000000L),
    GenesisTransactionSettings("37qcYthwDtBv1g9AbiWsG6o2nLE8nXxF2vr", 30000000000000000L)
  )
}
