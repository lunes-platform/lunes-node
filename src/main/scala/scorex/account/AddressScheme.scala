package scorex.account

import io.lunes.settings.Constants

abstract class AddressScheme {
  val chainId: Byte
}

object AddressScheme {
  @volatile var current : AddressScheme = DefaultAddressScheme
}

object DefaultAddressScheme extends AddressScheme {
  val chainId: Byte = Constants.TestSchemeCharacter.toByte
}
