package io.syspulse.skel.wf.temporal.demo

import spray.json._
import io.hacken.ext.wf.{WorkflowSchema, WorkflowSchemaNode, WorkflowSchemaConnection}
import io.hacken.ext.detector.{DetectorSchema, DetectorConfig, DetectorConfigContract}

/**
 * Demo Workflow Schema Builder
 *
 * Creates simple workflows with auto and human steps
 */
object DemoSchema {

  /**
   * Parse flow string and build workflow schema
   *
   * @param flowStr Flow string like "auto -> human -> auto"
   * @param schemaId Schema ID
   * @param title Workflow title (may contain placeholders)
   * @return WorkflowSchema with parsed steps
   */
  def buildSchemaFromFlow(
    flowStr: String,
    schemaId: Int = 1,
    title: String = "Demo Workflow"
  ): WorkflowSchema = {

    val ts = System.currentTimeMillis()

    // Parse flow string into step names
    val stepNames = flowStr.split("->").map(_.trim).toSeq

    // Build detector schemas for each step
    val detectorSchemas = stepNames.zipWithIndex.map { case (stepName, index) =>
      val stepId = index + 1
      val (name, stepTitle, stepType) = stepName.toLowerCase match {
        case "auto" => ("StepAuto", "Auto Step", "AUTO")
        case "human" => ("StepHuman", "Human Step", "WAIT")
        case _ => throw new IllegalArgumentException(s"Unknown step type: $stepName (use 'auto' or 'human')")
      }

      DetectorSchema(
        id = stepId,
        createdAt = ts,
        updatedAt = ts,
        status = "ACTIVE",
        name = name,
        version = "1.0.0",
        title = s"$stepTitle $stepId",
        description = s"Demo $stepName step",
        author = "Demo",
        icon = None,
        faq = None,
        tags = Seq("demo", stepName),
        networkTags = Seq.empty,
        schema = Some(JsObject(
          "type" -> JsString(stepType),
          "stepName" -> JsString(stepName)
        )),
        uiSchema = None
      )
    }

    // Build nodes
    val nodes = detectorSchemas.map { schema =>
      WorkflowSchemaNode(
        id = schema.id,
        name = schema.title,
        sid = schema.id,
        typ = Some("detector"),
        icon = schema.icon,
        schema = schema
      )
    }

    // Build connections (sequential chain)
    val connections = (1 until detectorSchemas.size).map { i =>
      WorkflowSchemaConnection(
        id = i,
        from = i,
        to = i + 1
      )
    }

    WorkflowSchema(
      id = schemaId,
      createdAt = ts,
      updatedAt = ts,
      status = "ACTIVE",
      name = "Demo-Flow",
      version = "1.0.0",
      title = title,
      description = s"Demo workflow: $flowStr",
      author = "Demo",
      icon = Some("play-circle"),
      faq = None,
      tags = Seq("demo", "test"),
      nodes = nodes,
      connections = connections
    )
  }

  /**
   * Build DetectorConfig instances for each step
   *
   * @param stepNames Step names from parsed flow
   * @return Sequence of DetectorConfig instances
   */
  def buildStepConfigs(stepNames: Seq[String]): Seq[DetectorConfig] = {

    val ts = System.currentTimeMillis()

    def createContract(id: Int, name: String) = DetectorConfigContract(
      id = id,
      createdAt = ts,
      updatedAt = ts,
      projectId = 1,
      tenantId = 1,
      chainUid = None,
      proxyAddress = None,
      implementation = None,
      address = None,
      name = name
    )

    stepNames.zipWithIndex.map { case (stepName, index) =>
      val stepId = index + 1
      val (name, stepType) = stepName.toLowerCase match {
        case "auto" => ("StepAuto", "AUTO")
        case "human" => ("StepHuman", "WAIT")
        case _ => throw new IllegalArgumentException(s"Unknown step type: $stepName")
      }

      DetectorConfig(
        id = stepId,
        createdAt = ts,
        updatedAt = ts,
        status = "ACTIVE",
        contract = createContract(stepId, s"$name-Contract"),
        schema = None,
        name = name,
        source = "DEMO",
        tags = Seq("demo", stepName),
        config = Some(JsObject(
          "type" -> JsString(stepType),
          "stepName" -> JsString(stepName),
          "sleepSeconds" -> JsNumber(if (stepType == "AUTO") 3 else 0)
        )),
        destinations = Seq()
      )
    }
  }
}
