package io.syspulse.skel.wf.temporal.por

import com.typesafe.scalalogging.Logger

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

  private val log = Logger(getClass.getName) //Workflow.getLogger(classOf[PorWorkflowImpl])

  private val activityOptions = ActivityOptions.newBuilder()
    .setStartToCloseTimeout(Duration.ofMinutes(10))
    .build()

  private val activities = Workflow.newActivityStub(classOf[PorActivities], activityOptions)

  // Signal data storage (survives worker restarts - managed by Temporal)
  @volatile
  private var polSignalData: Option[PolFileData] = None

  override def signalPol(data: PolFileData): Unit = {
    val info = Workflow.getInfo()
    val wid = s"[${info.getWorkflowId} / ${info.getRunId}]"
    log.info(s"${wid} Received PoL signal with ${data.liabilities.size} liabilities")
    polSignalData = Some(data)
  }

  override def getPolSignalData(): Option[PolFileData] = {
    polSignalData
  }

  override def execute(run: PorWorkflowRun): PorWorkflowRun = {
    val info = Workflow.getInfo()
    implicit val wid = s"[${info.getWorkflowId} / ${info.getRunId}]"    

    log.info(s"[$wid] Starting Workflow: ${run}")

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

    log.info(s"[$wid] Finished Workflow: ${currentRun}")
    currentRun
  }

  private def processPoOStep(run: PorWorkflowRun)(implicit wid:String): PorWorkflowRun = {    
    run.input.poo match {
      case None =>
        log.info(s"$wid PoO: Skipped (step not defined)")
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
            log.info(s"$wid PoO: Skipped (no input provided)")
            run
        }
    }
  }

  private def processPoRStep(run: PorWorkflowRun)(implicit wid:String): PorWorkflowRun = {
    run.input.por match {
      case None =>
        log.info(s"$wid PoR: Skipped (step not defined)")
        run

      case Some(step) =>
        step.input match {
          case Some(porInput) =>            
            activities.executeProofOfReserves(run)

          case None =>
            log.info(s"$wid PoR: Skipped (no input provided)")
            run
        }
    }
  }

  private def processPoLStep(run: PorWorkflowRun)(implicit wid:String): PorWorkflowRun = {

    run.input.pol match {
      case None =>
        log.info(s"$wid PoL: Skipped (step not defined)")
        run

      case Some(step) =>
        step.input match {
          case Some(polInput) =>
            import io.syspulse.skel.wf.temporal.por.demo.{PolSignalProcessors, PolSignalContext}

            val signalMode = polInput.config.get("signalMode").fold("simulate")(_.toString)
            val signalTimeout = polInput.config.get("signalTimeout").fold(24 * 60 * 60 * 1000L)(_.toString.toLong)

            // Use PolSignalProcessors to prepare data based on mode
            val polFileData = signalMode.toLowerCase match {
              case "api" if polInput.waitForConfirmation =>
                log.info(s"$wid PoL: Processing in API mode")
                PolSignalProcessors.processApiMode(polSignalData, signalTimeout, wid)

              case "file" if polInput.waitForConfirmation =>
                log.info(s"$wid PoL: Processing in file mode")
                val ctx = PolSignalContext(
                  workflowId = run.wid.getOrElse("unknown"),
                  runId = run.rid.getOrElse("unknown"),
                  wid = wid,
                  log = log,
                  signalPollIntervalMs = 5000L
                )
                PolSignalProcessors.processFileMode(ctx)

              case "simulate" | _ =>
                log.info(s"$wid PoL: Processing in simulate mode")
                PolSignalProcessors.processSimulateMode(wid)
            }

            // Inject prepared data into run and pass to activity
            log.info(s"$wid PoL: Data prepared (${polFileData.liabilities.size} liabilities), passing to activity")
            val runWithData = PolSignalProcessors.injectDataIntoRun(run, polFileData)
            activities.executeProofOfLiability(runWithData)

          case None =>
            log.info(s"$wid PoL: Skipped (no input provided)")
            run
        }
    }
  }

  private def processSolvencyStep(run: PorWorkflowRun)(implicit wid:String): PorWorkflowRun = {
    run.input.solvency match {
      case None =>
        log.info(s"$wid Solvency: Skipped (step not defined)")
        run

      case Some(step) =>
        (run.output.por, run.output.pol) match {
          case (Some(por), Some(pol)) =>
            activities.executeSolvency(run)

          case _ =>
            log.info(s"$wid Solvency: Skipped (requires both PoR and PoL outputs)")
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
        log.info(s"$wid Commit: Skipped (step not defined)")
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
        log.info(s"${wid} Report: Skipped (step not defined)")
        run

      case Some(step) =>
        // Check if report is configured (we use empty config Map to indicate enabled)
        val reportEnabled = step.config.isEmpty || step.config.getOrElse("enabled", "true") == "true"

        if (reportEnabled) {          
          activities.executeReport(run)
        } else {
          log.info(s"${wid} Report: Skipped (not enabled)")
          run
        }
    }
  }

}
