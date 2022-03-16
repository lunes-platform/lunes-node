package io.lunes.settings

import scorex.utils.ScorexLogging

/** System constants here.
  */
object Constants extends ScorexLogging {
  val VersionTuple = (0, 1, 0)
  val VersionStr =
    VersionTuple.toString.replace(",", ".").replace("(", "").replace(")", "")
  val MinimumCompatibilityVersion = List(0, 0, 1)
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
  val TestSignature =
    "JfDSLhi8qd2bi8xoC3AqV7mjXdUSZXHUU5KbYQT9AuXDEJSqVuZJ4vS4nfRbJ64tZQeeXdbJJniTNChWQv8YPfh"

  val TestTransactions = List(
    GenesisTransactionSettings(
      "37buuGA1KBFiaMmgtZojvkaRwifCdZbUNuq",
      5000000000000L
    ),
    GenesisTransactionSettings(
      "37VVpvgHjnNY9hfggyAbfMzvaD6Ewo7Cfcj",
      5000000000000L
    ),
    GenesisTransactionSettings(
      "37gKHykxhd8n5MhzjsETtVi9Pp12ximAkY7",
      40000000000000000L
    ),
    GenesisTransactionSettings(
      "37d1vG9NPjnkpyFErNdnyKXLoRzoEYGyxYu",
      40000000000000000L
    )
  )

  val MinimalStakeForIssueOrReissue: Long = 2000000000000L
}
