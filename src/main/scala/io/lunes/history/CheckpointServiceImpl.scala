package io.lunes.history

import io.lunes.crypto
import io.lunes.db.{CheckpointCodec, PropertiesStorage, SubStorage}
import io.lunes.network.Checkpoint
import io.lunes.settings.CheckpointsSettings
import org.iq80.leveldb.DB
import io.lunes.transaction.ValidationError.GenericError
import io.lunes.transaction.{CheckpointService, ValidationError}

/**
  *
  * @param db
  * @param settings
  */
class CheckpointServiceImpl(db: DB, settings: CheckpointsSettings)
  extends SubStorage(db, "checkpoints") with PropertiesStorage with CheckpointService {

  private val CheckpointProperty = "checkpoint"

  /**
    *
    * @return
    */
  override def get: Option[Checkpoint] = getProperty(CheckpointProperty).flatMap(b => CheckpointCodec.decode(b).toOption.map(r => r.value))

  /**
    *
    * @param cp
    * @return
    */
  override def set(cp: Checkpoint): Either[ValidationError, Unit] = for {
    _ <- Either.cond(!get.forall(_.signature sameElements cp.signature), (), GenericError("Checkpoint already applied"))
    _ <- Either.cond(crypto.verify(cp.signature, cp.toSign, settings.publicKey.arr),
      putProperty(CheckpointProperty, CheckpointCodec.encode(cp), None),
      GenericError("Invalid checkpoint signature"))
  } yield ()

}