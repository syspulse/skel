package io.syspulse.skel.wf.temporal.por.demo

import io.syspulse.skel.wf.temporal.por._
import scala.util.Random

object DemoUtil {

  /**
   * Generate mock wallets for demo purposes
   * Creates wallets across different blockchain networks with random balances
   */
  def generateMockWallets(): List[Wallet] = {
    List(
      Wallet("0x1234567890abcdef1234567890abcdef12345678", "Ethereum", BigInt(Random.nextInt(1000)) * BigInt(10).pow(18)),
      Wallet("bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh", "Bitcoin", BigInt(Random.nextInt(100)) * BigInt(10).pow(8)),
      Wallet("0xabcdef1234567890abcdef1234567890abcdef12", "Arbitrum", BigInt(Random.nextInt(500)) * BigInt(10).pow(18)),
      Wallet("9n4NBfQSKMbPDf6xJzewJ5V1Z9v5KnPBkJKHTgV7aoqd", "Solana", BigInt(Random.nextInt(2000)) * BigInt(10).pow(9)),
      Wallet("TXYZupQdGeRgKaFwNTjJFiJNdMdnXBx3K4", "Tron", BigInt(Random.nextInt(10000)) * BigInt(10).pow(6))
    )
  }

  /**
   * Generate PorWorkflowInput for a specific flow
   *
   * @param flow Flow name (flow-1, flow-2, etc.)
   * @param polSignalMode PoL signal mode (simulate, file, rest)
   * @return PorWorkflowInput configured for the flow
   */
  def generateFlowInput(flow: String, polSignalMode: String = "simulate"): PorWorkflowInput = {
    val mockWallets = generateMockWallets()

    flow match {
      case "flow-1" => // PoO -> PoR -> PoL -> Solvency -> Report
        PorWorkflowInput(
          poo = Some(StepDef(input = Some(PooInput(mockWallets, "signature")))),
          por = Some(StepDef(input = Some(PorInput(mockWallets, List("BTC", "ETH", "LINK", "AAVE", "SOL", "TRX"))))),
          pol = Some(StepDef(input = Some(PolInput(fileLink = Some("/tmp/liabilities.json"), waitForConfirmation = true, config = Map("signalMode" -> polSignalMode))))),
          solvency = Some(StepDef()),
          report = Some(StepDef()),
          commit = Some(StepDef())
        )

      case "flow-2" => // PoR -> PoL -> Solvency -> Report
        PorWorkflowInput(
          poo = None,
          por = Some(StepDef(input = Some(PorInput(mockWallets, List("BTC", "ETH", "LINK", "AAVE", "SOL", "TRX"))))),
          pol = Some(StepDef(input = Some(PolInput(fileLink = Some("/tmp/liabilities.json"), waitForConfirmation = true, config = Map("signalMode" -> polSignalMode))))),
          solvency = Some(StepDef()),
          report = Some(StepDef()),
          commit = Some(StepDef())
        )

      case "flow-3" => // PoR -> Report
        PorWorkflowInput(
          poo = None,
          por = Some(StepDef(input = Some(PorInput(mockWallets, List("BTC", "ETH", "LINK", "AAVE", "SOL", "TRX"))))),
          pol = None,
          solvency = None,
          report = Some(StepDef()),
          commit = Some(StepDef())
        )

      case "flow-4" => // PoO -> PoR -> Report
        PorWorkflowInput(
          poo = Some(StepDef(input = Some(PooInput(mockWallets, "signature")))),
          por = Some(StepDef(input = Some(PorInput(mockWallets, List("BTC", "ETH", "LINK", "AAVE", "SOL", "TRX"))))),
          pol = None,
          solvency = None,
          report = Some(StepDef()),
          commit = Some(StepDef())
        )

      case "flow-5" => // PoL only
        PorWorkflowInput(
          poo = None,
          por = None,
          pol = Some(StepDef(input = Some(PolInput(fileLink = Some("/tmp/liabilities.json"), waitForConfirmation = true, config = Map("signalMode" -> polSignalMode))))),
          solvency = None,
          report = None,
          commit = None
        )

      case _ =>
        throw new IllegalArgumentException(s"Unknown flow: $flow")
    }
  }

  /**
   * Generate complete PorWorkflowRun for a specific flow
   *
   * @param flow Flow name (flow-1, flow-2, etc.)
   * @param proj Project name
   * @param tags Workflow tags
   * @param memo Workflow memo
   * @param polSignalMode PoL signal mode
   * @return PorWorkflowRun ready to execute
   */
  def generateFlowRun(
    flow: String,
    proj: String = "demo",
    tags: Seq[String] = Seq.empty,
    memo: Map[String, String] = Map.empty,
    polSignalMode: String = "simulate"
  ): PorWorkflowRun = {
    PorWorkflowRun(
      proj = proj,
      ts0 = System.currentTimeMillis(),
      ts1 = System.currentTimeMillis(),
      tags = tags,
      memo = memo,
      input = generateFlowInput(flow, polSignalMode),
      output = PorWorkflowOutput()
    )
  }
}
