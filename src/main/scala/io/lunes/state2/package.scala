package io.lunes

import cats.Monoid
import cats.data.{NonEmptyList => NEL}
import io.lunes.state2.reader.SnapshotStateReader
import monix.eval.Coeval
import io.lunes.transaction.ValidationError.GenericError
import io.lunes.transaction.{Transaction, ValidationError}

// import scala.annotation.tailrec
import scala.reflect.ClassTag
import scala.util.{Left, Right, Try}

package object state2 {


  type StateReader = Coeval[SnapshotStateReader]

  def safeSum(x: Long, y: Long): Long = Try(Math.addExact(x, y)).getOrElse(Long.MinValue)

  implicit class EitherExt[L <: ValidationError, R](ei: Either[L, R]) {
    def liftValidationError[T <: Transaction](t: T): Either[ValidationError, R] = {
      ei.left.map(e => GenericError(e.toString))
    }
  }

  implicit class EitherExt2[A, B](ei: Either[A, B]) {
    def explicitGet(): B = ei match {
      case Left(value) => throw new Exception(value.toString)
      case Right(value) => value
    }
  }

  def ranges(from: Int, to: Int, by: Int): Stream[(Int, Int)] =
    if (from + by < to)
      (from, from + by) #:: ranges(from + by, to, by)
    else
      (from, to) #:: Stream.empty[(Int, Int)]

  /**
    *
    * @param list
    * @param cond
    * @tparam A
    * @return
    */
  //@tailrec // adicionado
  def dropLeftIf[A](list: List[A])(cond: List[A] => Boolean): List[A] = list match {
    case l@(x :: xs) => if (cond(l)) dropLeftIf(xs)(cond) else l
    case Nil => Nil
  }

  def splitAfterThreshold[A](list: NEL[A], threshold: Int)(count: A => Int): (NEL[A], List[A]) = {
    //@tailrec
    def splitR(agg: NEL[A], aggCount: Int, rest: List[A]): (NEL[A], List[A]) =
      if (aggCount >= threshold) (agg, rest)
      else rest match {
        case Nil => (agg, rest)
        case (x :: xs) => splitR(x :: agg, count(x) + aggCount, xs)
      }

    val r = splitR(NEL.one(list.head), count(list.head), list.tail)
    (r._1.reverse, r._2)
  }

  /**
    *
    * @param `new`
    * @param existing
    * @param compactPred
    * @param ma
    * @tparam A
    * @return
    */
  def prependCompact[A](`new`: A, existing: NEL[A])(compactPred: (A, A) => Boolean)(implicit ma: Monoid[A]): NEL[A] = {
    if (compactPred(`new`, existing.head)) NEL(Monoid.combine(existing.head, `new`), existing.tail)
    else `new` :: existing
  }

  /**
    *
    * @param `new`
    * @param existing
    * @param maxTxsInChunk
    * @return
    */
  def prependCompactBlockDiff(`new`: BlockDiff, existing: NEL[BlockDiff], maxTxsInChunk: Int): NEL[BlockDiff] =
    prependCompact[BlockDiff](`new`, existing) { case (x, y) => x.txsDiff.transactions.size + y.txsDiff.transactions.size <= maxTxsInChunk }

  /**
    *
    * @param x
    * @param y
    * @param divisor
    * @return
    */
  def sameQuotient(x: Int, y: Int, divisor: Int): Boolean = (x / divisor) == (y / divisor)

  /**
    *
    * @param a
    * @tparam A
    */
  implicit class Cast[A](a: A) {
    def cast[B: ClassTag]: Option[B] = {
      a match {
        case b: B => Some(b)
        case _ => None
      }
    }
  }

}
