package io.lunes.settings

import scorex.utils.ScorexLogging

/** System constants here.
  */
object Constants extends ScorexLogging {
  val VersionTuple = (0, 1, 5)
  val VersionStr =
    VersionTuple.toString.replace(",", ".").replace("(", "").replace(")", "")
  val MinimumCompatibilityVersion = (0, 0, 1)
  val ApplicationName = "lunesnode"
  val CoinName = "LUNES"
  val CoinAbr = "LNS"
  val AgentName = s"${ApplicationName} v${VersionStr}"
  val InitialBalance = 15072853761500800L

  val MainSchemeCharacter = '1'
  val MainDelay = 60
  val MainTimestamp = 1528077600000L
  val MainSignature =
    "soKTPcsb5cD97jnm64zF3pVuVUqUYx3caaDvuPyM6PXPY7eWCxeHeYvKSE2aJwZwRpXdRFdW1g5BQMFpYkHcf85"

  val MainTransactions = List(
    GenesisTransactionSettings(
      "37oqFHsx1cRtLWtnp6YyQpud5WJk4v79VPu",
      5072853761500800L
    ),
    GenesisTransactionSettings(
      "3826zwbgHauHXAoppU4we3hsJc9GtRCpSvz",
      10000000000000000L
    )
  )

  val TestSchemeCharacter = '0'
  val TestDelay = 60
  val TestTimestamp = 1528300800000L
  val TestSignature = ""

  val TestTransactions = List(
    GenesisTransactionSettings(
      "37UHoHBydMkaQaZThXLVQce6NLsqXHEhnBF",
      40000000000000000L
    ),
    GenesisTransactionSettings(
      "37NC89DsnG57nimtL9gS5Tbih1quMVDJswV",
      40000000000000000L
    ),
    GenesisTransactionSettings(
      "37Ubk12FdcdhjJyuZE4FMPBjKMRbXVqwevR",
      40000000000000000L
    ),
    GenesisTransactionSettings(
      "37M3jShfU8kpqTPu8UPkviM1UTehQZ79U7B",
      40000000000000000L
    )
  )

  val MinimalStakeForIssueOrReissue: Long = 2000000000000L
}
