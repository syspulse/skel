package io.syspulse.skel.wf.temporal.por

import io.temporal.workflow.Workflow
import io.temporal.activity.ActivityOptions
import java.time.Duration

/**
 * Main PoR Workflow Implementation
 * Supports all 4 flow patterns based on input configuration
 */
class PorWorkflowImpl extends PorWorkflow {

  private val logger = Workflow.getLogger(classOf[PorWorkflowImpl])

  private val activityOptions = ActivityOptions.newBuilder()
    .setStartToCloseTimeout(Duration.ofMinutes(10))
    .build()

  private val activities = Workflow.newActivityStub(classOf[PorActivities], activityOptions)

  override def execute(input: PorWorkflowInput): PorWorkflowOutput = {
    logger.info(s"Starting PoR Workflow for owner: ${input.ownerName}")

    var pooOutput: Option[PooOutput] = None
    var porOutput: Option[PorOutput] = None
    var polOutput: Option[PolOutput] = None
    var solvencyOutput: Option[SolvencyOutput] = None

    // Determine which flow pattern to execute based on input
    val flowPattern = determineFlowPattern(input)
    logger.info(s"Executing flow pattern: $flowPattern")

    def executeFlow1(input: PorWorkflowInput): PorWorkflowOutput = {
      // Flow 1: [PoO] -> [PoR] -> [PoL] -> [Solvency] -> [Report]

      // Step 1: Proof of Ownership
      val wallets = generateMockWallets()
      pooOutput = Some(activities.executeProofOfOwnership(
        PooInput(wallets = wallets, proofType = "signature")
      ))

      // Step 2: Proof of Reserves
      porOutput = Some(activities.executeProofOfReserves(
        PorInput(
          wallets = wallets,
          assets = List("BTC", "ETH", "LINK", "AAVE", "SOL", "TRX")
        )
      ))

      // Step 3: Proof of Liabilities
      polOutput = Some(activities.executeProofOfLiability(
        PolInput(fileLink = "/tmp/liabilities.json", waitForConfirmation = true, signalMode = input.polSignalMode)
      ))

      // Step 4: Solvency
      solvencyOutput = Some(activities.executeSolvency(porOutput.get, polOutput.get))

      // Step 5: Report
      val reportOutput = Some(activities.executeReport(input, pooOutput, porOutput, polOutput, solvencyOutput))

      PorWorkflowOutput(pooOutput, porOutput, polOutput, solvencyOutput, reportOutput)
    }

    def executeFlow2(input: PorWorkflowInput): PorWorkflowOutput = {
      // Flow 2: [PoR] -> [PoL] -> [Solvency] -> [Report]

      val wallets = generateMockWallets()

      // Step 1: Proof of Reserves
      porOutput = Some(activities.executeProofOfReserves(
        PorInput(
          wallets = wallets,
          assets = List("BTC", "ETH", "LINK", "AAVE", "SOL", "TRX")
        )
      ))

      // Step 2: Proof of Liabilities
      polOutput = Some(activities.executeProofOfLiability(
        PolInput(fileLink = "/tmp/liabilities.json", waitForConfirmation = true, signalMode = input.polSignalMode)
      ))

      // Step 3: Solvency
      solvencyOutput = Some(activities.executeSolvency(porOutput.get, polOutput.get))

      // Step 4: Report
      val reportOutput = Some(activities.executeReport(input, pooOutput, porOutput, polOutput, solvencyOutput))

      PorWorkflowOutput(pooOutput, porOutput, polOutput, solvencyOutput, reportOutput)
    }

    def executeFlow3(input: PorWorkflowInput): PorWorkflowOutput = {
      // Flow 3: [PoR] -> [Report]

      val wallets = generateMockWallets()

      // Step 1: Proof of Reserves
      porOutput = Some(activities.executeProofOfReserves(
        PorInput(
          wallets = wallets,
          assets = List("BTC", "ETH", "LINK", "AAVE", "SOL", "TRX")
        )
      ))

      // Step 2: Report
      val reportOutput = Some(activities.executeReport(input, pooOutput, porOutput, polOutput, solvencyOutput))

      PorWorkflowOutput(pooOutput, porOutput, polOutput, solvencyOutput, reportOutput)
    }

    def executeFlow4(input: PorWorkflowInput): PorWorkflowOutput = {
      // Flow 4: [PoO] -> [PoR] -> [Report]

      val wallets = generateMockWallets()

      // Step 1: Proof of Ownership
      pooOutput = Some(activities.executeProofOfOwnership(
        PooInput(wallets = wallets, proofType = "signature")
      ))

      // Step 2: Proof of Reserves
      porOutput = Some(activities.executeProofOfReserves(
        PorInput(
          wallets = wallets,
          assets = List("BTC", "ETH", "LINK", "AAVE", "SOL", "TRX")
        )
      ))

      // Step 3: Report
      val reportOutput = Some(activities.executeReport(input, pooOutput, porOutput, polOutput, solvencyOutput))

      PorWorkflowOutput(pooOutput, porOutput, polOutput, solvencyOutput, reportOutput)
    }

    def executeFlow5(input: PorWorkflowInput): PorWorkflowOutput = {
      // Flow 5: [PoL]

      val wallets = generateMockWallets()

      // Step 1: Proof of Liabilities
      polOutput = Some(activities.executeProofOfLiability(
        PolInput(fileLink = "/tmp/liabilities.json", waitForConfirmation = true, signalMode = input.polSignalMode)
      ))

      // Step 2: Report
      val reportOutput = Some(activities.executeReport(input, pooOutput, porOutput, polOutput, solvencyOutput))

      PorWorkflowOutput(pooOutput, porOutput, polOutput, solvencyOutput, reportOutput)
    }

    // Execute the appropriate flow based on pattern
    flowPattern match {
      case "flow-1" => executeFlow1(input)
      case "flow-2" => executeFlow2(input)
      case "flow-3" => executeFlow3(input)
      case "flow-4" => executeFlow4(input)
      case "flow-5" => executeFlow5(input)
      case _ => throw new IllegalArgumentException(s"Unknown flow pattern: $flowPattern")
    }
  }

