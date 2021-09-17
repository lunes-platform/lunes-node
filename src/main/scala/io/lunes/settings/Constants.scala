package io.lunes.settings

import scorex.utils.ScorexLogging

/**
  * System constants here.
  */
object Constants extends ScorexLogging {
  val VersionStr = "0.1.4"
  val VersionTuple = (0, 1, 4)
  val MinimumCompatibilityVersion = (0, 0, 8)
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
    ),
  )

  val TestSchemeCharacter = '0'
  val TestDelay = 60
  val TestTimestamp = 1528300800000L
  val TestSignature = "2FdB2ivVHxi3jTvX1F4WXZKXwoUDR8YYVDhn2QepDxd3qtrhfpDeCDLSG4kt8PAWrCCdFLWVhKcJKKnEzRN8aSu4"

  val TestTransactions = List(
    GenesisTransactionSettings("37gWwaFtsWjuwVWMvGBM12K2KWEDTazdHXL", 500000000000L),
    GenesisTransactionSettings("37N2DrXMj9fD5biLgreMgG1gT7fzjxzZNhB", 500000000000L),
    GenesisTransactionSettings("37axynQ4hiY1GgRireUbkQ3vpLYCsVtQYwh", 20000000000000000L),
    GenesisTransactionSettings("37bpECMv85nUr14YEfkyyyWKN2gYgrCfDhX", 40000000000000000L),
  )

  val MinimalStakeForIssueOrReissue: Long = 2000000000000L
}
