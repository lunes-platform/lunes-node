package io.lunes.settings

import java.io.File

import io.lunes.state2.ByteStr

case class WalletSettings(file: Option[File], password: String, seed: Option[ByteStr])