  private def determineFlowPattern(input: PorWorkflowInput): String = {
    (input.pooRequired, input.porRequired, input.polRequired, input.reportRequired) match {
      case (true, true, true, true) => "flow-1" // PoO -> PoR -> PoL -> Solvency -> Report
      case (false, true, true, true) => "flow-2" // PoR -> PoL -> Solvency -> Report
      case (false, true, false, true) => "flow-3" // PoR -> Report
      case (true, true, false, true) => "flow-4" // PoO -> PoR -> Report
      case (false, false, true, false) => "flow-5" // PoL
      case _ => throw new IllegalArgumentException(
        s"Invalid flow configuration: pooRequired=${input.pooRequired}, porRequired=${input.porRequired}, " +
        s"polRequired=${input.polRequired}, reportRequired=${input.reportRequired}"
      )
    }
  }

  private def generateMockWallets(): List[Wallet] = {
    import scala.util.Random

    List(
      Wallet("0x1234567890abcdef1234567890abcdef12345678", "Ethereum", BigInt(Random.nextInt(1000)) * BigInt(10).pow(18)),
      Wallet("bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh", "Bitcoin", BigInt(Random.nextInt(100)) * BigInt(10).pow(8)),
      Wallet("0xabcdef1234567890abcdef1234567890abcdef12", "Arbitrum", BigInt(Random.nextInt(500)) * BigInt(10).pow(18)),
      Wallet("9n4NBfQSKMbPDf6xJzewJ5V1Z9v5KnPBkJKHTgV7aoqd", "Solana", BigInt(Random.nextInt(2000)) * BigInt(10).pow(9)),
      Wallet("TXYZupQdGeRgKaFwNTjJFiJNdMdnXBx3K4", "Tron", BigInt(Random.nextInt(10000)) * BigInt(10).pow(6))
    )
  }
}
