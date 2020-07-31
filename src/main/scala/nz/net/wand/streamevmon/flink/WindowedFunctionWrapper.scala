package nz.net.wand.streamevmon.flink

import nz.net.wand.streamevmon.events.Event
import nz.net.wand.streamevmon.measurements.Measurement

import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.api.scala.{OutputTag => SOutputTag}
import org.apache.flink.streaming.api.scala.function.ProcessWindowFunction
import org.apache.flink.streaming.api.windowing.windows.Window
import org.apache.flink.streaming.api.TimerService
import org.apache.flink.util.{Collector, OutputTag => JOutputTag}

import scala.reflect.ClassTag

class WindowedFunctionWrapper[M <: Measurement : ClassTag, W <: Window](
  val processFunction: KeyedProcessFunction[String, M, Event] with HasFlinkConfig,
) extends ProcessWindowFunction[M, Event, String, W]
          with HasFlinkConfig {

  private lazy val keySelector: MeasurementKeySelector[M] = new MeasurementKeySelector[M]

  override def open(parameters: Configuration): Unit = {
    processFunction.setRuntimeContext(getRuntimeContext)
    processFunction.open(parameters)
  }

  override def close(): Unit = processFunction.close()

  override def process(
    key : String,
    myContext: this.Context,
    elements: Iterable[M],
    out: Collector[Event]
  ): Unit = {
    for (e <- elements.map(identity).toSeq.sortBy(_.time)) {
      val ctx: processFunction.Context = new processFunction.Context() {
        override def timestamp(): java.lang.Long = e.time.toEpochMilli

        override def timerService(): TimerService = ???

        override def output[X](outputTag: JOutputTag[X], value: X): Unit = {
          myContext.output[X](
            SOutputTag(outputTag.getId)(outputTag.getTypeInfo),
            value
          )
        }

        override def getCurrentKey: String = keySelector.getKey(e)
      }

      processFunction.processElement(
        e,
        ctx,
        out
      )
    }
  }

  override def overrideConfig(config: Map[String, String], addPrefix: String): WindowedFunctionWrapper.this.type = {
    processFunction.overrideConfig(config, addPrefix)
    super.overrideConfig(config, addPrefix)
  }

  override def overrideConfig(config: ParameterTool): WindowedFunctionWrapper.this.type = {
    processFunction.overrideConfig(config)
    super.overrideConfig(config)
  }

  override val flinkName: String = s"${processFunction.flinkName} (Window Wrapped)"
  override val flinkUid: String = s"window-wrapped-${processFunction.flinkUid}"
  override val configKeyGroup: String = processFunction.configKeyGroup
}
