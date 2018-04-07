package io.lunes.transaction

import scorex.account.PublicKeyAccount

trait Authorized {
  val sender: PublicKeyAccount
}
