package io.lunes.lang

import io.lunes.lang.v1.BaseGlobal

package object hacks {
  private[lang] val Global: BaseGlobal = io.lunes.lang.Global // Hack for IDEA
}
