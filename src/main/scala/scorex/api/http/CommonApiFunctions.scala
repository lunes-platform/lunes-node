package scorex.api.http

import akka.http.scaladsl.server.Directive1
import io.lunes.state2.ByteStr
import scorex.block.Block
import io.lunes.transaction.{History, TransactionParser}


trait CommonApiFunctions { this: ApiRoute =>
  protected[api] def withBlock(history: History, encodedSignature: String): Directive1[Block] =
  if (encodedSignature.length > TransactionParser.SignatureStringLength) complete(InvalidSignature) else {
    ByteStr.decodeBase58(encodedSignature).toOption.toRight(InvalidSignature)
        .flatMap(s => history.blockById(s).toRight(BlockNotExists)) match {
      case Right(b) => provide(b)
      case Left(e) => complete(e)
    }
  }
}
