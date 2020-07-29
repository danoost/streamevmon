package nz.net.wand.streamevmon.runners.unified.schema

import nz.net.wand.streamevmon.{Caching, Lazy}
import nz.net.wand.streamevmon.flink.MeasurementKeySelector
import nz.net.wand.streamevmon.measurements.Measurement

import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.windows.Window

/** Contains a type-filtered stream, as well as keyed and non-lossy variants. */
case class TypedStreams(
  typedStream: Lazy[DataStream[Measurement]]
) extends Caching {

  useInMemoryCache()

  lazy val notLossy: DataStream[Measurement] = typedStream.get
    .filter(!_.isLossy)
    .name("Is not lossy?")
  lazy val keyedStream: KeyedStream[Measurement, String] = typedStream.get
    .keyBy(new MeasurementKeySelector[Measurement])
  lazy val notLossyKeyedStream: KeyedStream[Measurement, String] = notLossy
    .keyBy(new MeasurementKeySelector[Measurement])

  def getWindowedStream(
    sourceName        : String,
    notLossy          : Boolean,
    windowType        : StreamWindowType.Value,
    timeWindowDuration: Time,
    countWindowSize   : Long,
    countWindowSlide  : Long
  ): WindowedStream[Measurement, String, Window] = {
    getWithCache(
      s"windowed-stream:$sourceName-$notLossy-$windowType-$timeWindowDuration-$countWindowSize-$countWindowSlide",
      ttl = None,
      method = {
        Some(
          (windowType, notLossy) match {
            case (_: StreamWindowType.TimeWithOverrides, true) =>
              notLossyKeyedStream.timeWindow(timeWindowDuration)
            case (_: StreamWindowType.TimeWithOverrides, false) =>
              keyedStream.timeWindow(timeWindowDuration)
            case (_: StreamWindowType.CountWithOverrides, true) =>
              notLossyKeyedStream.countWindow(countWindowSize, countWindowSlide)
            case (_: StreamWindowType.CountWithOverrides, false) =>
              keyedStream.countWindow(countWindowSize, countWindowSlide)
            case t => throw new IllegalArgumentException(s"Unrecognised StreamWindowType $t")
          }
        )
      }
    ).get
  }
}