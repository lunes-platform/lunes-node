package io.lunes.core.storage.db

/**
  *
  */
trait VersionedStorage {
  this: PropertiesStorage =>

  private val VersionProperty = "version"

  protected val Version: Int

  /**
    *
    * @return
    */
  def isVersionValid: Boolean = getIntProperty(VersionProperty) match {
    case None =>
      putIntProperty(VersionProperty, Version, None)
      true
    case Some(v) => v == Version
  }
}
