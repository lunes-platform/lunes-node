package io.lunes.core.storage.db

import java.nio.charset.StandardCharsets

import com.google.common.primitives.Ints
import org.iq80.leveldb.WriteBatch

/**
  * Bind Trait for Porperties Storage.
  * It needs to be imported with [[io.lunes.db.Storage]]
  */
trait PropertiesStorage {
  this: Storage =>

  private val PropertiesPrefix: Array[Byte] = "prop".getBytes(StandardCharsets.UTF_8)

  /**
    * Inserts [[scala.Int]] Property.
    * @param property Property name.
    * @param value Property value.
    * @param batch Option for a [[org.iq80.leveldb.WriteBatch]].
    */
  def putIntProperty(property: String, value: Int, batch: Option[WriteBatch]): Unit =
    put(makeKey(PropertiesPrefix, property), Ints.toByteArray(value), batch)

  /**
    * Retrieves an Integer Property.
    * @param property Property name.
    * @return Returns an Option of [[scala.Int]] as the value.
    */
  def getIntProperty(property: String): Option[Int] = get(makeKey(PropertiesPrefix, property)).map(Ints.fromByteArray)

  /**
    * Retrieves a generic Array of Byte.
    * @param property Property name.
    * @return Returns an Option of Array of Byte.
    */
  def getProperty(property: String): Option[Array[Byte]] = get(makeKey(PropertiesPrefix, property))

  /**
    *  Insert a generic Array of Byte property.
    * @param property Property name.
    * @param value Property value.
    * @param batch Option for a [[org.iq80.leveldb.WriteBatch]].
    */
  def putProperty(property: String, value: Array[Byte], batch: Option[WriteBatch]): Unit =
    put(makeKey(PropertiesPrefix, property), value, batch)
}

