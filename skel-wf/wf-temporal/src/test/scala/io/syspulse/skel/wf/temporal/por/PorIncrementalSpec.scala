package io.syspulse.skel.wf.temporal.por

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import io.syspulse.skel.wf.temporal.ScalaDataConverter
import java.util.UUID

/**
 * Tests for incremental workflow execution (diff/merge functionality)
 */
class PorIncrementalSpec extends AnyWordSpec with Matchers {

  val dataConverter = ScalaDataConverter.create()

  "PorWorkflowInput incremental scenarios" should {

    "support new run with only inputs" in {
      val input = PorWorkflowInput(
        poo = Some(StepDef(input = Some(PooInput(List.empty, "signature")))),
        por = Some(StepDef(input = Some(PorInput(List.empty, List("BTC", "ETH"))))),
        pol = Some(StepDef(input = Some(PolInput("/tmp/test.json", true, Map[String, Any]("signalMode" -> "simulate"))))),
        solvency = Some(StepDef()),
        report = Some(StepDef()),
        commit = Some(StepDef())
      )

      // All inputs defined
      input.poo.flatMap(_.input).isDefined should be (true)
      input.por.flatMap(_.input).isDefined should be (true)
      input.pol.flatMap(_.input).isDefined should be (true)
    }

    "support incremental run with mixed inputs and outputs" in {
      val previousPooOutput = PooOutput(
        ts = 1234567890L,
        proofs = Map("0x1234" -> "0xabcd", "0x5678" -> "0xef01")
      )

      val previousPorOutput = PorOutput(
        ts = 1234567890L,
        balances = List(
          WalletWithAsset("0x1234", "Ethereum", "ETH", BigInt("100000000000000000000"))
        )
      )

      // Create run with previous outputs and new PoL input
      val run = PorWorkflowRun(
        ownerName = "Exchange1",
        ts0 = System.currentTimeMillis(),
        ts1 = System.currentTimeMillis(),
        input = PorWorkflowInput(
          poo = Some(StepDef()),  // No new PoO input
          por = Some(StepDef()),  // No new PoR input
          pol = Some(StepDef(input = Some(PolInput("/tmp/updated-liabilities.json", true, Map[String, Any]("signalMode" -> "simulate"))))), // New PoL
          solvency = Some(StepDef()),
          report = Some(StepDef()),
          commit = Some(StepDef())
        ),
        output = PorWorkflowOutput(
          poo = Some(previousPooOutput), // Reuse previous PoO
          por = Some(previousPorOutput)  // Reuse previous PoR
        )
      )

      // PoO and PoR have outputs (reused), PoL has input (executed)
      run.input.poo.flatMap(_.input).isDefined should be (false)
      run.output.poo.isDefined should be (true)
      run.input.por.flatMap(_.input).isDefined should be (false)
      run.output.por.isDefined should be (true)
      run.input.pol.flatMap(_.input).isDefined should be (true)
      run.output.pol.isDefined should be (false)

      // Verify reused outputs have correct data
      run.output.poo.get.proofs should have size 2
      run.output.por.get.balances should have size 1
    }

    "support pure reuse with only outputs" in {
      val previousPooOutput = PooOutput(
        ts = 1234567890L,
        proofs = Map("0x1234" -> "0xabcd")
      )

      val previousPorOutput = PorOutput(
        ts = 1234567890L,
        balances = List(
          WalletWithAsset("0x1234", "Ethereum", "ETH", BigInt("100000000000000000000"))
        )
      )

      val previousPolOutput = PolOutput(
        ts = 1234567890L,
        liabilities = List(
          Liability(UUID.randomUUID(), "ETH", BigInt("50000000000000000000"))
        ),
        signature = "0xdeadbeef",
        signatureType = "public_key",
        publicKey = "0xcafebabe"
      )

      // Create run with only previous outputs (no inputs)
      val run = PorWorkflowRun(
        ownerName = "Exchange1",
        ts0 = System.currentTimeMillis(),
        input = PorWorkflowInput(
          poo = Some(StepDef()),  // No input
          por = Some(StepDef()),  // No input
          pol = Some(StepDef()),  // No input
          solvency = Some(StepDef()),
          report = Some(StepDef()),
          commit = Some(StepDef())
        ),
        output = PorWorkflowOutput(
          poo = Some(previousPooOutput), // Reuse
          por = Some(previousPorOutput), // Reuse
          pol = Some(previousPolOutput)  // Reuse
        )
      )

      // All steps have outputs (reused), no inputs
      run.input.poo.flatMap(_.input).isDefined should be (false)
      run.output.poo.isDefined should be (true)
      run.input.por.flatMap(_.input).isDefined should be (false)
      run.output.por.isDefined should be (true)
      run.input.pol.flatMap(_.input).isDefined should be (false)
      run.output.pol.isDefined should be (true)

      // Verify all outputs are present
      run.output.poo.get.proofs should have size 1
      run.output.por.get.balances should have size 1
      run.output.pol.get.liabilities should have size 1
    }

    "serialize and deserialize incremental workflow run" in {
      val previousPorOutput = PorOutput(
        ts = 1234567890L,
        balances = List(
          WalletWithAsset("0x1234", "Ethereum", "ETH", BigInt("100000000000000000000"))
        )
      )

      val run = PorWorkflowRun(
        ownerName = "Exchange1",
        ts0 = System.currentTimeMillis(),
        input = PorWorkflowInput(
          poo = Some(StepDef(input = Some(PooInput(List.empty, "signature")))),
          por = Some(StepDef()),  // No input, will use previous output
          pol = Some(StepDef(input = Some(PolInput("/tmp/test.json", true, Map[String, Any]("signalMode" -> "simulate"))))),
          solvency = Some(StepDef()),
          report = Some(StepDef()),
          commit = Some(StepDef())
        ),
        output = PorWorkflowOutput(
          por = Some(previousPorOutput) // Previous output for reuse
        )
      )

      val payload = dataConverter.toPayload(run).get()
      val deserialized = dataConverter.fromPayload(payload, classOf[PorWorkflowRun], classOf[PorWorkflowRun])

      deserialized.input.poo.flatMap(_.input).isDefined should be (true)
      deserialized.output.por.isDefined should be (true)
      deserialized.output.por.get.balances should have size 1
    }
  }

