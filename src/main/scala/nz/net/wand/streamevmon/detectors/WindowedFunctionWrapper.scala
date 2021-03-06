/* This file is part of streamevmon.
 *
 * Copyright (C) 2021  The University of Waikato, Hamilton, New Zealand
 *
 * Author: Daniel Oosterwijk
 *
 * All rights reserved.
 *
 * This code has been developed by the University of Waikato WAND
 * research group. For further information please see https://wand.nz,
 * or our Github organisation at https://github.com/wanduow
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package nz.net.wand.streamevmon.detectors

import nz.net.wand.streamevmon.events.Event
import nz.net.wand.streamevmon.flink.HasFlinkConfig
import nz.net.wand.streamevmon.measurements.traits.Measurement
import nz.net.wand.streamevmon.measurements.MeasurementKeySelector

import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.api.scala.{OutputTag => SOutputTag}
import org.apache.flink.streaming.api.scala.function.ProcessWindowFunction
import org.apache.flink.streaming.api.windowing.windows.Window
import org.apache.flink.streaming.api.TimerService
import org.apache.flink.util.{Collector, OutputTag => JOutputTag}

import scala.reflect.ClassTag

/** Wraps a KeyedProcessFunction, which most detectors are, to turn them into
  * ProcessWindowFunctions. This lets us use Flink's windowing system with any
  * detector if we so choose, not just those which implement windowed behaviour
  * manually.
  */
class WindowedFunctionWrapper[MeasT <: Measurement : ClassTag, W <: Window](
  val processFunction: KeyedProcessFunction[String, MeasT, Event] with HasFlinkConfig,
) extends ProcessWindowFunction[MeasT, Event, String, W]
          with HasFlinkConfig {

  private lazy val keySelector: MeasurementKeySelector[MeasT] = new MeasurementKeySelector[MeasT]

  override def open(parameters: Configuration): Unit = {
    processFunction.setRuntimeContext(getRuntimeContext)
    processFunction.open(parameters)
  }

  override def close(): Unit = processFunction.close()

  override def process(
    key : String,
    myContext: this.Context,
    elements: Iterable[MeasT],
    out: Collector[Event]
  ): Unit = {
    // We'll make sure that they're sorted correctly - they probably already
    // are, but this won't hurt.
    elements.toSeq.sortBy(_.time).foreach { e =>
      val ctx: processFunction.Context = new processFunction.Context() {
        override def timestamp(): java.lang.Long = e.time.toEpochMilli

        // We don't use this in any of our detectors, so we'll leave it unimplemented.
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

  // Most function calls just get passed down into the wrapped object.
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
