package io.lunes.metrics

import kamon.metric.instrument.Histogram
import scorex.utils.ScorexLogging

/**
  *
  */
trait Instrumented {
  self: ScorexLogging =>

  import Instrumented._

  /**
    *
    * @param s
    * @param fa
    * @param f
    * @tparam F
    * @tparam A
    * @tparam R
    * @return
    */
  def measureSizeLog[F[_] <: TraversableOnce[_], A, R](s: String)(fa: => F[A])(f: F[A] => R): R = {
    val (r, time) = withTime(f(fa))
    log.trace(s"processing of ${fa.size} $s took ${time}ms")
    r
  }

  /**
    *
    * @param s
    * @param f
    * @tparam R
    * @return
    */
  def measureLog[R](s: String)(f: => R): R = {
    val (r, time) = withTime(f)
    log.trace(s"$s took ${time}ms")
    r
  }

  /**
    *
    * @param h
    * @param f
    * @tparam A
    * @tparam B
    * @return
    */
  def measureSuccessful[A, B](h: Histogram, f: => Either[A, B]): Either[A, B] = {
    val (r, time) = withTime(f)
    if (r.isRight)
      h.safeRecord(time)
    r
  }

  /**
    *
    * @param h
    * @param f
    * @tparam A
    * @return
    */
  def measureSuccessful[A](h: Histogram, f: => Option[A]): Option[A] = {
    val (r, time) = withTime(f)
    if (r.isDefined)
      h.safeRecord(time)
    r
  }

  /**
    *
    * @param writeTime
    * @param f
    * @tparam A
    * @tparam B
    * @return
    */
  def measureSuccessfulFun[A, B](writeTime: Long => Unit, f: => Either[A, B]): Either[A, B] = {
    val (r, time) = withTime(f)
    if (r.isRight)
      writeTime(time)
    r
  }

  /**
    *
    * @param writeTime
    * @param f
    * @tparam A
    * @return
    */
  def measureSuccessfulFun[A](writeTime: Long => Unit, f: => Option[A]): Option[A] = {
    val (r, time) = withTime(f)
    if (r.isDefined)
      writeTime(time)
    r
  }
}

/**
  *
  */
object Instrumented {
  /**
    *
    * @param f
    * @tparam R
    * @return
    */
  def withTime[R](f: => R): (R, Long) = {
    val t0 = System.currentTimeMillis()
    val r: R = f
    val t1 = System.currentTimeMillis()
    (r, t1 - t0)
  }
}
