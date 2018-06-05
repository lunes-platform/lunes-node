package io.lunes.settings

import scorex.utils.ScorexLogging

/**
  * System constants here.
  */
/**
  *
  */
object Constants extends ScorexLogging {
  val VersionStr = "0.0.5"
  val VersionTuple = (0, 0, 5)
  val MinimalVersion = (0, 0, 5)
  val ApplicationName = "lunesnode"
  val CoinName = "LUNES"
  val CoinAbr = "LNS"
  val AgentName = s"lunesnode"
  val InitialBalance = 15072853761500800L

  val MainSchemeCharacter = '1'
  val MainDelay = 60
  val MainTimestamp = 1528077600000L
  val MainSignature = "soKTPcsb5cD97jnm64zF3pVuVUqUYx3caaDvuPyM6PXPY7eWCxeHeYvKSE2aJwZwRpXdRFdW1g5BQMFpYkHcf85"

  val MainTransactions = List(
    GenesisTransactionSettings("37oqFHsx1cRtLWtnp6YyQpud5WJk4v79VPu", 5072853761500800L),
    GenesisTransactionSettings("3826zwbgHauHXAoppU4we3hsJc9GtRCpSvz", 10000000000000000L),
  )

  val TestSchemeCharacter = '0'
  val TestDelay = 60
  val TestTimestamp = 1523145600000L
  val TestSignature = ""

  val TestTransactions = List(
    GenesisTransactionSettings("3825YjBosdU7g2AWZjZNF5hN7VsRZg35RcA", 30000000000000000L),
    GenesisTransactionSettings("37qcYthwDtBv1g9AbiWsG6o2nLE8nXxF2vr", 30000000000000000L)
  )
}
