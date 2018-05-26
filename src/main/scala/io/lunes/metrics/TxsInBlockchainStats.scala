package io.lunes.metrics

import org.influxdb.dto.Point

/**
  *
  */
object TxsInBlockchainStats {
  /**
    *
    * @param number
    */
  def record(number: Int): Unit = Metrics.write(
    Point
      .measurement("applied-txs")
      .addField("n", number)
  )
}
