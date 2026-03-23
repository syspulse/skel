package io.syspulse.skel.wf.temporal.por2

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import spray.json._

import io.hacken.ext.wf.{WorkflowRun, WorkflowStep}
import io.hacken.ext.detector.{DetectorConfig, DetectorConfigContract}
import io.syspulse.skel.wf.temporal.workflow.store._
import io.syspulse.skel.wf.temporal.workflow.activity.ActivityResult

/**
 * Test suite for PoR2-specific Activities Implementation
 */
class Por2ActivitiesImplSpec extends AnyWordSpec with Matchers {

  "Por2ActivitiesImpl" should {

    "execute Proof of Ownership activity" in {
      val schemaStore = new WorkflowSchemaStoreMem()
      val runStore = new WorkflowRunStoreMem()
      val configStore = new WorkflowConfigStoreMem()

      val activities = new Por2ActivitiesImpl(schemaStore, runStore, configStore)

      val contract = DetectorConfigContract(1, 0L, 0L, 1, 1, None, None, None, None, "Test")

      val config = DetectorConfig(
        id = 1,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
        status = "ACTIVE",
        contract = contract,
        schema = None,
        name = "ProofOfOwnership",
        source = "POR2",
        tags = Seq(),
        config = Some(JsObject(
          "type" -> JsString("AUTO"),
          "wallets" -> JsArray(
            JsObject(
              "address" -> JsString("0x123"),
              "network" -> JsString("ETH"),
              "balance" -> JsString("1000000000000000000")
            )
          )
        )),
        destinations = Seq()
      )

      configStore.+(config)

      val result = activities.executeActivity(1)
      result.configId shouldBe 1
      result.activityName shouldBe "ProofOfOwnership"
      result.status shouldBe "SUCCESS"

      // Verify output added by fetching from store
      val updatedConfig = configStore.??(1).get
      updatedConfig.config should not be None
      val output = updatedConfig.config.get.fields.get("output")
      output should not be None

      val outputObj = output.get.asJsObject
      outputObj.fields should contain key "proofs"
      outputObj.fields should contain key "walletsVerified"
      outputObj.fields("walletsVerified") shouldBe JsNumber(1)
    }

    "execute Proof of Reserve activity" in {
      val schemaStore = new WorkflowSchemaStoreMem()
      val runStore = new WorkflowRunStoreMem()
      val configStore = new WorkflowConfigStoreMem()

      val activities = new Por2ActivitiesImpl(schemaStore, runStore, configStore)

      val contract = DetectorConfigContract(2, 0L, 0L, 1, 1, None, None, None, None, "Test")

      val config = DetectorConfig(
        id = 2,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
        status = "ACTIVE",
        contract = contract,
        schema = None,
        name = "ProofOfReserve",
        source = "POR2",
        tags = Seq(),
        config = Some(JsObject(
          "type" -> JsString("AUTO"),
          "assets" -> JsArray(JsString("ETH"), JsString("BTC"))
        )),
        destinations = Seq()
      )

      configStore.+(config)

      val result = activities.executeActivity(2)
      result.configId shouldBe 2
      result.activityName shouldBe "ProofOfReserve"
      result.status shouldBe "SUCCESS"

      // Verify output added
      val updatedConfig = configStore.??(2).get
      val output = updatedConfig.config.get.fields.get("output").get.asJsObject
      output.fields should contain key "balances"
      output.fields should contain key "totalAssets"
      output.fields("totalAssets") shouldBe JsNumber(2)
    }

    "execute Solvency activity" in {
      val schemaStore = new WorkflowSchemaStoreMem()
      val runStore = new WorkflowRunStoreMem()
      val configStore = new WorkflowConfigStoreMem()

      val activities = new Por2ActivitiesImpl(schemaStore, runStore, configStore)

      val contract = DetectorConfigContract(3, 0L, 0L, 1, 1, None, None, None, None, "Test")

      val config = DetectorConfig(
        id = 3,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
        status = "ACTIVE",
        contract = contract,
        schema = None,
        name = "Solvency",
        source = "POR2",
        tags = Seq(),
        config = Some(JsObject("type" -> JsString("AUTO"))),
        destinations = Seq()
      )

      configStore.+(config)

      val result = activities.executeActivity(3)
      result.configId shouldBe 3
      result.activityName shouldBe "Solvency"
      result.status shouldBe "SUCCESS"

      // Verify output added
      val updatedConfig = configStore.??(3).get
      val output = updatedConfig.config.get.fields.get("output").get.asJsObject
      output.fields should contain key "solvencyRatio"
      output.fields should contain key "isSolvent"
      output.fields should contain key "porTotalUsd"
      output.fields should contain key "polTotalUsd"
    }

    "fall back to generic for unknown PoR activities" in {
      val schemaStore = new WorkflowSchemaStoreMem()
      val runStore = new WorkflowRunStoreMem()
      val configStore = new WorkflowConfigStoreMem()

      val activities = new Por2ActivitiesImpl(schemaStore, runStore, configStore)

      val contract = DetectorConfigContract(4, 0L, 0L, 1, 1, None, None, None, None, "Test")

      val config = DetectorConfig(
        id = 4,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
        status = "ACTIVE",
        contract = contract,
        schema = None,
        name = "UnknownPorActivity",
        source = "POR2",
        tags = Seq(),
        config = Some(JsObject("type" -> JsString("AUTO"))),
        destinations = Seq()
      )

      configStore.+(config)

      val result = activities.executeActivity(4)
      result.configId shouldBe 4
      result.activityName shouldBe "UnknownPorActivity"
      result.status shouldBe "SUCCESS"

      // Verify generic output
      val updatedConfig = configStore.??(4).get
      val output = updatedConfig.config.get.fields.get("output").get.asJsObject
      output.fields should contain key "activity"
      output.fields should contain key "executed"
      output.fields("activity") shouldBe JsString("UnknownPorActivity")
      output.fields("executed") shouldBe JsBoolean(true)
    }
  }
}
