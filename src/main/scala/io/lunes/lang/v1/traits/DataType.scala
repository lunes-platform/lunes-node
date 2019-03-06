package io.lunes.lang.v1.traits

import io.lunes.lang.v1.compiler.Terms._

sealed abstract case class DataType(innerType: TYPE)
object DataType {
  object Boolean extends DataType(BOOLEAN)
  object Long extends DataType(LONG)
  object ByteArray extends DataType(BYTEVECTOR)
  object String extends DataType(STRING)
}
