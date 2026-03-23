package io.syspulse.skel.wf.temporal.demo

import com.typesafe.scalalogging.Logger
import spray.json._
import io.hacken.ext.detector.DetectorConfig

/**
 * Demo Activities
 *
 * Simple implementations for auto and human steps
 */
object DemoActivities {
  private val log = Logger(getClass)

  /**
   * Execute auto step - simple sleep work
   *
   * @param config DetectorConfig for the step
   * @return Updated config with output
   */
  def executeStepAuto(config: DetectorConfig): DetectorConfig = {
    log.info(s"Executing StepAuto: id=${config.id}, name=${config.name}")

    // Get sleep duration from config
    val sleepSeconds = DetectorConfig.getInt(config, "sleepSeconds", 3)

    log.info(s"StepAuto sleeping for ${sleepSeconds} seconds...")
    Thread.sleep(sleepSeconds * 1000)

    val output = JsObject(
      "step" -> JsString("auto"),
      "name" -> JsString(config.name),
      "sleepSeconds" -> JsNumber(sleepSeconds),
      "executed" -> JsBoolean(true),
      "timestamp" -> JsNumber(System.currentTimeMillis())
    )

    log.info(s"StepAuto completed: id=${config.id}")

    addOutput(config, output)
  }

  /**
   * Execute human step - waits for signal (handled by workflow)
   *
   * @param config DetectorConfig for the step
   * @return Updated config with output
   */
  def executeStepHuman(config: DetectorConfig): DetectorConfig = {
    log.info(s"Executing StepHuman: id=${config.id}, name=${config.name}")

    // Human step just records that it was reached
    // Actual waiting happens in the workflow via WAIT type
    val output = JsObject(
      "step" -> JsString("human"),
      "name" -> JsString(config.name),
      "waitingForSignal" -> JsBoolean(true),
      "timestamp" -> JsNumber(System.currentTimeMillis())
    )

    log.info(s"StepHuman ready: id=${config.id}, waiting for continue signal")

    addOutput(config, output)
  }

  /**
   * Add output to detector config
   *
   * @param config Original config
   * @param output Output JSON to add
   * @return Updated config with output in config field
   */
  private def addOutput(config: DetectorConfig, output: JsObject): DetectorConfig = {
    val currentConfig = config.config.getOrElse(JsObject())
    val updatedConfig = JsObject(
      currentConfig.fields + ("output" -> output)
    )

    config.copy(
      config = Some(updatedConfig),
      updatedAt = System.currentTimeMillis()
    )
  }
}
