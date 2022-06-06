package io.lunes.core.storage.db

import java.nio.charset.{Charset, StandardCharsets}

import com.google.common.primitives.{Bytes, Ints}
import io.lunes.utils.forceStopApplication
import org.iq80.leveldb.{DB, DBIterator, WriteBatch}
import scorex.utils.ScorexLogging

import scala.collection.AbstractIterator
import scala.util.control.NonFatal

/**
  * Storage extension for [[scorex.utils.ScorexLogging]].
  * @param db LevelDB database object.
  */
abstract class Storage(private val db: DB) extends ScorexLogging {
  protected val Charset: Charset = StandardCharsets.UTF_8

  protected val Separator: Array[Byte] = Array[Byte](':')

  /**
    * Gets a generic value given a generic key.
    * @param key Inputs a Generic Array of Byte key.
    * @return Return a Generic Array of Byte value.
    */
  def get(key: Array[Byte]): Option[Array[Byte]] = {
    try {
      Option(db.get(key))
    } catch {
      case NonFatal(t) =>
        log.error("LevelDB get error", t)
        forceStopApplication()
        throw t
    }
  }

  /**
    * Creates a LevelDB WriteBatch operation.
    * @return Returns a Option for LevelDB WriteBatch.
    */
  def createBatch(): Option[WriteBatch] = {
    try {
      Some(db.createWriteBatch())
    } catch {
      case NonFatal(t) =>
        log.error("LevelDB create batch error", t)
        forceStopApplication()
        throw t
    }
  }

  /**
    * Inserts a Generic Value for a Generic Key.
    * @param key Inputs a Array of Byte key.
    * @param value Inputs a Array of Byte value.
    * @param batch Inputs a Option for a LevelDB WriteBatch.
    */
  def put(key: Array[Byte], value: Array[Byte], batch: Option[WriteBatch]): Unit = {
    try {
      if (batch.isDefined) batch.get.put(key, value) else db.put(key, value)
    } catch {
      case NonFatal(t) =>
        log.error("LevelDB batch put error", t)
        forceStopApplication()
        throw t
    }
  }

  /**
    * Erases a key.
    * @param key Informs a Array of Byte key.
    * @param batch Inputs a Option for a LevelDB WriteBatch.
    */
  def delete(key: Array[Byte], batch: Option[WriteBatch]): Unit = {
    try {
      if (batch.isDefined) batch.get.delete(key) else db.delete(key)
    } catch {
      case NonFatal(t) =>
        log.error("LevelDB delete error", t)
        forceStopApplication()
        throw t
    }
  }

  /**
    * Commits the memmory stored transactions in the Database.
    * @param batch Inputs a Option for a LevelDB WriteBatch.
    */
  def commit(batch: Option[WriteBatch]): Unit = {
    batch.foreach { b =>
      try {
        db.write(b)
      } catch {
        case NonFatal(t) =>
          log.error("LevelDB write batch error", t)
          forceStopApplication()
          throw t
      } finally {
        b.close()
      }
    }
  }

  /**
    * Inner Class for a Key Iterator.
    * @constructor Constructs a Key Iterator based on a DBIterator.
    * @param it Inputs the Database Iterator.
    */
  class KeysIterator(val it: DBIterator) extends AbstractIterator[Array[Byte]] {
    /**
      * Check for the next element for the Iterator.
       * @return Returns true if there is a next element.
      */
    override def hasNext: Boolean = it.hasNext

    /**
      * Gives the next element for the Iterator.
      * @return Returns an Array of Byte key for the next element.
      */
    override def next(): Array[Byte] = it.next().getKey

    /**
      * Closes the iterator.
      */
    def close(): Unit = it.close()
  }

  /**
    * Returns a [[KeysIterator]] for the first key on the Database.
    * @return Returns the [[KeysIterator]] object.
    */
  protected def allKeys: KeysIterator = {
    val it: DBIterator = db.iterator()
    it.seekToFirst()
    new KeysIterator(it)
  }

  /**
    * Interface for remove all elements on the Database.
    * @param b Inputs a Option for LevelDB WriteBatch.
    */
  def removeEverything(b: Option[WriteBatch]): Unit

  /**
    * Creates a Prefix.
    * @param prefix Inputs a generic Array of Byte prefix
    * @return Returns a concatenated Prefix.
    */
  protected def makePrefix(prefix: Array[Byte]): Array[Byte] = Bytes.concat(prefix, Separator)

  /** Generates a Generic Key for a Array of Byte key seed.
    * @param prefix Inputs a generic Array of Byte prefix.
    * @param key Inputs a generic Array of Byte key.
    * @return Returns a generic Array of Byte key.
    */
  protected def makeKey(prefix: Array[Byte], key: Array[Byte]): Array[Byte] = Bytes.concat(prefix, Separator, key, Separator)

  /** Generates a Generic Key for a String key seed.
    * @param prefix Inputs a generic Array of Byte prefix.
    * @param key Inputs a String key.
    * @return Returns a generic Array of Byte
    */
  protected def makeKey(prefix: Array[Byte], key: String): Array[Byte] = makeKey(prefix, key.getBytes(Charset))

  /** Generates a Generic Key for a Int key seed.
    * @param prefix Inputs a generic Array of Byte prefix.
    * @param key Inputs a Int key.
    * @return Returns a generic Array of Byte
    */
  protected def makeKey(prefix: Array[Byte], key: Int): Array[Byte] = makeKey(prefix, Ints.toByteArray(key))
}
