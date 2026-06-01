package io.syspulse.skel.wf.temporal.workflow.store

import com.typesafe.scalalogging.Logger

import io.syspulse.skel.wf.temporal.por2.Por2Schema
import io.syspulse.skel.wf.temporal.demo.DemoSchema

/**
 * Test store pre-populated with all schemas from Por2 and Demo
 *
 * This store inherits from WorkflowSchemaStoreMem and automatically
 * initializes with sample schemas for testing and development.
 */
class WorkflowSchemaStoreTest extends WorkflowSchemaStoreMem {
  private val log = Logger(getClass)

  // Pre-populate with Por2 schema
  val por2Schema = Por2Schema.buildSchema(schemaId = 1, tenantId = 1, projectId = 1)
  this.+(por2Schema)
  log.info(s"Pre-populated Por2 schema: ${por2Schema.name} (id=${por2Schema.id})")

  // Pre-populate with Demo schemas (flow-1 through flow-5, matching Por2Schema style)
  val demoFlow1 = DemoSchema.buildSchemaFromFlow(
    flowStr = "auto",
    schemaId = 2,
    title = "{name}-{tid}-{pid}-{ts}"  // Placeholder title like Por2
  )
  this.+(demoFlow1)
  log.info(s"Pre-populated Demo schema: flow-1 (id=${demoFlow1.id})")

  val demoFlow2 = DemoSchema.buildSchemaFromFlow(
    flowStr = "auto -> human",
    schemaId = 3,
    title = "{name}-{tid}-{pid}-{ts}"
  )
  this.+(demoFlow2)
  log.info(s"Pre-populated Demo schema: flow-2 (id=${demoFlow2.id})")

  val demoFlow3 = DemoSchema.buildSchemaFromFlow(
    flowStr = "auto -> human -> auto",
    schemaId = 4,
    title = "{name}-{tid}-{pid}-{ts}"
  )
  this.+(demoFlow3)
  log.info(s"Pre-populated Demo schema: flow-3 (id=${demoFlow3.id})")

  val demoFlow4 = DemoSchema.buildSchemaFromFlow(
    flowStr = "human -> auto",
    schemaId = 5,
    title = "{name}-{tid}-{pid}-{ts}"
  )
  this.+(demoFlow4)
  log.info(s"Pre-populated Demo schema: flow-4 (id=${demoFlow4.id})")

  val demoFlow5 = DemoSchema.buildSchemaFromFlow(
    flowStr = "auto -> auto -> human",
    schemaId = 6,
    title = "{name}-{tid}-{pid}-{ts}"
  )
  this.+(demoFlow5)
  log.info(s"Pre-populated Demo schema: flow-5 (id=${demoFlow5.id})")

  log.info(s"WorkflowSchemaStoreTest initialized with 6 schemas (1 Por2 + 5 Demo)")
}
