package io.lunes.settings

import java.io.File

import io.lunes.state2.ByteStr

/**
	*
	* @param file
	* @param password
	* @param seed
	*/
case class WalletSettings(file: Option[File], password: String, seed: Option[ByteStr])
