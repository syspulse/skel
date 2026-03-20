package io.syspulse.skel.wf.temporal.por

import spray.json._

/**
 * Generic step input that can be updated via API signals
 *
 * @param stepId Unique identifier for the workflow step
 * @param data Step-specific data as JSON
 * @param metadata Optional metadata
 */
case class StepInput(
  stepId: String,
  data: JsValue,
  metadata: Map[String, String] = Map.empty,
  timestamp: Long = System.currentTimeMillis()
)

object StepInput extends DefaultJsonProtocol {
  implicit val format: RootJsonFormat[StepInput] = jsonFormat4(StepInput.apply)
}

/**
 * Container for all step inputs in a workflow
 */
case class WorkflowInputs(
  inputs: Map[String, StepInput] = Map.empty
) {
  def update(input: StepInput): WorkflowInputs = {
    copy(inputs = inputs + (input.stepId -> input))
  }

  def get(stepId: String): Option[StepInput] = inputs.get(stepId)

  def getAs[T](stepId: String)(implicit reader: JsonReader[T]): Option[T] = {
    get(stepId).map(_.data.convertTo[T])
  }
}

object WorkflowInputs extends DefaultJsonProtocol {
  implicit val format: RootJsonFormat[WorkflowInputs] = jsonFormat1(WorkflowInputs.apply)
}
