package io.lunes.security

import io.lunes.state2.ByteStr

case class BanAddress(account: String) {

  def toByteStr: ByteStr = ByteStr(account.toCharArray.map(_.toByte))

  def checksWith(input: String): Boolean = input == account
}
