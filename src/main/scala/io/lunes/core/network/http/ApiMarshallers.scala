package io.lunes.core.storage.http

import scala.util.control.Exception.nonFatalCatch
import scala.util.control.NoStackTrace
import akka.http.scaladsl.marshalling.{Marshaller, PredefinedToEntityMarshallers, ToEntityMarshaller, ToResponseMarshaller}
import akka.http.scaladsl.model.MediaTypes.{`application/json`, `text/plain`}
import akka.http.scaladsl.model.StatusCode
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, PredefinedFromEntityUnmarshallers, Unmarshaller}
import akka.util.ByteString
import play.api.libs.json._
import scorex.api.http.ApiError
import io.lunes.transaction.{Transaction, ValidationError}

/**
  * Play JavaScript Object Notation Exception
  * @constructor Creates a Exception Object for JSON validation
  * @param cause Inputs a Option for Throwable
  * @param errors Sequence of Tuples of JsPath and a Sequence fo JsonValidationError.
  */
case class PlayJsonException(
    cause: Option[Throwable] = None,
    errors: Seq[(JsPath, Seq[JsonValidationError])] = Seq.empty) extends IllegalArgumentException with NoStackTrace

/**
  * Marshaller Trait for the [[io.lunes]] API.
  */
trait ApiMarshallers {
  /**
    * Type for a Response Marshaller parametrized Type.
    * @tparam A Type Parameter
    */
  type TRM[A] = ToResponseMarshaller[A]

  import akka.http.scaladsl.marshalling.PredefinedToResponseMarshallers._
  implicit val aem: TRM[ApiError] = fromStatusCodeAndValue[StatusCode, JsValue].compose { ae => ae.code -> ae.json }
  implicit val vem: TRM[ValidationError] = aem.compose(ve => ApiError.fromValidationError(ve))

  implicit val tw: Writes[Transaction] = Writes(_.json())

  private val jsonStringUnmarshaller =
    Unmarshaller.byteStringUnmarshaller
      .forContentTypes(`application/json`)
      .mapWithCharset {
        case (ByteString.empty, _) => throw Unmarshaller.NoContentException
        case (data, charset)       => data.decodeString(charset.nioCharset.name)
      }

  private lazy val jsonStringMarshaller =
    Marshaller.stringMarshaller(`application/json`)

  /** UnMarshal as a JSON.
    * @param reads Input a Reads Object of Parametrized Type A.
    * @tparam A Parametrized Type.
    * @return Returns a Unmarshalled Object of Parametrized Type A.
    */
  implicit def playJsonUnmarshaller[A](implicit reads: Reads[A]): FromEntityUnmarshaller[A] =
    jsonStringUnmarshaller map { data =>
      val json = nonFatalCatch.withApply(t => throw PlayJsonException(cause = Some(t)))(Json.parse(data))

      json.validate[A] match {
        case JsSuccess(value, _) => value
        case JsError(errors) => throw PlayJsonException(errors = errors)
      }
    }

  // preserve support for extracting plain strings from requests
  implicit val stringUnmarshaller: FromEntityUnmarshaller[String] = PredefinedFromEntityUnmarshallers.stringUnmarshaller
  implicit val intUnmarshaller: FromEntityUnmarshaller[Int] = stringUnmarshaller.map(_.toInt)

  /** Marshalls the Object.
    * @param writes Inputs a Writes Object parametrized by Type A.
    * @param printer Provides a function for better reability of JS Values.
    * @tparam A Parametrized Type.
    * @return Returns a Marshalled Object.
    */
  implicit def playJsonMarshaller[A](
      implicit writes: Writes[A],
      printer: JsValue => String = Json.prettyPrint): ToEntityMarshaller[A] =
    jsonStringMarshaller.compose(printer).compose(writes.writes)

  // preserve support for using plain strings as request entities
  implicit val stringMarshaller = PredefinedToEntityMarshallers.stringMarshaller(`text/plain`)
}

/** API Marshaller Object*/
object ApiMarshallers extends ApiMarshallers
