package io.lunes.lang

trait Versioned {
  type V <: ScriptVersion
  val version: V
}
