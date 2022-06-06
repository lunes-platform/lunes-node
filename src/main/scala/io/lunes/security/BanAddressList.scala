package io.lunes.security

case class BanAddressList(list: List[BanAddress]) {

  def banned(input: String): Boolean = list.exists(_.checksWith(input))
}
