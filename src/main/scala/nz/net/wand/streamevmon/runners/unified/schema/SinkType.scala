package nz.net.wand.streamevmon.runners.unified.schema

import nz.net.wand.streamevmon.events.Event
import nz.net.wand.streamevmon.flink.{HasFlinkConfig, InfluxSinkFunction}

import org.apache.flink.streaming.api.functions.sink.{PrintSinkFunction, SinkFunction}

/** This enum includes logic to build sinks. */
object SinkType extends Enumeration {

  val Influx: ValueBuilder = new ValueBuilder("influx")
  val Print: ValueBuilder = new ValueBuilder("print")

  class ValueBuilder(name: String) extends Val(name) {
    def build: SinkFunction[Event] with HasFlinkConfig = {
      this match {
        case Influx => new InfluxSinkFunction
        // Override configurations can be supplied, but will be ignored.
        case Print => new PrintSinkFunction[Event] with HasFlinkConfig {
          override val flinkName: String = "Print: Std Out"
          override val flinkUid: String = "print-sink"
          override val configKeyGroup: String = ValueBuilder.this.name
        }
        case t => throw new UnsupportedOperationException(s"Unknown sink type $t")
      }
    }
  }
}
