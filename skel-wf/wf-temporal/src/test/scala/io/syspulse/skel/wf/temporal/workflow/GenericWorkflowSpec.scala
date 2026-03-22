package io.syspulse.skel.wf.temporal.workflow

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import spray.json._

import io.hacken.ext.wf.{WorkflowRun, WorkflowStep, WorkflowSchema, WorkflowSchemaNode, WorkflowSchemaConnection}
import io.hacken.ext.detector.{DetectorConfig, DetectorConfigContract, DetectorConfigSchema}
import io.syspulse.skel.wf.temporal.workflow.activity.GenericActivitiesImpl
import io.syspulse.skel.wf.temporal.workflow.store._

import scala.util.Success

/**
 * Test suite for Generic Workflow logic
 */
class GenericWorkflowSpec extends AnyWordSpec with Matchers {

  "GenericActivitiesImpl" should {

    "execute generic activity successfully" in {
      val schemaStore = new WorkflowSchemaStoreMem()
      val runStore = new WorkflowRunStoreMem()
      val configStore = new WorkflowConfigStoreMem()

      val activities = new GenericActivitiesImpl(schemaStore, runStore, configStore)

      val contract = DetectorConfigContract(1, 0L, 0L, 1, 1, None, None, None, None, "Test")

      val config = DetectorConfig(
        id = 1,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
        status = "ACTIVE",
        contract = contract,
        schema = None,
        name = "GenericActivity",
        source = "TEST",
        tags = Seq(),
        config = Some(JsObject(
          "type" -> JsString("AUTO"),
          "data" -> JsObject("key" -> JsString("value"))
        )),
        destinations = Seq()
      )

      configStore.+(config)

      val resultId = activities.executeActivity(1)
      resultId shouldBe 1

      // Verify output added by fetching from store
      val updatedConfig = configStore.??(1).get
      updatedConfig.config should not be None
      val output = updatedConfig.config.get.fields.get("output")
      output should not be None

      val outputObj = output.get.asJsObject
      outputObj.fields should contain key "activity"
      outputObj.fields should contain key "executed"
      outputObj.fields("activity") shouldBe JsString("GenericActivity")
      outputObj.fields("executed") shouldBe JsBoolean(true)
    }

    "execute any activity as generic" in {
      val schemaStore = new WorkflowSchemaStoreMem()
      val runStore = new WorkflowRunStoreMem()
      val configStore = new WorkflowConfigStoreMem()

      val activities = new GenericActivitiesImpl(schemaStore, runStore, configStore)

      val contract = DetectorConfigContract(1, 0L, 0L, 1, 1, None, None, None, None, "Test")

      val config = DetectorConfig(
        id = 1,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
        status = "ACTIVE",
        contract = contract,
        schema = None,
        name = "SomeCustomActivity",
        source = "TEST",
        tags = Seq(),
        config = Some(JsObject("type" -> JsString("AUTO"))),
        destinations = Seq()
      )

      configStore.+(config)

      val resultId = activities.executeActivity(1)
      resultId shouldBe 1

      // Verify output added by fetching from store
      val updatedConfig = configStore.??(1).get
      updatedConfig.config should not be None
      val output = updatedConfig.config.get.fields.get("output")
      output should not be None

      val outputObj = output.get.asJsObject
      outputObj.fields should contain key "activity"
      outputObj.fields should contain key "executed"
      outputObj.fields("activity") shouldBe JsString("SomeCustomActivity")
      outputObj.fields("executed") shouldBe JsBoolean(true)
    }
  }

  "WorkflowRun status lifecycle" should {

    val testSteps = Seq(
      WorkflowStep(1, "Step1", "AUTO"),
      WorkflowStep(2, "Step2", "AUTO"),
      WorkflowStep(3, "Step3", "WAIT")
    )

    "transition from NEW to RUNNING to FINISHED" in {
      var run = WorkflowRun(
        wid = "workflow-1",
        rid = Some("run-1"),
        status = "NEW",
        cursor = -1,
        schema = 1,
        steps = testSteps
      )

      run.status shouldBe "NEW"
      run.cursor shouldBe -1

      // Start execution
      run = run.copy(status = "RUNNING", cursor = 1)
      run.status shouldBe "RUNNING"
      run.cursor shouldBe 1

      // Move to next step
      run = run.copy(cursor = 2)
      run.cursor shouldBe 2

      // Finish
      run = run.copy(status = "FINISHED", cursor = 3)
      run.status shouldBe "FINISHED"
    }

    "support WAITING status" in {
      var run = WorkflowRun(
        wid = "workflow-1",
        rid = Some("run-1"),
        status = "RUNNING",
        cursor = 2,
        schema = 1,
        steps = testSteps
      )

      // Step requires waiting
      run = run.copy(status = "WAITING")
      run.status shouldBe "WAITING"
      run.cursor shouldBe 2

      // Continue after signal
      run = run.copy(status = "RUNNING")
      run.status shouldBe "RUNNING"
    }

    "support FAILED status" in {
      var run = WorkflowRun(
        wid = "workflow-1",
        rid = Some("run-1"),
        status = "RUNNING",
        cursor = 2,
        schema = 1,
        steps = testSteps
      )

      // Activity failed
      run = run.copy(status = "FAILED")
      run.status shouldBe "FAILED"
    }

    "support STOPPED status" in {
      var run = WorkflowRun(
        wid = "workflow-1",
        rid = Some("run-1"),
        status = "RUNNING",
        cursor = 2,
        schema = 1,
        steps = testSteps
      )

      // User stopped workflow
      run = run.copy(status = "STOPPED")
      run.status shouldBe "STOPPED"
    }
  }
}
