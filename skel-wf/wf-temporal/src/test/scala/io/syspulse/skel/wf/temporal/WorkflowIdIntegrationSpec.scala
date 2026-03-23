package io.syspulse.skel.wf.temporal

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll

import io.syspulse.skel.wf.temporal.por2.Por2Schema
import io.hacken.ext.wf.{WorkflowIdGenerator, WorkflowStep}
import io.hacken.ext.detector.DetectorConfig

class WorkflowIdIntegrationSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  "Workflow ID Integration" should {

    "generate correct workflow ID with title containing placeholders" in {
      val tenantId = 1
      val projectId = 2
      val title = "{name}-{project}-{tid}-{ts}"
      val porProject = "demo"

      // Step 1: Create schema with title containing placeholders (simulating por2-start)
      println(s"\n=== Step 1: Create schema with title='$title' ===")
      val schema = Por2Schema.buildSchema(
        schemaId = 1,
        tenantId = tenantId,
        projectId = projectId,
        title = title
      )

      println(s"Schema created: name='${schema.name}', title='${schema.title}'")
      schema.title shouldEqual title
      schema.name shouldEqual "PoR-Flow"

      // Step 2: Generate workflow ID from schema (simulating App.scala)
      println(s"\n=== Step 2: Generate workflow ID from schema ===")
      val context = Map(
        "tid" -> tenantId.toString,
        "pid" -> projectId.toString,
        "project" -> porProject
      )

      val workflowId = WorkflowIdGenerator.generateFromSchema(
        schema = schema,
        context = context,
        defaultTemplate = "por2-{project}-{ts}"
      )

      println(s"Generated workflow ID: '$workflowId'")

      // Verify workflow ID uses title as template
      workflowId should include("PoR-Flow")    // {name}
      workflowId should include("demo")        // {project}
      workflowId should include("1")           // {tid}
      workflowId should not contain "{"
      workflowId should not contain "}"
      workflowId should startWith("PoR-Flow-demo-1-")

      println(s"✅ Workflow ID correctly generated from title template")
    }

    "use title as-is when title has no placeholders" in {
      val tenantId = 5
      val projectId = 10
      val title = "custom-workflow-id"
      val porProject = "testProject"

      // Step 1: Create schema with title (no placeholders)
      println(s"\n=== Step 1: Create schema with title='$title' ===")
      val schema = Por2Schema.buildSchema(
        schemaId = 1,
        tenantId = tenantId,
        projectId = projectId,
        title = title
      )

      println(s"Schema created: name='${schema.name}', title='${schema.title}'")
      schema.title shouldEqual title

      // Step 2: Generate workflow ID from schema
      println(s"\n=== Step 2: Generate workflow ID from schema ===")
      val context = Map(
        "tid" -> tenantId.toString,
        "pid" -> projectId.toString,
        "project" -> porProject
      )

      val workflowId = WorkflowIdGenerator.generateFromSchema(
        schema = schema,
        context = context,
        defaultTemplate = "por2-{project}-{ts}"  // Ignored - not used anymore
      )

      println(s"Generated workflow ID: '$workflowId'")

      // Verify workflow ID uses title as-is
      workflowId shouldEqual title
      workflowId shouldEqual "custom-workflow-id"

      println(s"✅ Workflow ID correctly generated from title (used as-is)")
    }

