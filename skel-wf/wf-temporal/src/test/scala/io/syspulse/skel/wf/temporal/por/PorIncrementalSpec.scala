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
        ownerName = "Exchange1",
        timestamp = System.currentTimeMillis(),
        pooInput = Some(PooInput(List.empty, "signature")),
        pooOutput = None,
        porInput = Some(PorInput(List.empty, List("BTC", "ETH"))),
        porOutput = None,
        polInput = Some(PolInput("/tmp/test.json", true, "simulate")),
        polOutput = None,
        reportRequired = true,
        polSignalMode = "simulate"
      )

      // All inputs defined, no outputs
      input.pooInput.isDefined should be (true)
      input.pooOutput.isDefined should be (false)
      input.porInput.isDefined should be (true)
      input.porOutput.isDefined should be (false)
      input.polInput.isDefined should be (true)
      input.polOutput.isDefined should be (false)
    }

    "support incremental run with mixed inputs and outputs" in {
      val previousPooOutput = PooOutput(
        timestamp = 1234567890L,
        proofs = Map("0x1234" -> "0xabcd", "0x5678" -> "0xef01")
      )

      val previousPorOutput = PorOutput(
        timestamp = 1234567890L,
        balances = List(
          WalletWithAsset("0x1234", "Ethereum", "ETH", BigInt("100000000000000000000"))
        )
      )

      val input = PorWorkflowInput(
        ownerName = "Exchange1",
        timestamp = System.currentTimeMillis(),
        pooInput = None,
        pooOutput = Some(previousPooOutput), // Reuse previous PoO
        porInput = None,
        porOutput = Some(previousPorOutput), // Reuse previous PoR
        polInput = Some(PolInput("/tmp/updated-liabilities.json", true, "simulate")), // New PoL
        polOutput = None,
        reportRequired = true,
        polSignalMode = "simulate"
      )

      // PoO and PoR have outputs (reused), PoL has input (executed)
      input.pooInput.isDefined should be (false)
      input.pooOutput.isDefined should be (true)
      input.porInput.isDefined should be (false)
      input.porOutput.isDefined should be (true)
      input.polInput.isDefined should be (true)
      input.polOutput.isDefined should be (false)

      // Verify reused outputs have correct data
      input.pooOutput.get.proofs should have size 2
      input.porOutput.get.balances should have size 1
    }

    "support pure reuse with only outputs" in {
      val previousPooOutput = PooOutput(
        timestamp = 1234567890L,
        proofs = Map("0x1234" -> "0xabcd")
      )

      val previousPorOutput = PorOutput(
        timestamp = 1234567890L,
        balances = List(
          WalletWithAsset("0x1234", "Ethereum", "ETH", BigInt("100000000000000000000"))
        )
      )

      val previousPolOutput = PolOutput(
        timestamp = 1234567890L,
        liabilities = List(
          Liability(UUID.randomUUID(), "ETH", BigInt("50000000000000000000"))
        ),
        signature = "0xdeadbeef",
        signatureType = "public_key",
        publicKey = "0xcafebabe"
      )

      val input = PorWorkflowInput(
        ownerName = "Exchange1",
        timestamp = System.currentTimeMillis(),
        pooInput = None,
        pooOutput = Some(previousPooOutput), // Reuse
        porInput = None,
        porOutput = Some(previousPorOutput), // Reuse
        polInput = None,
        polOutput = Some(previousPolOutput), // Reuse
        reportRequired = true,
        polSignalMode = "simulate"
      )

      // All steps have outputs (reused), no inputs
      input.pooInput.isDefined should be (false)
      input.pooOutput.isDefined should be (true)
      input.porInput.isDefined should be (false)
      input.porOutput.isDefined should be (true)
      input.polInput.isDefined should be (false)
      input.polOutput.isDefined should be (true)

      // Verify all outputs are present
      input.pooOutput.get.proofs should have size 1
      input.porOutput.get.balances should have size 1
      input.polOutput.get.liabilities should have size 1
    }

    "serialize and deserialize incremental workflow input" in {
      val previousPorOutput = PorOutput(
        timestamp = 1234567890L,
        balances = List(
          WalletWithAsset("0x1234", "Ethereum", "ETH", BigInt("100000000000000000000"))
        )
      )

      val input = PorWorkflowInput(
        ownerName = "Exchange1",
        timestamp = System.currentTimeMillis(),
        pooInput = Some(PooInput(List.empty, "signature")),
        pooOutput = None,
        porInput = None,
        porOutput = Some(previousPorOutput),
        polInput = Some(PolInput("/tmp/test.json", true, "simulate")),
        polOutput = None,
        reportRequired = true,
        polSignalMode = "simulate"
      )

      val payload = dataConverter.toPayload(input).get()
      val deserialized = dataConverter.fromPayload(payload, classOf[PorWorkflowInput], classOf[PorWorkflowInput])

      deserialized.ownerName should === ("Exchange1")
      deserialized.pooInput.isDefined should be (true)
      deserialized.porOutput.isDefined should be (true)
      deserialized.porOutput.get.balances should have size 1
    }
  }

  "PorWorkflowOutput for incremental scenarios" should {

    "represent full execution output" in {
      val output = PorWorkflowOutput(
        pooOutput = Some(PooOutput(System.currentTimeMillis(), Map("0x1" -> "0xa"))),
        porOutput = Some(PorOutput(System.currentTimeMillis(), List.empty)),
        polOutput = Some(PolOutput(System.currentTimeMillis(), List.empty, "0x", "public_key", "0x")),
        solvencyOutput = Some(SolvencyOutput(BigDecimal(1000), BigDecimal(900), BigDecimal(0.9))),
        reportOutput = Some(ReportOutput("/tmp/report.md", "https://example.com/report"))
      )

      // All steps completed
      output.pooOutput.isDefined should be (true)
      output.porOutput.isDefined should be (true)
      output.polOutput.isDefined should be (true)
      output.solvencyOutput.isDefined should be (true)
      output.reportOutput.isDefined should be (true)
    }

    "represent partial execution output" in {
      val output = PorWorkflowOutput(
        pooOutput = None, // Skipped
        porOutput = Some(PorOutput(System.currentTimeMillis(), List.empty)),
        polOutput = Some(PolOutput(System.currentTimeMillis(), List.empty, "0x", "public_key", "0x")),
        solvencyOutput = Some(SolvencyOutput(BigDecimal(1000), BigDecimal(900), BigDecimal(0.9))),
        reportOutput = Some(ReportOutput("/tmp/report.md", "https://example.com/report"))
      )

      // PoO skipped, others completed
      output.pooOutput.isDefined should be (false)
      output.porOutput.isDefined should be (true)
      output.polOutput.isDefined should be (true)
      output.solvencyOutput.isDefined should be (true)
      output.reportOutput.isDefined should be (true)
    }

    "be reusable as input for next run" in {
      // Simulate first run output
      val firstRunOutput = PorWorkflowOutput(
        pooOutput = Some(PooOutput(1234567890L, Map("0x1" -> "0xa"))),
        porOutput = Some(PorOutput(1234567890L, List(
          WalletWithAsset("0x1", "Ethereum", "ETH", BigInt("100000000000000000000"))
        ))),
        polOutput = Some(PolOutput(1234567890L, List(
          Liability(UUID.randomUUID(), "ETH", BigInt("50000000000000000000"))
        ), "0xsig", "public_key", "0xkey")),
        solvencyOutput = Some(SolvencyOutput(BigDecimal(1000), BigDecimal(900), BigDecimal(0.9))),
        reportOutput = Some(ReportOutput("/tmp/report1.md", "https://example.com/report1"))
      )

      // Create second run input reusing first run outputs
      val secondRunInput = PorWorkflowInput(
        ownerName = "Exchange1",
        timestamp = System.currentTimeMillis(),
        pooInput = None,
        pooOutput = firstRunOutput.pooOutput, // Reuse PoO
        porInput = None,
        porOutput = firstRunOutput.porOutput, // Reuse PoR
        polInput = Some(PolInput("/tmp/updated-liabilities.json", true, "simulate")), // New PoL
        polOutput = None,
        reportRequired = true,
        polSignalMode = "simulate"
      )

      // Verify outputs are reused
      secondRunInput.pooOutput.isDefined should be (true)
      secondRunInput.porOutput.isDefined should be (true)
      secondRunInput.pooOutput.get.proofs should have size 1
      secondRunInput.porOutput.get.balances should have size 1

      // New PoL will be executed
      secondRunInput.polInput.isDefined should be (true)
      secondRunInput.polOutput.isDefined should be (false)
    }

    "serialize complete workflow output for persistence" in {
      val output = PorWorkflowOutput(
        pooOutput = Some(PooOutput(1234567890L, Map("0x1" -> "0xa", "0x2" -> "0xb"))),
        porOutput = Some(PorOutput(1234567890L, List(
          WalletWithAsset("0x1", "Ethereum", "ETH", BigInt("100000000000000000000")),
          WalletWithAsset("0x2", "Bitcoin", "BTC", BigInt("200000000"))
        ))),
        polOutput = Some(PolOutput(1234567890L, List(
          Liability(UUID.randomUUID(), "ETH", BigInt("50000000000000000000")),
          Liability(UUID.randomUUID(), "BTC", BigInt("100000000"))
        ), "0xsig", "public_key", "0xkey")),
        solvencyOutput = Some(SolvencyOutput(BigDecimal(1500000), BigDecimal(1200000), BigDecimal(0.8))),
        reportOutput = Some(ReportOutput("/tmp/report.md", "https://example.com/report"))
      )

      val payload = dataConverter.toPayload(output).get()
      val deserialized = dataConverter.fromPayload(payload, classOf[PorWorkflowOutput], classOf[PorWorkflowOutput])

      // Verify all data is preserved
      deserialized.pooOutput.get.proofs should have size 2
      deserialized.porOutput.get.balances should have size 2
      deserialized.polOutput.get.liabilities should have size 2
      deserialized.solvencyOutput.get.solvencyRatio should === (BigDecimal(0.8))
      deserialized.reportOutput.get.reportFilePath should === ("/tmp/report.md")
    }
  }
}
