package io.syspulse.skel.wf.temporal.workflow.store

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import scala.util.{Success, Failure}
import spray.json._

import io.hacken.ext.detector.{DetectorConfig, DetectorConfigContract}

/**
 * Test suite for WorkflowConfigStore (DetectorConfig store)
 */
class WorkflowConfigStoreSpec extends AnyWordSpec with Matchers {

  def createTestConfig(id: Int, name: String): DetectorConfig = {
    val contract = DetectorConfigContract(
      id = id,
      createdAt = System.currentTimeMillis(),
      updatedAt = System.currentTimeMillis(),
      projectId = 1,
      tenantId = 1,
      chainUid = None,
      proxyAddress = None,
      implementation = None,
      address = None,
      name = s"Contract-${name}"
    )

    DetectorConfig(
      id = id,
      createdAt = System.currentTimeMillis(),
      updatedAt = System.currentTimeMillis(),
      status = "ACTIVE",
      contract = contract,
      schema = None,
      name = name,
      source = "TEST",
      tags = Seq(),
      config = Some(JsObject("type" -> JsString("AUTO"))),
      destinations = Seq()
    )
  }

  "WorkflowConfigStoreMem" should {

    "store and retrieve detector configs" in {
      val store = new WorkflowConfigStoreMem()

      val config1 = createTestConfig(1, "ProofOfOwnership")

      // Add config
      store.+(config1) shouldBe Success(config1)

      // Retrieve by ID
      store.??(1) shouldBe Some(config1)
    }

    "update existing configs" in {
      val store = new WorkflowConfigStoreMem()

      val config = createTestConfig(1, "ProofOfOwnership")
      store.+(config)

      // Update config
      val updatedConfig = config.copy(status = "DISABLED")
      store.+(updatedConfig) shouldBe Success(updatedConfig)

      // Verify update
      store.??(1) shouldBe Some(updatedConfig)
      store.??(1).map(_.status) shouldBe Some("DISABLED")
    }

    "delete configs" in {
      val store = new WorkflowConfigStoreMem()

      val config = createTestConfig(1, "ProofOfOwnership")

      store.+(config)
      store.??(1) should not be None

      // Delete
      store.del(1) shouldBe Success(1)
      store.??(1) shouldBe None
    }

    "return all configs" in {
      val store = new WorkflowConfigStoreMem()

      val config1 = createTestConfig(1, "ProofOfOwnership")
      val config2 = createTestConfig(2, "ProofOfReserve")
      val config3 = createTestConfig(3, "ProofOfLiability")

      store.+(config1)
      store.+(config2)
      store.+(config3)

      val all = store.all
      all.size shouldBe 3
      all should contain allOf (config1, config2, config3)
    }

    "return correct size" in {
      val store = new WorkflowConfigStoreMem()

      store.size shouldBe 0

      store.+(createTestConfig(1, "Step1"))
      store.size shouldBe 1

      store.+(createTestConfig(2, "Step2"))
      store.size shouldBe 2

      store.del(1)
      store.size shouldBe 1
    }

    "store config with output data" in {
      val store = new WorkflowConfigStoreMem()

      val config = createTestConfig(1, "ProofOfOwnership")
      val configWithOutput = config.copy(
        config = Some(JsObject(
          "type" -> JsString("AUTO"),
          "output" -> JsObject(
            "verified" -> JsArray(JsString("0x123"), JsString("0x456")),
            "total" -> JsNumber(2)
          )
        ))
      )

      store.+(configWithOutput) shouldBe Success(configWithOutput)

      // Verify output persisted
      val retrieved = store.??(1)
      retrieved should not be None
      retrieved.get.config should not be None
      retrieved.get.config.get.fields should contain key "output"
    }

    "return failure when retrieving non-existent config" in {
      val store = new WorkflowConfigStoreMem()

      store.??(999) shouldBe None

      store.???(999) match {
        case Failure(e) => e.getMessage should include("workflow config")
        case Success(_) => fail("Should return failure for non-existent config")
      }
    }
  }

  "WorkflowConfigStoreDir" should {

    "persist configs to disk" in {
      // Create temporary directory
      val tempDir = java.nio.file.Files.createTempDirectory("workflow-config-test").toString

      try {
        val store = new WorkflowConfigStoreDir(tempDir)

        val config = createTestConfig(1, "ProofOfOwnership")

        // Add config
        store.+(config) shouldBe Success(config)

        // Verify file exists
        val file = new java.io.File(s"${tempDir}/1.json")
        file.exists() shouldBe true

        // Create new store instance and verify data persisted
        val store2 = new WorkflowConfigStoreDir(tempDir)
        store2.??(1) shouldBe Some(config)

      } finally {
        // Cleanup
        val dir = new java.io.File(tempDir)
        if (dir.exists()) {
          dir.listFiles().foreach(_.delete())
          dir.delete()
        }
      }
    }
  }
}
