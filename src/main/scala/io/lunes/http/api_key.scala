package io.lunes.http

import akka.http.scaladsl.model.headers._

import scala.util.Try

/** api_key object   */
object api_key extends ModeledCustomHeaderCompanion[api_key] {
  override val name = "X-API-Key"

  /** Parses String for API Key.
    * @param value Input String.
    * @return Try for a new api_key object.
    */
  override def parse(value: String) = Try(new api_key(value))
}

/** API Key Class.
  * This is a Final Class.
  * @constructor Creates a new API Key Object.
  * @param value Key value.
  */
final class api_key(override val value: String) extends ModeledCustomHeader[api_key] {
  /** Companion
    * @return Returns a api_key object.
    */
  override def companion = api_key

  /** Sets the State for Render In Requests.
    * @return True
    */
  override def renderInRequests = true


  /** Sets the State for Render In Requests.
    * @return False
    */
  override def renderInResponses = false
}

/** Deprecated API Key Object.
  */
object deprecated_api_key extends ModeledCustomHeaderCompanion[deprecated_api_key] {
  override val name = "api_key"

  /** Parses for Deprecated API Key.
    * @param value Input String key.
    * @return Deprecated API key, if exists.
    */
  override def parse(value: String) = Try(new deprecated_api_key(value))
}

/** Deprecated API Key Class
  * @constructor Creates a new Deprecated API key object.
  * @param value Key Name.
  */
final class deprecated_api_key(override val value: String) extends ModeledCustomHeader[deprecated_api_key] {
  /** Returns a Companion API key
    * @return Returns the object itself.
    */
  override def companion = deprecated_api_key

  /** Checks True for render Input Requests.
    * @return Returns true.
    */
  override def renderInRequests = true

  /** Checks false for render Input Responses.
    * @return Returns false.
    */
  override def renderInResponses = false
}
