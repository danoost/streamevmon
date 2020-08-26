package nz.net.wand.streamevmon.runners.tuner

import nz.net.wand.streamevmon.runners.tuner.jobs.{JobResult, SimpleJob}
import nz.net.wand.streamevmon.runners.tuner.nab.{NabJob, NabJobResult}
import nz.net.wand.streamevmon.Logging
import nz.net.wand.streamevmon.runners.tuner.parameters.ParameterSpec
import nz.net.wand.streamevmon.runners.tuner.strategies.RandomSearch
import nz.net.wand.streamevmon.runners.unified.schema.DetectorType

object ParameterTuner extends Logging {
  def main(args: Array[String]): Unit = {

    // Squash all the logs from Flink to tidy up our output.
    System.setProperty("org.slf4j.simpleLogger.log.org.apache.flink", "error")

    val searchStrategy = RandomSearch(
      Seq(
        ParameterSpec[Double](
          "detector.baseline.percentile",
          0.1,
          Some(0.0),
          Some(1.0)
        )
      ).asInstanceOf[Seq[ParameterSpec[Any]]]
    )

    ConfiguredPipelineRunner.addJobResultHook {
      jr: JobResult => {
        println(s"Got job result! $jr")
        jr match {
          case NabJobResult(_, results) => println(results)
          case _ =>
        }
      }
    }

    ConfiguredPipelineRunner.submit(SimpleJob("HelloWorld"))
    ConfiguredPipelineRunner.submit(NabJob(
      searchStrategy.nextParameters(),
      "./out/parameterTuner/base",
      detectors = Seq(DetectorType.Baseline),
      skipDetectors = false,
      skipScoring = false
    ))
  }
}
