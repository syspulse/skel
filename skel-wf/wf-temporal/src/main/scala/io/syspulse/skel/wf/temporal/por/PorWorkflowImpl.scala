package io.syspulse.skel.wf.temporal.por

import io.temporal.workflow.Workflow
import io.temporal.activity.ActivityOptions
import java.time.Duration
import io.temporal.activity.Activity
import io.syspulse.skel.wf.temporal.por.demo.DemoUtil

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
    val info = Workflow.getInfo()
    implicit val wid = s"[${info.getWorkflowId} / ${info.getRunId}]"    

    logger.info(s"[$wid] Starting Workflow: ${run}")

    // Process each step using workflow run context
    var currentRun = run.copy(wid = Some(info.getWorkflowId), rid = Some(info.getRunId))

    // PoO Step
    currentRun = processPoOStep(currentRun)

    // PoR Step
    currentRun = processPoRStep(currentRun)

    // PoL Step
    currentRun = processPoLStep(currentRun)

    // Solvency Step
    currentRun = processSolvencyStep(currentRun)
    
    // Report Step
    currentRun = processReportStep(currentRun)

    // Commit Step
    currentRun = processCommitStep(currentRun)

    logger.info(s"[$wid] Finished Workflow: ${currentRun}")
    currentRun
  }

  private def processPoOStep(run: PorWorkflowRun)(implicit wid:String): PorWorkflowRun = {    
    run.input.poo match {
      case None =>
        logger.info(s"$wid PoO: Skipped (step not defined)")
        run

      case Some(step) =>
        step.input match {
          case Some(pooInput) =>
            // Merge with mock wallets if empty
            val finalInput = if (pooInput.wallets.isEmpty) {
              pooInput.copy(wallets = DemoUtil.generateMockWallets())
            } else {
              pooInput
            }
            val updatedRun = run.copy(input = run.input.copy(poo = Some(step.copy(input = Some(finalInput)))))
            activities.executeProofOfOwnership(updatedRun)

          case None =>
            logger.info(s"$wid PoO: Skipped (no input provided)")
            run
        }
    }
  }

  private def processPoRStep(run: PorWorkflowRun)(implicit wid:String): PorWorkflowRun = {
    run.input.por match {
      case None =>
        logger.info(s"$wid PoR: Skipped (step not defined)")
        run

      case Some(step) =>
        step.input match {
          case Some(porInput) =>            
            activities.executeProofOfReserves(run)

          case None =>
            logger.info(s"$wid PoR: Skipped (no input provided)")
            run
        }
    }
  }

  private def processPoLStep(run: PorWorkflowRun)(implicit wid:String): PorWorkflowRun = {
    run.input.pol match {
      case None =>
        logger.info(s"$wid PoL: Skipped (step not defined)")
        run

      case Some(step) =>
        step.input match {
          case Some(polInput) =>            
            activities.executeProofOfLiability(run)

          case None =>
            logger.info(s"$wid PoL: Skipped (no input provided)")
            run
        }
    }
  }

  private def processSolvencyStep(run: PorWorkflowRun)(implicit wid:String): PorWorkflowRun = {
    run.input.solvency match {
      case None =>
        logger.info(s"$wid Solvency: Skipped (step not defined)")
        run

      case Some(step) =>
        (run.output.por, run.output.pol) match {
          case (Some(por), Some(pol)) =>
            activities.executeSolvency(run)

          case _ =>
            logger.info(s"$wid Solvency: Skipped (requires both PoR and PoL outputs)")
            run
        }
    }
  }

  /**
   * Process Commit step
   * If step is None -> skip
   * Writes workflow output to storage
   */
  private def processCommitStep(run: PorWorkflowRun)(implicit wid:String): PorWorkflowRun = {
    run.input.commit match {
      case None =>
        logger.info(s"$wid Commit: Skipped (step not defined)")
        run

      case Some(step) =>        
        val updatedRun = activities.executeCommit(run)        
        updatedRun
    }
  }

  /**
   * Process Report step
   * If step is None -> skip
   * Generates report if required
   */
  private def processReportStep(run: PorWorkflowRun)(implicit wid:String): PorWorkflowRun = {
    run.input.report match {
      case None =>
        logger.info(s"${wid} Report: Skipped (step not defined)")
        run

      case Some(step) =>
        // Check if report is configured (we use empty config Map to indicate enabled)
        val reportEnabled = step.config.isEmpty || step.config.getOrElse("enabled", "true") == "true"

        if (reportEnabled) {          
          activities.executeReport(run)
        } else {
          logger.info(s"${wid} Report: Skipped (not enabled)")
          run
        }
    }
  }

}