  "PorWorkflowOutput for incremental scenarios" should {

    "represent full execution output" in {
      val output = PorWorkflowOutput(
        poo = Some(PooOutput(System.currentTimeMillis(), Map("0x1" -> "0xa"))),
        por = Some(PorOutput(System.currentTimeMillis(), List.empty)),
        pol = Some(PolOutput(System.currentTimeMillis(), List.empty, "0x", "public_key", "0x")),
        solvency = Some(SolvencyOutput(BigDecimal(1000), BigDecimal(900), BigDecimal(0.9))),
        report = Some(ReportOutput("/tmp/report.md", "https://example.com/report"))
      )

      // All steps completed
      output.poo.isDefined should be (true)
      output.por.isDefined should be (true)
      output.pol.isDefined should be (true)
      output.solvency.isDefined should be (true)
      output.report.isDefined should be (true)
    }

    "represent partial execution output" in {
      val output = PorWorkflowOutput(
        poo = None, // Skipped
        por = Some(PorOutput(System.currentTimeMillis(), List.empty)),
        pol = Some(PolOutput(System.currentTimeMillis(), List.empty, "0x", "public_key", "0x")),
        solvency = Some(SolvencyOutput(BigDecimal(1000), BigDecimal(900), BigDecimal(0.9))),
        report = Some(ReportOutput("/tmp/report.md", "https://example.com/report"))
      )

      // PoO skipped, others completed
      output.poo.isDefined should be (false)
      output.por.isDefined should be (true)
      output.pol.isDefined should be (true)
      output.solvency.isDefined should be (true)
      output.report.isDefined should be (true)
    }

    "be reusable as input for next run" in {
      // Simulate first run output
      val firstRunOutput = PorWorkflowOutput(
        poo = Some(PooOutput(1234567890L, Map("0x1" -> "0xa"))),
        por = Some(PorOutput(1234567890L, List(
          WalletWithAsset("0x1", "Ethereum", "ETH", BigInt("100000000000000000000"))
        ))),
        pol = Some(PolOutput(1234567890L, List(
          Liability(UUID.randomUUID(), "ETH", BigInt("50000000000000000000"))
        ), "0xsig", "public_key", "0xkey")),
        solvency = Some(SolvencyOutput(BigDecimal(1000), BigDecimal(900), BigDecimal(0.9))),
        report = Some(ReportOutput("/tmp/report1.md", "https://example.com/report1")),
        commit = None
      )

      // Create second run reusing first run outputs
      val secondRun = PorWorkflowRun(
        ownerName = "Exchange1",
        ts0 = System.currentTimeMillis(),
        input = PorWorkflowInput(
          poo = Some(StepDef()),  // No new PoO input, will use previous output
          por = Some(StepDef()),  // No new PoR input, will use previous output
          pol = Some(StepDef(input = Some(PolInput("/tmp/updated-liabilities.json", true, Map[String, Any]("signalMode" -> "simulate"))))), // New PoL
          solvency = Some(StepDef()),
          report = Some(StepDef()),
          commit = Some(StepDef())
        ),
        output = PorWorkflowOutput(
          poo = firstRunOutput.poo, // Reuse PoO
          por = firstRunOutput.por  // Reuse PoR
        )
      )

      // Verify outputs are reused
      secondRun.output.poo.isDefined should be (true)
      secondRun.output.por.isDefined should be (true)
      secondRun.output.poo.get.proofs should have size 1
      secondRun.output.por.get.balances should have size 1

      // New PoL will be executed
      secondRun.input.pol.flatMap(_.input).isDefined should be (true)
      secondRun.output.pol.isDefined should be (false)
    }

    "serialize complete workflow output for persistence" in {
      val output = PorWorkflowOutput(
        poo = Some(PooOutput(1234567890L, Map("0x1" -> "0xa", "0x2" -> "0xb"))),
        por = Some(PorOutput(1234567890L, List(
          WalletWithAsset("0x1", "Ethereum", "ETH", BigInt("100000000000000000000")),
          WalletWithAsset("0x2", "Bitcoin", "BTC", BigInt("200000000"))
        ))),
        pol = Some(PolOutput(1234567890L, List(
          Liability(UUID.randomUUID(), "ETH", BigInt("50000000000000000000")),
          Liability(UUID.randomUUID(), "BTC", BigInt("100000000"))
        ), "0xsig", "public_key", "0xkey")),
        solvency = Some(SolvencyOutput(BigDecimal(1500000), BigDecimal(1200000), BigDecimal(0.8))),
        report = Some(ReportOutput("/tmp/report.md", "https://example.com/report"))
      )

      val payload = dataConverter.toPayload(output).get()
      val deserialized = dataConverter.fromPayload(payload, classOf[PorWorkflowOutput], classOf[PorWorkflowOutput])

      // Verify all data is preserved
      deserialized.poo.get.proofs should have size 2
      deserialized.por.get.balances should have size 2
      deserialized.pol.get.liabilities should have size 2
      deserialized.solvency.get.solvencyRatio should === (BigDecimal(0.8))
      deserialized.report.get.reportFilePath should === ("/tmp/report.md")
    }
  }
}
