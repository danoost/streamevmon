package nz.net.wand.streamevmon.measurements.esmond

import nz.net.wand.streamevmon.connectors.esmond.schema._
import nz.net.wand.streamevmon.measurements.RichMeasurement

import java.time.Instant

case class RichEsmondMeasurement(
  stream       : Int,
  value        : Double,
  metadataKey  : String,
  eventType    : String,
  summaryType  : Option[String],
  summaryWindow: Option[Long],
  time         : Instant
) extends RichMeasurement {
  override def isLossy: Boolean = false
}

object RichEsmondMeasurement {
  def apply(
    stream: Int,
    entry: AbstractTimeSeriesEntry,
    metadataKey: String,
    eventType  : String,
    summaryType: Option[String],
    summaryWindow: Option[Long]
  ): RichEsmondMeasurement = RichEsmondMeasurement(
    stream,
    // TODO: Support all subtypes. Same with EsmondMeasurement.
    entry.asInstanceOf[SimpleTimeSeriesEntry].value,
    metadataKey,
    eventType,
    summaryType,
    summaryWindow,
    Instant.ofEpochSecond(entry.timestamp)
  )

  def apply(
    eventType     : EventType,
    entry         : AbstractTimeSeriesEntry
  ): RichEsmondMeasurement = apply(
    EsmondMeasurement.calculateStreamId(eventType),
    entry,
    eventType.metadataKey,
    eventType.eventType,
    None,
    None
  )

  def apply(
    summary: Summary,
    entry: AbstractTimeSeriesEntry
  ): RichEsmondMeasurement = apply(
    EsmondMeasurement.calculateStreamId(summary),
    entry,
    summary.metadataKey,
    summary.eventType,
    Some(summary.summaryType),
    Some(summary.summaryWindow)
  )
}
