package io.lunes.db

import com.google.common.primitives.{Bytes, Ints}
import org.iq80.leveldb.{DB, WriteBatch}

/**
  * Subtorage extension for [[scorex.utils.ScorexLogging]]
  * @constructor Creates a SubStorage object.
  * @param db LevelDB database object.
  * @param name Substorage name.
  */
class SubStorage(db: DB, name: String) extends Storage(db) {

  private val subPrefix: Array[Byte] = name.getBytes(Charset)

  /**
    * Prefix generator.
    * @param prefix Generic Array of Byte prefix.
    * @return Concatenated prefix.
    */
  override protected def makePrefix(prefix: Array[Byte]): Array[Byte] = Bytes.concat(subPrefix, Separator, prefix, Separator)

  /**
    * Key Generator.
    * @param prefix Generic prefix.
    * @param key Generic key.
    * @return Concatenated key.
    */
  override protected def makeKey(prefix: Array[Byte], key: Array[Byte]): Array[Byte] = Bytes.concat(subPrefix, Separator, prefix, Separator, key)

  /**
    * Key Generator for a String Key.
    * @param prefix Generic prefix.
    * @param key String input for key.
    * @return Concatenated key.
    */
  override protected def makeKey(prefix: Array[Byte], key: String): Array[Byte] = makeKey(prefix, key.getBytes(Charset))

  /**
    * Key Generator for Integer Key.
    * @param prefix Generic prefix.
    * @param key Integer input for key.
    * @return Concatenated key.
    */
  override protected def makeKey(prefix: Array[Byte], key: Int): Array[Byte] = makeKey(prefix, Ints.toByteArray(key))

  /**
    * Erase the Substorage.
    * @param b Option for LevelDB WriteBatch.
    */
  override def removeEverything(b: Option[WriteBatch]): Unit = {
    val it = allKeys
    while (it.hasNext) {
      val key = it.next()
      if (key.startsWith(subPrefix)) delete(key, b)
    }
    it.close()
  }

}
