package nz.net.wand.streamevmon.detectors

import nz.net.wand.streamevmon.events.{Event, ThresholdEvent}
import nz.net.wand.streamevmon.measurements.{Measurement, RichICMP}

import org.apache.flink.streaming.api.scala.function.ProcessAllWindowFunction
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.util.Collector

/** Very basic example of threshold detection.
  *
  * Examines [[nz.net.wand.streamevmon.measurements.RichICMP RichICMP]]
  * objects, and emits events with a constant severity if the median value is
  * greater than the specified value (default 1000).
  *
  * @tparam T This class can accept any type of Measurement, but only provides
  *           output if the measurement is a RichICMP.
  */
class SimpleThresholdDetector[T <: Measurement](threshold: Int = 1000)
    extends ProcessAllWindowFunction[T, Event, TimeWindow] {

  private val description = s"Median latency was over $threshold"

  override def process(context: Context, elements: Iterable[T], out: Collector[Event]): Unit = {
    elements
      .filter(_.isInstanceOf[RichICMP])
      .map(_.asInstanceOf[RichICMP])
      .filter(_.median.getOrElse(Int.MinValue) > threshold)
      .foreach(
        m =>
          out.collect(
            ThresholdEvent(
              tags = Map(
                "stream" -> m.stream.toString
              ),
              severity = 10,
              eventTime = m.time,
              detectionLatency = m.median.getOrElse(Int.MinValue).toLong,
              description = description
            )
        )
      )
  }
}
