package scorex.consensus.nxt

import io.lunes.state.ByteStr

case class NxtLikeConsensusBlockData(baseTarget: Long,
                                     generationSignature: ByteStr)
