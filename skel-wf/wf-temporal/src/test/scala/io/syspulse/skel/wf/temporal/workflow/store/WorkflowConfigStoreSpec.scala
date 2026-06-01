package io.syspulse.skel.wf.temporal.workflow.store

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import scala.util.{Success, Failure}
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import spray.json._

import io.hacken.ext.detector.{DetectorConfig, DetectorConfigContract}

class WorkflowConfigStoreSpec extends AnyWordSpec with Matchers {

  val timeout = 5.seconds

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
      Await.result(store.+(config1), timeout) shouldBe config1

      // Retrieve by ID
      store.??(1) shouldBe Some(config1)
    }

    "update existing configs" in {
      val store = new WorkflowConfigStoreMem()

      val config = createTestConfig(1, "ProofOfOwnership")
      Await.result(store.+(config), timeout)

      // Update config
      val updatedConfig = config.copy(status = "DISABLED")
      Await.result(store.+(updatedConfig), timeout) shouldBe updatedConfig

      // Verify update
      store.??(1) shouldBe Some(updatedConfig)
      store.??(1).map(_.status) shouldBe Some("DISABLED")
    }

    "delete configs" in {
      val store = new WorkflowConfigStoreMem()

      val config = createTestConfig(1, "ProofOfOwnership")

      Await.result(store.+(config), timeout)
      store.??(1) should not be None

      // Delete
      Await.result(store.del(1), timeout) shouldBe 1
      store.??(1) shouldBe None
    }

    "return all configs" in {
      val store = new WorkflowConfigStoreMem()

      val config1 = createTestConfig(1, "ProofOfOwnership")
      val config2 = createTestConfig(2, "ProofOfReserve")
      val config3 = createTestConfig(3, "ProofOfLiability")

      Await.result(store.+(config1), timeout)
      Await.result(store.+(config2), timeout)
      Await.result(store.+(config3), timeout)

      val all = Await.result(store.all, timeout)
      all.size shouldBe 3
      all should contain allOf (config1, config2, config3)
    }

    "return correct size" in {
      val store = new WorkflowConfigStoreMem()

      Await.result(store.size, timeout) shouldBe 0

      Await.result(store.+(createTestConfig(1, "Step1")), timeout)
      Await.result(store.size, timeout) shouldBe 1

      Await.result(store.+(createTestConfig(2, "Step2")), timeout)
      Await.result(store.size, timeout) shouldBe 2

      Await.result(store.del(1), timeout)
      Await.result(store.size, timeout) shouldBe 1
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

      Await.result(store.+(configWithOutput), timeout) shouldBe configWithOutput

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
        Await.result(store.+(config), timeout) shouldBe config

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
