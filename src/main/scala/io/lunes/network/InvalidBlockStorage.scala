package io.lunes.network

import com.google.common.cache.CacheBuilder
import io.lunes.state2.ByteStr

import scala.concurrent.duration.FiniteDuration
import InvalidBlockStorageImpl._
import io.lunes.transaction.ValidationError
import io.lunes.transaction.ValidationError.GenericError

/**
  *
  */
trait InvalidBlockStorage {
  /**
    *
    * @param blockId
    * @param validationError
    */
  def add(blockId: ByteStr, validationError: ValidationError): Unit

  /**
    *
    * @param blockId
    * @return
    */
  def find(blockId: ByteStr): Option[ValidationError]
}

/**
  *
  */
class InMemoryInvalidBlockStorage extends InvalidBlockStorage {

  var s: Set[ByteStr] = Set.empty[ByteStr]

  /**
    *
    * @param blockId
    * @param validationError
    */
  override def add(blockId: ByteStr, validationError: ValidationError): Unit = s += blockId

  /**
    *
    * @param blockId
    * @return
    */
  override def find(blockId: ByteStr): Option[ValidationError] = {
    if (s.contains(blockId)) Some(GenericError("Unknown")) else None
  }

}

/**
  *
  * @param settings
  */
class InvalidBlockStorageImpl(settings: InvalidBlockStorageSettings) extends InvalidBlockStorage {
  private val cache = CacheBuilder
    .newBuilder()
    .expireAfterWrite(settings.timeout.length, settings.timeout.unit)
    .build[ByteStr, ValidationError]()

  /**
    *
    * @param blockId
    * @param validationError
    */
  override def add(blockId: ByteStr, validationError: ValidationError): Unit = cache.put(blockId, validationError)

  /**
    *
    * @param blockId
    * @return
    */
  override def find(blockId: ByteStr): Option[ValidationError] = Option(cache.getIfPresent(blockId))
}

/**
  *
  */
object InvalidBlockStorageImpl {

  /**
    *
    * @param maxSize
    * @param timeout
    */
  case class InvalidBlockStorageSettings(maxSize: Int,
                                         timeout: FiniteDuration)
}
