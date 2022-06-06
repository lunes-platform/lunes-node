package io.lunes.network

import org.scalatest.funsuite.AnyFunSuite

class HandshakeHandlerSpec extends AnyFunSuite {

  test("Version (2, 0, 0) should be NOT compatible") {
    assert(
      false == HandshakeHandler.versionIsSupported((2, 0, 0))
    )
  }
  test("Version (1, 21, 1) should be NOT compatible") {
    assert(
      false == HandshakeHandler.versionIsSupported((1, 21, 1))
    )
  }
  test("Version (0, 1, 2) should be compatible") {
    assert(
      true == HandshakeHandler.versionIsSupported((0, 1, 2))
    )
  }
  test("Version (0, 1, 0) should be compatible") {
    assert(
      true == HandshakeHandler.versionIsSupported((0, 1, 0))
    )
  }

  test("Version between (0, 0, 13) should be compatible") {
    assert(
      true == HandshakeHandler.versionIsSupported((0, 0, 13))
    )
  }
  test("Version between (0, 0, 9) should be compatible") {
    assert(
      true == HandshakeHandler.versionIsSupported((0, 0, 9))
    )
  }
  test("Version between (0, 0, 1) should be compatible") {
    assert(
      true == HandshakeHandler.versionIsSupported((0, 0, 1))
    )
  }

//  test("Invoking head on an empty Set should produce NoSuchElementException") {
//    assertThrows[NoSuchElementException] {
//      Set.empty.head
//    }
//  }
}
