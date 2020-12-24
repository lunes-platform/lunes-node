package io.lunes.settings

import scala.concurrent.duration.FiniteDuration

/**
	*
	* @param maxSize
	* @param maxTransactionAge
	* @param blacklistSenderAddresses
	* @param allowBlacklistedTransferTo
	* @param cleanupInterval
	*/
case class UtxSettings(maxSize: Int,
                       maxTransactionAge: FiniteDuration,
                       blacklistSenderAddresses: Set[String],
                       allowBlacklistedTransferTo: Set[String],
                       cleanupInterval: FiniteDuration)
