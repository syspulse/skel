package io.syspulse.skel.wf.temporal.por.demo

import com.typesafe.scalalogging.Logger
import io.syspulse.skel.wf.temporal.por._

/**
 * PoL Activity - processes prepared PoL data
 * (Data preparation is handled by workflow via PolSignalProcessors)
 *
 * This activity is agnostic to how the data arrived (signal, file, or simulated)
 * It simply processes whatever data is provided in run.input.pol.input.data
 */
class PolActivityDemo extends ActivityLogging {
  private val log = Logger(getClass.getName)

  def execute(run: PorWorkflowRun): PorWorkflowRun = {
    log.info(s"$wid PoL: Activity processing")

    run.input.pol.flatMap(_.input) match {
      case None =>
        log.warn(s"$wid PoL: No input provided, returning run unchanged")
        run

      case Some(polInput) =>
        // Read prepared data from input
        polInput.data match {
          case Some(polFileData) =>
            log.info(s"$wid PoL: Processing ${polFileData.liabilities.size} liabilities")

            // Convert PolFileData to PolOutput
            val output = PolSignalValidator.createOutput(polFileData)

            log.info(s"$wid PoL: Completed with ${output.liabilities.size} liability entries")
            run.copy(output = run.output.copy(pol = Some(output)))

          case None =>
            log.error(s"$wid PoL: No data provided in input (data should be prepared by PolSignalProcessors)")
            throw new IllegalStateException("PoL data not prepared - this should not happen")
        }
    }
  }
}
