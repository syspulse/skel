package io.syspulse.skel.wf.temporal

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import io.syspulse.skel.wf.temporal.por2.Por2Schema
import io.syspulse.skel.wf.temporal.demo.DemoSchema

/**
 * Tests for non-sequential ID offsets in schemas and configs
 *
 * Verifies that:
 * - Por2: DetectorSchema IDs start from 1000, DetectorConfig IDs start from 100
 * - Demo: DetectorSchema IDs start from 2000, DetectorConfig IDs start from 200
 * - No ID overlap between different workflow types
 */
class SchemaIdOffsetSpec extends AnyWordSpec with Matchers {

  "Por2Schema" should {

    "use offset 1000 for DetectorSchema IDs" in {
      val schema = Por2Schema.buildSchema()

      val schemaIds = schema.nodes.map(_.schema.id).sorted
      schemaIds shouldBe Seq(1000, 1001, 1002, 1003, 1004, 1005)
    }

    "use offset 100 for DetectorConfig IDs" in {
      val configs = Por2Schema.buildStepConfigs()

      val configIds = configs.map(_.id).sorted
      configIds shouldBe Seq(100, 101, 102, 103, 104, 105)
    }

    "use correct schema IDs in connections" in {
      val schema = Por2Schema.buildSchema()

      // First connection: PoO (1000) -> PoR (1001)
      schema.connections.head.from shouldBe 1000
      schema.connections.head.to shouldBe 1001

      // Last connection: Report (1004) -> Commit (1005)
      schema.connections.last.from shouldBe 1004
      schema.connections.last.to shouldBe 1005
    }

    "return config IDs with offset 100 for all flows" in {
      Por2Schema.getFlowSteps("flow-1") shouldBe Seq(100, 101, 102, 103, 104, 105)
      Por2Schema.getFlowSteps("flow-2") shouldBe Seq(101, 102, 103, 104, 105)
      Por2Schema.getFlowSteps("flow-3") shouldBe Seq(101, 104, 105)
      Por2Schema.getFlowSteps("flow-4") shouldBe Seq(100, 101, 104, 105)
      Por2Schema.getFlowSteps("flow-5") shouldBe Seq(102)
    }
  }

  "DemoSchema" should {

    "use offset 2000 for DetectorSchema IDs in 2-step flow" in {
      val schema = DemoSchema.buildSchemaFromFlow("auto -> human")

      val schemaIds = schema.nodes.map(_.schema.id).sorted
      schemaIds shouldBe Seq(2000, 2001)
    }

    "use offset 200 for DetectorConfig IDs in 2-step flow" in {
      val configs = DemoSchema.buildStepConfigs(Seq("auto", "human"))

      val configIds = configs.map(_.id).sorted
      configIds shouldBe Seq(200, 201)
    }

    "use offset 2000 for DetectorSchema IDs in 5-step flow" in {
      val schema = DemoSchema.buildSchemaFromFlow("auto -> human -> auto -> human -> auto")

      val schemaIds = schema.nodes.map(_.schema.id).sorted
      schemaIds shouldBe Seq(2000, 2001, 2002, 2003, 2004)
    }

    "use offset 200 for DetectorConfig IDs in 5-step flow" in {
      val configs = DemoSchema.buildStepConfigs(Seq("auto", "human", "auto", "human", "auto"))

      val configIds = configs.map(_.id).sorted
      configIds shouldBe Seq(200, 201, 202, 203, 204)
    }

    "use correct schema IDs in connections for multi-step flow" in {
      val schema = DemoSchema.buildSchemaFromFlow("auto -> human -> auto")

      // First connection: auto (2000) -> human (2001)
      schema.connections.head.from shouldBe 2000
      schema.connections.head.to shouldBe 2001

      // Second connection: human (2001) -> auto (2002)
      schema.connections.last.from shouldBe 2001
      schema.connections.last.to shouldBe 2002
    }
  }

