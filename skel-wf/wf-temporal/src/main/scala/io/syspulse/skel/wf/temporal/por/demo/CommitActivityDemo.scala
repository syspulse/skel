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

  def execute(run: PorWorkflowRun): PorWorkflowRun = {
    val activityInfo = Activity.getExecutionContext.getInfo
    val wid = activityInfo.getWorkflowId
    
    val mapper = new ObjectMapper()
    mapper.registerModule(DefaultScalaModule)

    //val ts = System.currentTimeMillis()
    
    val filePath = run.input.commit.flatMap(_.config.get("file").map(f => os.Path(f, os.pwd)))
      .getOrElse(os.temp.dir() / s"por-workflow-output-${wid}.json")

    try {
      val commit = CommitOutput(filePath.toString)
      val run1 = run.copy(output = run.output.copy(commit = Some(commit)))

      val json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(run1)
      os.write(filePath, json)

      log.info(s"[$wid] Committed: $filePath")
      run1
      
    } catch {
      case e: Exception =>
        log.error(s"[$wid] Failed to commit: ${filePath}: ${e.getMessage}", e)
        throw e
    }
  }
}
