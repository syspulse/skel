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

  // Pre-populate with Demo schemas (sample flows)
  val demoSchema2Step = DemoSchema.buildSchemaFromFlow(
    flowStr = "auto -> human",
    schemaId = 2,
    title = "Demo 2-Step Flow"
  )
  this.+(demoSchema2Step)
  log.info(s"Pre-populated Demo schema: ${demoSchema2Step.name} (id=${demoSchema2Step.id})")

  val demoSchema3Step = DemoSchema.buildSchemaFromFlow(
    flowStr = "auto -> human -> auto",
    schemaId = 3,
    title = "Demo 3-Step Flow"
  )
  this.+(demoSchema3Step)
  log.info(s"Pre-populated Demo schema: ${demoSchema3Step.name} (id=${demoSchema3Step.id})")

  log.info(s"WorkflowSchemaStoreTest initialized with ${this.all.size} schemas")
}
