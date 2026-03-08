package io.syspulse.skel.wf.temporal.por.demo

import com.typesafe.scalalogging.Logger
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import io.temporal.activity.Activity

import io.syspulse.skel.wf.temporal.por._

/**
 * Demo implementation of CommitActivity
 * Writes workflow outputs to JSON files in temp directory
 */
class CommitActivityDemo {
  private val log = Logger(getClass.getName)

  def execute(output: PorWorkflowOutput): CommitOutput = {
    val activityInfo = Activity.getExecutionContext.getInfo
    val wid = activityInfo.getWorkflowId

    log.info(s"[$wid] Committing PorWorkflowOutput to file")

    val mapper = new ObjectMapper()
    mapper.registerModule(DefaultScalaModule)

    val timestamp = System.currentTimeMillis()
    val fileName = s"por-workflow-output-${timestamp}.json"
    val filePath = os.temp.dir() / fileName

    try {
      val json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(output)
      os.write(filePath, json)

      log.info(s"[$wid] Successfully wrote PorWorkflowOutput to: $filePath")
      log.info(s"[$wid] Output contains: PoO=${output.pooOutput.isDefined}, PoR=${output.porOutput.isDefined}, PoL=${output.polOutput.isDefined}, Solvency=${output.solvencyOutput.isDefined}, Report=${output.reportOutput.isDefined}")

      CommitOutput(filePath.toString)
    } catch {
      case e: Exception =>
        log.error(s"[$wid] Failed to write PorWorkflowOutput: ${e.getMessage}", e)
        throw e
    }
  }
}
