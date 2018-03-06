package io.lunes.settings

import scorex.utils.ScorexLogging

/**
  * System constants here.
  */
object Constants extends ScorexLogging {
  val VersionStr = "0.0.2"
  val VersionTuple = (0, 0, 2)
  val ApplicationName = "lunesnode"
  val CoinName = "LUNES"
  val CoinAbr = "LNS"
  val AgentName = s"Lunes v${VersionStr}"
  val UnitsInLunes = 100000000L
  val TotalLunes = 600000000L
  val InitialBalance = TotalLunes * UnitsInLunes

  val MainSchemeCharacter = '0'
  val MainDelay = 60
  val MainTimestamp = 1514764800000L
  val MainSignature = "4Zot64eFvdNRiCEmAuMFtmXy3c8vtLKHESVGMPZcWvKg2gjnhWjG61WEREFfFMUKLHcQveABCqPsGXQFe649d4BQ"

  val MainTransactions = List(
    GenesisTransactionSettings("37TYU7wMUhrYFWUHe1YLDpXfZ4jCu8h9XSY", 30000000000000000L),
    GenesisTransactionSettings("37YfN5XiJ4bZsj2mRLUh796cJCvf4EDEAfe", 30000000000000000L)
  )

  val TestSchemeCharacter = '1'
  val TestDelay = 60
  val TestTimestamp = 1514764810
  val TestSignature = "2yuzjasD2Wnx74AbPuKsEqHvtdpgcwQjYyrrmws88st5MmLaKRQnQ1PytWHwXzF4tkvuuaZSZhXzQzUJeUQYfjPj"
  val TestInitialBalance = TotalLunes * UnitsInLunes
  val TestTransactions = List(
    GenesisTransactionSettings("37vD5P3TDyRwSKCvAABrbNnaKcMUX4T94Qd", 30000000000000000L),
    GenesisTransactionSettings("386t8is7jbnBWUbVFJousva1ViLjq1Jq2jR", 30000000000000000L)
  )
}
