package io.lunes.network

import java.net.InetSocketAddress

import io.lunes.crypto
import io.lunes.state2.ByteStr
import monix.eval.Coeval
import scorex.account.{PrivateKeyAccount, PublicKeyAccount}
import scorex.block.{Block, MicroBlock}
import io.lunes.transaction.{History, Signed}

/** Base Sealed Trait for Algebraic Data Type.
  *
  * Possible Values Are:
  * [[GetPeers]]
  * [[KnownPeers]]
  * [[GetSignatures]]
  * [[Signatures]]
  * [[GetBlock]]
  * [[LocalScoreChanged]]
  * [[RawBytes]]
  * [[BlockForged]]
  * [[MicroBlockRequest]]
  * [[MicroBlockResponse]]
  * */
sealed trait Message

/** Case object for ADT Message. */
case object GetPeers extends Message

/**  Case Class for ADT Message.
  * @constructor Creates a ADT value given a Sequence of [[InetSocketAddress]].
  * @param peers Sequence of Addresses.
  */
case class KnownPeers(peers: Seq[InetSocketAddress]) extends Message

/** Case Class for ADT Message
  * @constructor Creates a ADT value given a Sequence of Signatures [[ByteStr]].
  * @param signatures Sequence of Signatures.
  */
case class GetSignatures(signatures: Seq[ByteStr]) extends Message {
  override def toString: String = s"GetSignatures(${formatSignatures(signatures)})"
}

/** Case Class for ADT Message
  * @constructor Creates a ADT value given a Sequence of Signatures [[ByteStr]]
  * @param signatures The Sequence of Signatures.
  */
case class Signatures(signatures: Seq[ByteStr]) extends Message {
  override def toString: String = s"Signatures(${formatSignatures(signatures)})"

}

/** Case Class for ADT Message
  * @constructor Creates a ADT value given a Signature [[ByteStr]].
  * @param signature The Signature.
  */
case class GetBlock(signature: ByteStr) extends Message

/** Case Class for ADT Message
  * @constructor Creates a ADT value given a [[History.BlockchainScore]].
  * @param newLocalScore The Score.
  */
case class LocalScoreChanged(newLocalScore: History.BlockchainScore) extends Message

/** Case Class for ADT Message
  * @constructor Creates a ADT value given a Code and Data.
  * @param code A byte assigning a code.
  * @param data Array of Byte for the data.
  */
case class RawBytes(code: Byte, data: Array[Byte]) extends Message

/** Case Class for ADT Message.
  * @constructor Creates a ADT value given a [[Block]].
  * @param block The input Block.
  */
case class BlockForged(block: Block) extends Message

/** Case Class for ADT Message.
  * @constructor Creates a ADT value given a Block Signature [[ByteStr]].
  * @param totalBlockSig The total Block Signature.
  */
case class MicroBlockRequest(totalBlockSig: ByteStr) extends Message

/** Case Class for ADT Message.
  * @constructor Creates a ADT value given a [[MicroBlock]].
  * @param microblock The Micro Block.
  */
case class MicroBlockResponse(microblock: MicroBlock) extends Message

/** Case Class for MicroBlockInv.
  * @param sender Sender's Public Key.
  * @param totalBlockSig Total Block Signature.
  * @param prevBlockSig Previous Block Signature.
  * @param signature Micro Block Signature.
  */
case class MicroBlockInv(sender: PublicKeyAccount, totalBlockSig: ByteStr, prevBlockSig: ByteStr, signature: ByteStr) extends Message with Signed {
  override protected val signatureValid: Coeval[Boolean] = Coeval.evalOnce(
    crypto.verify(signature.arr, sender.toAddress.bytes.arr ++ totalBlockSig.arr ++ prevBlockSig.arr, sender.publicKey))

  override def toString: String = s"MicroBlockInv(${totalBlockSig.trim} ~> ${prevBlockSig.trim})"
}

/** Companion Object for MicroBlockInv*/
object MicroBlockInv {
  /** Factory for MicroBlockInv.
    * @param sender Sender's Private Key.
    * @param totalBlockSig Total Block Signature.
    * @param prevBlockSig Previous Block Signature.
    * @return The MicroBlockInv Object.
    */
  def apply(sender: PrivateKeyAccount, totalBlockSig: ByteStr, prevBlockSig: ByteStr): MicroBlockInv = {
    val signature = crypto.sign(sender, sender.toAddress.bytes.arr ++ totalBlockSig.arr ++ prevBlockSig.arr)
    new MicroBlockInv(sender, totalBlockSig, prevBlockSig, ByteStr(signature))
  }
}
