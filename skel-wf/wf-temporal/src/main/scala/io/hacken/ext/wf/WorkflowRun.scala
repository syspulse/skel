package io.hacken.ext.wf

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.{Duration,FiniteDuration}
import java.util.concurrent.atomic.AtomicInteger
import com.typesafe.scalalogging.Logger

import spray.json._
import io.syspulse.skel.service.JsonCommon
import java.util.concurrent.TimeUnit

import io.syspulse.skel.Ingestable
import io.syspulse.skel.util.Util

// Workflow Run - context defining workflow instance execution propagated through all steps and activities
case class WorkflowRun(
  wid: String,   // Workflow ID as in Temporal Workflow ID
  rid: Option[String],   // Run ID: reference to find WorkflowRun in WorkflowRunStore. Also used as Temporal Run ID

  status: String, // "NEW", "RUNNING", "WAITING", "FINISHED", "FAILED", "STOPPED"
  cursor: Int, // Cursor to DetectorConfig id instance currently executing. -1 means not started yet

  schema: Int, // WorkflowSchema ID reference to map to WorkflowSchema

  steps: Seq[Int], // References to DetectorConfig runtime instance IDs
)

object WorkflowRunJson extends JsonCommon {
  implicit val jf_wf_run: RootJsonFormat[WorkflowRun] = jsonFormat6(WorkflowRun)
}
