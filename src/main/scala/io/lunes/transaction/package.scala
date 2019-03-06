package io.lunes
import io.lunes.utils.base58Length
import scorex.block.{Block, MicroBlock}

package object transaction {

  type AssetId = io.lunes.state.ByteStr
  val AssetIdLength: Int = io.lunes.crypto.DigestSize
  val AssetIdStringLength: Int = base58Length(AssetIdLength)
  type DiscardedTransactions = Seq[Transaction]
  type DiscardedBlocks = Seq[Block]
  type DiscardedMicroBlocks = Seq[MicroBlock]
  type AuthorizedTransaction = Authorized with Transaction
}
