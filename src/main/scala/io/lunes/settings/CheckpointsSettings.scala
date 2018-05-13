package io.lunes.settings

import com.typesafe.config.Config
import io.lunes.state2.ByteStr
import net.ceedubs.ficus.Ficus._

/**
  *
  * @param publicKey
  */
case class CheckpointsSettings(publicKey: ByteStr)

/**
  *
  */
object CheckpointsSettings {
  val configPath: String = "lunes.checkpoints"

  /**
    *
    * @param config
    * @return
    */
  def fromConfig(config: Config): CheckpointsSettings = {
    val publicKey = config.as[ByteStr](s"$configPath.public-key")

    CheckpointsSettings(publicKey)
  }
}