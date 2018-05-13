package io.lunes.metrics

import kamon.metric.instrument.Histogram
import kamon.util.{NanoInterval, RelativeNanoTimestamp}

/**
  *
  * @param histogram
  */
class LatencyHistogram(private val histogram: Histogram) {
  private var timestamp = Option.empty[RelativeNanoTimestamp]

  /**
    *
    */
  def start(): Unit = {
    timestamp = Some(RelativeNanoTimestamp.now)
  }

  /**
    *
    */
  def record(): Unit = {
    timestamp.foreach(t => histogram.safeRecord(NanoInterval.since(t).nanos))
  }
}
