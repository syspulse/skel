package io.syspulse.skel.wf.temporal.por

import io.temporal.workflow.Workflow
import io.temporal.activity.ActivityOptions
import java.time.Duration

/**
 * Main PoR Workflow Implementation
 * Processes workflow run context through all steps
 */
class PorWorkflowImpl extends PorWorkflow {

  private val logger = Workflow.getLogger(classOf[PorWorkflowImpl])

  private val activityOptions = ActivityOptions.newBuilder()
    .setStartToCloseTimeout(Duration.ofMinutes(10))
    .build()

  private val activities = Workflow.newActivityStub(classOf[PorActivities], activityOptions)

  override def execute(run: PorWorkflowRun): PorWorkflowRun = {
    logger.info(s"Starting PoR Workflow for owner: ${run.ownerName}")

    // Process each step using workflow run context
    var currentRun = run

    // PoO Step
    currentRun = processPoOStep(currentRun)

    // PoR Step
    currentRun = processPoRStep(currentRun)

    // PoL Step
    currentRun = processPoLStep(currentRun)

    // Solvency Step
    currentRun = processSolvencyStep(currentRun)

    // Commit Step
    currentRun = processCommitStep(currentRun)

    // Report Step
    currentRun = processReportStep(currentRun)

    logger.info(s"Completed PoR Workflow for owner: ${currentRun.ownerName}")
    currentRun
  }

  /**
   * Process Proof of Ownership step
   * If input exists -> execute activity (activity will merge with existing output)
   */
  private def processPoOStep(run: PorWorkflowRun): PorWorkflowRun = {
    run.input.poo.input match {
      case Some(pooInput) =>
        logger.info("PoO: Executing with provided input")
        // Merge with mock wallets if empty
        val finalInput = if (pooInput.wallets.isEmpty) {
          pooInput.copy(wallets = generateMockWallets())
        } else {
          pooInput
        }
        val updatedRun = run.copy(input = run.input.copy(poo = run.input.poo.copy(input = Some(finalInput))))
        activities.executeProofOfOwnership(updatedRun)

      case None =>
        logger.info("PoO: Skipped (no input provided)")
        run
    }
  }

  /**
   * Process Proof of Reserves step
   * If input exists -> execute activity (activity will merge with existing output)
   */
  private def processPoRStep(run: PorWorkflowRun): PorWorkflowRun = {
    run.input.por.input match {
      case Some(porInput) =>
        logger.info("PoR: Executing with provided input")
        // Merge with mock wallets if empty
        val finalInput = if (porInput.wallets.isEmpty) {
          porInput.copy(wallets = generateMockWallets())
        } else {
          porInput
        }
        val updatedRun = run.copy(input = run.input.copy(por = run.input.por.copy(input = Some(finalInput))))
        activities.executeProofOfReserves(updatedRun)

      case None =>
        logger.info("PoR: Skipped (no input provided)")
        run
    }
  }

  /**
   * Process Proof of Liabilities step
   * If input exists -> execute activity (activity will merge with existing output)
   */
  private def processPoLStep(run: PorWorkflowRun): PorWorkflowRun = {
    run.input.pol.input match {
      case Some(polInput) =>
        logger.info("PoL: Executing with provided input")
        activities.executeProofOfLiability(run)

      case None =>
        logger.info("PoL: Skipped (no input provided)")
        run
    }
  }

  /**
   * Process Solvency step
   * Only executes if both PoR and PoL outputs exist
   */
  private def processSolvencyStep(run: PorWorkflowRun): PorWorkflowRun = {
    (run.output.por, run.output.pol) match {
      case (Some(por), Some(pol)) =>
        logger.info("Solvency: Calculating from PoR and PoL outputs")
        activities.executeSolvency(run)

      case _ =>
        logger.info("Solvency: Skipped (requires both PoR and PoL outputs)")
        run
    }
  }

  /**
   * Process Commit step
   * Writes workflow output to storage
   */
  private def processCommitStep(run: PorWorkflowRun): PorWorkflowRun = {
    logger.info("Commit: Writing workflow output to storage")
    val updatedRun = activities.executeCommit(run)
    updatedRun.output.commit.foreach(output =>
      logger.info(s"Commit: Output written to: ${output.filePath}")
    )
    updatedRun
  }

  /**
   * Process Report step
   * Generates report if required
   */
  private def processReportStep(run: PorWorkflowRun): PorWorkflowRun = {
    // Check if report is configured (we use empty config Map to indicate enabled)
    val reportEnabled = run.input.report.config.isEmpty || run.input.report.config.getOrElse("enabled", "true") == "true"

    if (reportEnabled) {
      logger.info("Report: Generating report")
      activities.executeReport(run)
    } else {
      logger.info("Report: Skipped (not enabled)")
      run
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