    "generate workflow ID for flow-1 with all steps" in {
      val tenantId = 1
      val projectId = 2
      val title = "{name}-flow1-{tid}-{pid}-{ts}"
      val porProject = "demo"
      val flow = "flow-1"

      println(s"\n=== Full Flow-1 Integration Test ===")

      // Step 1: Create schema
      println(s"\nStep 1: Create schema with title='$title'")
      val schema = Por2Schema.buildSchema(
        schemaId = 1,
        tenantId = tenantId,
        projectId = projectId,
        title = title
      )
      println(s"  Schema: name='${schema.name}', title='${schema.title}'")

      // Step 2: Get flow steps
      println(s"\nStep 2: Get flow-1 steps")
      val allConfigs = Por2Schema.buildStepConfigs(tenantId = tenantId, projectId = projectId)
      val flowStepIds = Por2Schema.getFlowSteps(flow)
      val configs = allConfigs.filter(c => flowStepIds.contains(c.id))

      println(s"  Flow $flow includes ${configs.size} steps: ${configs.map(_.name).mkString(" -> ")}")
      configs.size shouldEqual 6  // flow-1 has all 6 steps

      // Step 3: Build workflow steps
      println(s"\nStep 3: Build workflow steps")
      val workflowSteps = configs.map { c =>
        WorkflowStep(
          id = c.id,
          name = c.name,
          typ = DetectorConfig.getString(c, "type", "AUTO")
        )
      }
      println(s"  Workflow steps: ${workflowSteps.map(s => s"${s.id}:${s.name}").mkString(", ")}")

      // Step 4: Generate workflow ID
      println(s"\nStep 4: Generate workflow ID")
      val context = Map(
        "tid" -> tenantId.toString,
        "pid" -> projectId.toString,
        "project" -> porProject
      )

      val workflowId = WorkflowIdGenerator.generateFromSchema(
        schema = schema,
        context = context,
        defaultTemplate = "por2-{project}-{ts}"
      )

      println(s"  Generated workflow ID: '$workflowId'")

      // Verify
      workflowId should include("PoR-Flow")
      workflowId should include("flow1")
      workflowId should include("1")  // tid
      workflowId should include("2")  // pid
      // Note: template doesn't have {project}, so "demo" won't be in ID
      workflowId should not contain "{"
      workflowId should not contain "}"
      workflowId should startWith("PoR-Flow-flow1-1-2-")

      println(s"\n✅ Full flow-1 integration test passed!")
      println(s"✅ Title was correctly used as workflow ID template")
      println(s"✅ All 6 steps configured correctly")
    }

    "verify the exact por2-start command line behavior" in {
      println(s"\n=== Simulating: ./run.sh por2-start flow-1 1 2 \"{name}-{project}-{tid}-{ts}\" ===")

      // Parse command line arguments (simulating App.scala)
      val params = List("flow-1", "1", "2", "{name}-{project}-{tid}-{ts}")

      val (flow, tenantId, projectId, title) = params match {
        case f :: tid :: pid :: titleParam :: Nil if f.startsWith("flow-") =>
          (f, tid.toInt, pid.toInt, titleParam)
        case _ => fail("Parameter parsing failed")
      }

      println(s"\nParsed parameters:")
      println(s"  flow=$flow, tenantId=$tenantId, projectId=$projectId, title='$title'")

      // Create schema (simulating App.scala)
      val schema = Por2Schema.buildSchema(
        schemaId = 1,
        tenantId = tenantId,
        projectId = projectId,
        title = title
      )

      println(s"\nSchema created:")
      println(s"  name='${schema.name}'")
      println(s"  title='${schema.title}'")
      println(s"  Title contains placeholders: ${schema.title.contains("{") && schema.title.contains("}")}")

      // Generate workflow ID (simulating App.scala)
      val context = Map(
        "tid" -> tenantId.toString,
        "pid" -> projectId.toString,
        "project" -> "demo"  // from config.porProject
      )

      val workflowId = WorkflowIdGenerator.generateFromSchema(
        schema = schema,
        context = context,
        defaultTemplate = "por2-{project}-{ts}"
      )

      println(s"\nWorkflow ID generated:")
      println(s"  '$workflowId'")

      // Assertions
      schema.title shouldEqual "{name}-{project}-{tid}-{ts}"
      workflowId should startWith("PoR-Flow-demo-1-")
      workflowId should not contain "{"

      println(s"\n✅ Command line simulation successful!")
      println(s"✅ Title '{name}-{project}-{tid}-{ts}' was correctly used")
      println(s"✅ Workflow ID: $workflowId")
    }
  }
}
