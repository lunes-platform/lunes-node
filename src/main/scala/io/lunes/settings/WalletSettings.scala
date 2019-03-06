package io.lunes.settings

import java.io.File

import io.lunes.state.ByteStr

case class WalletSettings(file: Option[File],
                          password: String,
                          seed: Option[ByteStr])
