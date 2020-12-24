package io.lunes

import java.security.Security

import com.google.common.base.Throwables
import io.lunes.db.{Storage, VersionedStorage}
import monix.execution.UncaughtExceptionReporter
import org.joda.time.Duration
import org.joda.time.format.PeriodFormat
import scorex.utils.ScorexLogging

import scala.util.Try

package object utils extends ScorexLogging {
  /**
    *
    */
  type HeightInfo = (Int, Long)

  private val BytesMaxValue = 256
  private val Base58MaxValue = 58

  private val BytesLog = math.log(BytesMaxValue)
  private val BaseLog = math.log(Base58MaxValue)

  val UncaughtExceptionsToLogReporter = UncaughtExceptionReporter(exc => log.error(Throwables.getStackTraceAsString(exc)))

  /**
    *
    * @param byteArrayLength
    * @return
    */
  def base58Length(byteArrayLength: Int): Int = math.ceil(BytesLog / BaseLog * byteArrayLength).toInt

  /**
    *
    * @param storage
    * @tparam A
    * @return
    */
  def createWithVerification[A <: Storage with VersionedStorage](storage: => A): Try[A] = Try {
    if (storage.isVersionValid) storage else {
      log.info(s"Re-creating storage")
      val b = storage.createBatch()
      storage.removeEverything(b)
      storage.commit(b)
      storage
    }
  }

  /**
    *
    * @param reason
    */
  def forceStopApplication(reason: ApplicationStopReason = Default): Unit = new Thread(() => {
    System.exit(reason.code)
  }, "lunes-platform-shutdown-thread").start()

  /**
    *
    * @param bytes
    * @param si
    * @return
    */
  def humanReadableSize(bytes: Long, si: Boolean = true): String = {
    val (baseValue, unitStrings) =
      if (si)
        (1000, Vector("B", "kB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB"))
      else
        (1024, Vector("B", "KiB", "MiB", "GiB", "TiB", "PiB", "EiB", "ZiB", "YiB"))

    def getExponent(curBytes: Long, baseValue: Int, curExponent: Int = 0): Int =
      if (curBytes < baseValue) curExponent
      else {
        val newExponent = 1 + curExponent
        getExponent(curBytes / (baseValue * newExponent), baseValue, newExponent)
      }

    val exponent = getExponent(bytes, baseValue)
    val divisor = Math.pow(baseValue, exponent)
    val unitString = unitStrings(exponent)

    f"${bytes / divisor}%.1f $unitString"
  }

  /**
    *
    * @param duration
    * @return
    */
  def humanReadableDuration(duration: Long): String = {
    val d = new Duration(duration)
    PeriodFormat.getDefault.print(d.toPeriod)
  }

  /**
    *
    */
  //TODO: check
  def fixNTP(): Unit ={
    System.setProperty("sun.net.inetaddr.ttl", "0")
    System.setProperty("sun.net.inetaddr.negative.ttl", "0")
    Security.setProperty("networkaddress.cache.ttl", "0")
    Security.setProperty("networkaddress.cache.negative.ttl", "0")
  }

  /**
    *
    * @param a
    * @tparam A
    */
  implicit class Tap[A](a: A) {
    def tap(g: A => Unit): A = {
      g(a)
      a
    }
  }
}
