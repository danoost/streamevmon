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

package nz.net.wand.streamevmon.runners.examples

import nz.net.wand.streamevmon.Configuration
import nz.net.wand.streamevmon.detectors.baseline.BaselineDetector
import nz.net.wand.streamevmon.detectors.changepoint.{ChangepointDetector, NormalDistribution}
import nz.net.wand.streamevmon.detectors.distdiff.DistDiffDetector
import nz.net.wand.streamevmon.detectors.mode.ModeDetector
import nz.net.wand.streamevmon.detectors.spike.SpikeDetector
import nz.net.wand.streamevmon.events.Event
import nz.net.wand.streamevmon.flink.sources.LatencyTSAmpFileInputFormat
import nz.net.wand.streamevmon.measurements.latencyts.LatencyTSAmpICMP
import nz.net.wand.streamevmon.measurements.MeasurementKeySelector

import java.io.File

import org.apache.commons.io.FilenameUtils
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.scala.operators.ScalaCsvOutputFormat
import org.apache.flink.core.fs.Path
import org.apache.flink.streaming.api.scala._

import scala.reflect.io.Directory

/** Runs all detectors against each series in the Latency TS I dataset.
  *
  * Each file is run separately so that we can more explicitly put the results
  * in the correct files. This could be done with a single Flink pipeline, but
  * using the StreamingFileSink with finite sources is a pain, and naming the
  * files how they should be is a lot more verbose. This is a simpler and nicer
  * solution that just adds a bit of extra overhead to create the pipeline for
  * each file.
  */
object LatencyTSAllDetectors {

  def runTest(file: File): Unit = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment

    val config = Configuration.get()
    env.getConfig.setGlobalJobParameters(config)

    env.disableOperatorChaining

    // Our detectors are all keyed so that data goes to the right place in
    // environments where there's multiple streams.
    val keySelector = new MeasurementKeySelector[LatencyTSAmpICMP]

    // We do single parallelism just to make sure everything goes in the same
    // order. This is probably not needed, but that's fine.
    val input = env
      .readFile(new LatencyTSAmpFileInputFormat, file.getCanonicalPath)
      .setParallelism(1)
      .keyBy(keySelector)

    implicit val normalDistributionTypeInformation: TypeInformation[NormalDistribution[LatencyTSAmpICMP]] =
      TypeInformation.of(classOf[NormalDistribution[LatencyTSAmpICMP]])

    // List all the detectors we want to use.
    val detectors = Seq(
      new BaselineDetector[LatencyTSAmpICMP],
      new ChangepointDetector[LatencyTSAmpICMP, NormalDistribution[LatencyTSAmpICMP]](
        new NormalDistribution[LatencyTSAmpICMP](mean = 0)
      ),
      new DistDiffDetector[LatencyTSAmpICMP],
      new ModeDetector[LatencyTSAmpICMP],
      new SpikeDetector[LatencyTSAmpICMP],
    )

    // Every detector should be used as a ProcessFunction...
    val detectorsWithSource = detectors.map { det =>
      input.process(det)
    }
    // ... then we merge their outputs so they can go to a single sink.
    val detectorsAsOne = detectorsWithSource.head.union(detectorsWithSource.drop(1): _*)

    // Event doesn't support CsvOutputable, so we just do it manually here.
    val eventsAsTuple = detectorsAsOne
      .map { e =>
        val tuple = Event.unapply(e).get
        (
          tuple._1,
          tuple._3,
          tuple._4.toEpochMilli,
          tuple._6
        )
      }

    // We can happily use the ScalaCsvOutputFormat in cases where the input
    // type is identical, including the width.
    val outputFormat = new ScalaCsvOutputFormat[(String, Int, Long, String)](
      new Path(s"./out/allDetectors/${FilenameUtils.removeExtension(file.getName)}.events.csv")
    )
    // Let's write it to file and print it at the same time.
    eventsAsTuple
      .writeUsingOutputFormat(outputFormat)
      .setParallelism(1)
    eventsAsTuple
      .print()
      .setParallelism(1)

    env.execute()
  }

  def main(args: Array[String]): Unit = {
    // Delete the existing outputs so it doesn't append when we don't want it to.
    new Directory(new File("./out/allDetectors")).deleteRecursively()

    // Use all the series files, making sure we don't get anything else like
    // .events files or READMEs.
    new File("data/latency-ts-i/ampicmp/")
      .listFiles(_.getName.endsWith(".series"))
      .foreach(runTest)
  }
}
