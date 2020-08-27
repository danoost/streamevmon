package nz.net.wand.streamevmon.runners.tuner.nab

import nz.net.wand.streamevmon.runners.tuner.jobs.{FailedJob, Job, JobResult}
import nz.net.wand.streamevmon.runners.tuner.parameters.Parameters
import nz.net.wand.streamevmon.runners.unified.schema.DetectorType

import java.io._

import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import org.apache.commons.io.{FilenameUtils, FileUtils}

import scala.sys.process._

case class NabJob(
  uid          : String,
  args         : Array[String],
  outputDir    : String,
  detectors    : Iterable[DetectorType.ValueBuilder] = NabJob.allDetectors,
  skipDetectors: Boolean = false,
  skipScoring  : Boolean = false
) extends Job(uid) {

  override def toString: String = s"NabJob-$uid"

  private val shutdownHookThread: Thread = new Thread() {
    override def run(): Unit = {
      logger.error("Job interrupted.")
      new File(s"$outputDir/INTERRUPTED").createNewFile()
    }
  }

  override def run(): JobResult = {
    try {
      Runtime.getRuntime.addShutdownHook(shutdownHookThread)

      val startTime = System.currentTimeMillis()

      if (!skipDetectors) {
        logger.info(s"Starting detectors...")
        logger.debug(s"Using parameters: ")
        args.grouped(2).foreach {
          arg => logger.debug(arg.mkString(" "))
        }
        val runner = new NabAllDetectors(detectors)
        runner.runOnAllNabFiles(args, outputDir, deleteOutputDirectory = false)
      }
      else {
        logger.info(s"Skipping detectors...")
      }

      if (!skipScoring) {
        logger.info("Making output directory sane for scorer...")
        FileUtils.copyDirectory(new File("data/NAB/results/null"), new File(s"$outputDir/null"))

        logger.info(s"Scoring tests from job $this...")
        Seq(
          "./scripts/nab/nab-scorer.sh",
          detectors.mkString(","),
          FilenameUtils.normalize(new File(outputDir).getAbsolutePath)
        ).!!
      }
      else {
        logger.info("Skipping scoring...")
      }

      logger.info("Parsing results...")
      val mapper = new ObjectMapper()
      mapper.registerModule(DefaultScalaModule)
      val results = mapper.readValue(
        new File(s"$outputDir/final_results.json"),
        new TypeReference[Map[String, Map[String, String]]] {}
      )

      val endTime = System.currentTimeMillis()

      logger.info(s"Time taken: ${endTime - startTime}ms")
      val writer = new BufferedWriter(new FileWriter(s"$outputDir/runtime.log"))
      writer.write(s"${endTime - startTime}")
      writer.newLine()
      writer.flush()
      writer.close()

      Runtime.getRuntime.removeShutdownHook(shutdownHookThread)

      NabJobResult(this, results)
    }
    catch {
      case e: Exception =>
        val writer = new PrintWriter(new File(s"$outputDir/fail.log"))
        e.printStackTrace(writer)
        writer.flush()
        writer.close()
        FailedJob(this, e)
    }
  }
}

object NabJob {
  val allDetectors = Seq(
    DetectorType.Baseline,
    DetectorType.Changepoint,
    DetectorType.DistDiff,
    DetectorType.Mode,
    DetectorType.Spike
  )

  def apply(
    params       : Parameters,
    outputDir    : String,
    detectors    : Iterable[DetectorType.ValueBuilder],
    skipDetectors: Boolean,
    skipScoring  : Boolean
  ): NabJob = new NabJob(
    params.hashCode.toString,
    params.getAsArgs.toArray,
    outputDir,
    detectors,
    skipDetectors,
    skipScoring
  )
}
