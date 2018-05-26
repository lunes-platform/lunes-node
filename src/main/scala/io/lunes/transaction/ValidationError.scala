package io.lunes.transaction

import com.google.common.base.Throwables
import io.lunes.state2.ByteStr
import scorex.account.{Address, Alias}
import scorex.block.{Block, MicroBlock}
import io.lunes.transaction.assets.exchange.Order

import scala.util.Either

/**
  *
  */
trait ValidationError

/**
  *
  */
object ValidationError {
  /**
    *
     * @tparam T Type for T or a Validation Error.
    */
  type Validation[T] = Either[ValidationError, T]

  /**
    *
    * @param reason
    */
  case class InvalidAddress(reason: String) extends ValidationError

  /**
    *
    * @param amount
    * @param of
    */
  case class NegativeAmount(amount: Long, of: String) extends ValidationError

  /**
    *
    */
  case object InsufficientFee extends ValidationError

  /**
    *
    */
  case object TooBigArray extends ValidationError

  /**
    *
    */
  case object InvalidName extends ValidationError

  /**
    *
    */
  case object OverflowError extends ValidationError

  /**
    *
    */
  case object ToSelf extends ValidationError

  /**
    *
    */
  case object MissingSenderPrivateKey extends ValidationError

  /**
    *
    */
  case object UnsupportedTransactionType extends ValidationError

  /**
    *
    */
  case object InvalidRequestSignature extends ValidationError

  /**
    *
    * @param ts
    */
  case class BlockFromFuture(ts: Long) extends ValidationError

  /**
    *
    * @param s
    * @param details
    */
  case class InvalidSignature(s: Signed, details: Option[InvalidSignature] = None) extends ValidationError {
    override def toString: String = s"InvalidSignature(${s.toString + " reason: " + details})"
  }

  /**
    *
    * @param t
    */
  case class TransactionNotAllowedByScript(t: Transaction) extends ValidationError {
    override def toString: String = s"TransactionNotAllowedByScript($t)"
  }

  /**
    *
    * @param m
    */
  case class ScriptParseError(m: String) extends ValidationError

  /**
    *
    * @param err
    */
  case class GenericError(err: String) extends ValidationError

  /**
    *
    */
  object GenericError {
    /**
      *
      * @param ex
      * @return
      */
    def apply(ex: Throwable): GenericError = new GenericError(Throwables.getStackTraceAsString(ex))
  }

  /**
    *
    * @param txId
    * @param txHeight
    */
  case class AlreadyInTheState(txId: ByteStr, txHeight: Int) extends ValidationError

  /**
    *
    * @param errs
    */
  case class AccountBalanceError(errs: Map[Address, String]) extends ValidationError

  /**
    *
    * @param a
    */
  case class AliasNotExists(a: Alias) extends ValidationError

  /**
    *
    * @param order
    * @param err
    */
  case class OrderValidationError(order: Order, err: String) extends ValidationError

  /**
    *
    * @param addr
    */
  case class SenderIsBlacklisted(addr: String) extends ValidationError

  /**
    *
    * @param err
    */
  case class Mistiming(err: String) extends ValidationError

  /**
    *
    * @param err
    * @param b
    */
  case class BlockAppendError(err: String, b: Block) extends ValidationError

  /**
    *
    * @param err
    * @param microBlock
    */
  case class MicroBlockAppendError(err: String, microBlock: MicroBlock) extends ValidationError {
    override def toString: String = s"MicroBlockAppendError($err, ${microBlock.totalResBlockSig} ~> ${microBlock.prevResBlockSig.trim}])"
  }

  /**
    *
    * @param err
    */
  case class ActivationError(err: String) extends ValidationError

}