  "ID Isolation" should {

    "ensure Por2 and Demo schema IDs don't overlap" in {
      val por2Schema = Por2Schema.buildSchema()
      val demoSchema = DemoSchema.buildSchemaFromFlow("auto -> human -> auto -> human -> auto -> human")

      val por2SchemaIds = por2Schema.nodes.map(_.schema.id).toSet
      val demoSchemaIds = demoSchema.nodes.map(_.schema.id).toSet

      // Verify no overlap
      por2SchemaIds.intersect(demoSchemaIds) shouldBe empty

      // Verify Por2 range: 1000-1005
      por2SchemaIds.min shouldBe 1000
      por2SchemaIds.max shouldBe 1005

      // Verify Demo range starts at 2000
      demoSchemaIds.min shouldBe 2000
    }

    "ensure Por2 and Demo config IDs don't overlap" in {
      val por2Configs = Por2Schema.buildStepConfigs()
      val demoConfigs = DemoSchema.buildStepConfigs(Seq("auto", "human", "auto", "human", "auto", "human"))

      val por2ConfigIds = por2Configs.map(_.id).toSet
      val demoConfigIds = demoConfigs.map(_.id).toSet

      // Verify no overlap
      por2ConfigIds.intersect(demoConfigIds) shouldBe empty

      // Verify Por2 range: 100-105
      por2ConfigIds.min shouldBe 100
      por2ConfigIds.max shouldBe 105

      // Verify Demo range starts at 200
      demoConfigIds.min shouldBe 200
    }

    "use realistic non-sequential IDs" in {
      val por2Configs = Por2Schema.buildStepConfigs()
      val demoConfigs = DemoSchema.buildStepConfigs(Seq("auto", "human"))

      // Verify IDs are not 1, 2, 3...
      por2Configs.head.id should not be 1
      por2Configs.head.id shouldBe 100

      demoConfigs.head.id should not be 1
      demoConfigs.head.id shouldBe 200
    }
  }

  "Multi-Step Flow Filtering" should {

    "filter Por2 configs correctly for flow-3" in {
      val allConfigs = Por2Schema.buildStepConfigs()
      val flowStepIds = Por2Schema.getFlowSteps("flow-3")
      val flowConfigs = allConfigs.filter(c => flowStepIds.contains(c.id))

      flowConfigs.map(_.id) shouldBe Seq(101, 104, 105)
      flowConfigs.map(_.name) shouldBe Seq("ProofOfReserve", "Report", "Commit")
    }

    "filter Por2 configs correctly for flow-5" in {
      val allConfigs = Por2Schema.buildStepConfigs()
      val flowStepIds = Por2Schema.getFlowSteps("flow-5")
      val flowConfigs = allConfigs.filter(c => flowStepIds.contains(c.id))

      flowConfigs.map(_.id) shouldBe Seq(102)
      flowConfigs.map(_.name) shouldBe Seq("ProofOfLiability")
    }
  }

  "Schema and Config Consistency" should {

    "have matching counts for Por2" in {
      val schema = Por2Schema.buildSchema()
      val configs = Por2Schema.buildStepConfigs()

      schema.nodes.size shouldBe 6
      configs.size shouldBe 6
    }

    "have matching counts for Demo 3-step flow" in {
      val flowStr = "auto -> human -> auto"
      val schema = DemoSchema.buildSchemaFromFlow(flowStr)
      val stepNames = flowStr.split("->").map(_.trim).toSeq
      val configs = DemoSchema.buildStepConfigs(stepNames)

      schema.nodes.size shouldBe 3
      configs.size shouldBe 3
    }

    "have correct activity names for Por2" in {
      val configs = Por2Schema.buildStepConfigs()

      configs.map(_.name) shouldBe Seq(
        "ProofOfOwnership",
        "ProofOfReserve",
        "ProofOfLiability",
        "Solvency",
        "Report",
        "Commit"
      )
    }

    "have correct activity names for Demo" in {
      val configs = DemoSchema.buildStepConfigs(Seq("auto", "human", "auto"))

      configs.map(_.name) shouldBe Seq("StepAuto", "StepHuman", "StepAuto")
    }
  }
}
