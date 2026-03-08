package io.syspulse.skel.wf.temporal.por

import io.temporal.workflow.Workflow
import io.temporal.activity.ActivityOptions
import java.time.Duration

/**
 * Main PoR Workflow Implementation
 * Supports incremental runs with input/output diff/merge logic
 */
class PorWorkflowImpl extends PorWorkflow {

  private val logger = Workflow.getLogger(classOf[PorWorkflowImpl])

  private val activityOptions = ActivityOptions.newBuilder()
    .setStartToCloseTimeout(Duration.ofMinutes(10))
    .build()

  private val activities = Workflow.newActivityStub(classOf[PorActivities], activityOptions)

  override def execute(input: PorWorkflowInput): PorWorkflowOutput = {
    logger.info(s"Starting PoR Workflow for owner: ${input.ownerName}")

    // Process each step: check if output exists, else execute input
    val pooOutput = processPoOStep(input)
    val porOutput = processPoRStep(input)
    val polOutput = processPoLStep(input)
    val solvencyOutput = processSolvencyStep(porOutput, polOutput)
    
    // Create intermediate workflow output
    val workflowOutput = PorWorkflowOutput(
      pooOutput = pooOutput,
      porOutput = porOutput,
      polOutput = polOutput,
      solvencyOutput = solvencyOutput,
      reportOutput = None,
      commitOutput = None
    )

    // Commit workflow output to file
    val commitOutput = activities.executeCommit(workflowOutput)
    val outputFile = commitOutput.filePath
    logger.info(s"Workflow output written to: $outputFile")

    // Generate report if required
    val reportOutput = if (input.reportRequired) {
      Some(activities.executeReport(input, pooOutput, porOutput, polOutput, solvencyOutput))
    } else {
      None
    }

    // Return final output with report
    workflowOutput.copy(reportOutput = reportOutput)
  }

  /**
   * Process Proof of Ownership step
   * If output exists -> use it
   * Else if input exists -> execute it
   */
  private def processPoOStep(input: PorWorkflowInput): Option[PooOutput] = {
    input.pooOutput match {
      case Some(output) =>
        logger.info("PoO: Using provided output (skipping execution)")
        Some(output)

      case None =>
        input.pooInput.map { pooInput =>
          logger.info("PoO: Executing with provided input")
          // Merge with mock wallets if empty
          val finalInput = if (pooInput.wallets.isEmpty) {
            pooInput.copy(wallets = generateMockWallets())
          } else {
            pooInput
          }
          activities.executeProofOfOwnership(finalInput)
        }
    }
  }

  /**
   * Process Proof of Reserves step
   * If output exists -> use it
   * Else if input exists -> execute it
   */
  private def processPoRStep(input: PorWorkflowInput): Option[PorOutput] = {
    input.porOutput match {
      case Some(output) =>
        logger.info("PoR: Using provided output (skipping execution)")
        Some(output)

      case None =>
        input.porInput.map { porInput =>
          logger.info("PoR: Executing with provided input")
          // Merge with mock wallets if empty
          val finalInput = if (porInput.wallets.isEmpty) {
            porInput.copy(wallets = generateMockWallets())
          } else {
            porInput
          }
          activities.executeProofOfReserves(finalInput)
        }
    }
  }

  /**
   * Process Proof of Liabilities step
   * If output exists -> use it
   * Else if input exists -> execute it
   */
  private def processPoLStep(input: PorWorkflowInput): Option[PolOutput] = {
    input.polOutput match {
      case Some(output) =>
        logger.info("PoL: Using provided output (skipping execution)")
        Some(output)

      case None =>
        input.polInput.map { polInput =>
          logger.info("PoL: Executing with provided input")
          activities.executeProofOfLiability(polInput)
        }
    }
  }

  /**
   * Process Solvency step
   * Only executes if both PoR and PoL outputs exist
   */
  private def processSolvencyStep(
    porOutput: Option[PorOutput],
    polOutput: Option[PolOutput]
  ): Option[SolvencyOutput] = {
    (porOutput, polOutput) match {
      case (Some(por), Some(pol)) =>
        logger.info("Solvency: Calculating from PoR and PoL outputs")
        Some(activities.executeSolvency(por, pol))

      case _ =>
        logger.info("Solvency: Skipped (requires both PoR and PoL outputs)")
        None
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
