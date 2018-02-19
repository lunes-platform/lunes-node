package io.lunes.network

import com.google.common.cache.CacheBuilder
import io.lunes.state2.ByteStr

import scala.concurrent.duration.FiniteDuration
import InvalidBlockStorageImpl._
import io.lunes.transaction.ValidationError
import io.lunes.transaction.ValidationError.GenericError

trait InvalidBlockStorage {
  def add(blockId: ByteStr, validationError: ValidationError): Unit

  def find(blockId: ByteStr): Option[ValidationError]
}

class InMemoryInvalidBlockStorage extends InvalidBlockStorage {

  var s: Set[ByteStr] = Set.empty[ByteStr]

  override def add(blockId: ByteStr, validationError: ValidationError): Unit = s += blockId

  override def find(blockId: ByteStr): Option[ValidationError] = {
    if (s.contains(blockId)) Some(GenericError("Unknown")) else None
  }

}

class InvalidBlockStorageImpl(settings: InvalidBlockStorageSettings) extends InvalidBlockStorage {
  private val cache = CacheBuilder
    .newBuilder()
    .expireAfterWrite(settings.timeout.length, settings.timeout.unit)
    .build[ByteStr, ValidationError]()

  override def add(blockId: ByteStr, validationError: ValidationError): Unit = cache.put(blockId, validationError)

  override def find(blockId: ByteStr): Option[ValidationError] = Option(cache.getIfPresent(blockId))
}

object InvalidBlockStorageImpl {

  case class InvalidBlockStorageSettings(maxSize: Int,
                                         timeout: FiniteDuration)

}
