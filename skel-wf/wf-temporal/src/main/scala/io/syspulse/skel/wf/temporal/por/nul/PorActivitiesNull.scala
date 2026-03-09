package io.syspulse.skel.wf.temporal.por.nul

import com.typesafe.scalalogging.Logger

import io.syspulse.skel.wf.temporal.por._

/**
 * Null implementation of PorActivities that does nothing.
 * All activities simply log their execution and return the input unchanged.
 * This implementation serves as a base for extending workflows with real implementations.
 */
class PorActivitiesNull extends PorActivities with ActivityLogging {
  private val log = Logger(getClass.getName)

  override def executeProofOfOwnership(run: PorWorkflowRun): PorWorkflowRun = {
    log.info(s"$wid [NULL] executeProofOfOwnership: ${run.proj}")
    run
  }

  override def executeProofOfReserves(run: PorWorkflowRun): PorWorkflowRun = {
    log.info(s"$wid [NULL] executeProofOfReserves: ${run.proj}")
    run
  }

  override def executeProofOfLiability(run: PorWorkflowRun): PorWorkflowRun = {
    log.info(s"$wid [NULL] executeProofOfLiability: ${run.proj}")
    run
  }

  override def executeSolvency(run: PorWorkflowRun): PorWorkflowRun = {
    log.info(s"$wid [NULL] executeSolvency: ${run.proj}")
    run
  }

  override def executeCommit(run: PorWorkflowRun): PorWorkflowRun = {
    log.info(s"$wid [NULL] executeCommit: ${run.proj}")
    run
  }

  override def executeReport(run: PorWorkflowRun): PorWorkflowRun = {
    log.info(s"$wid [NULL] executeReport: ${run.proj}")
    run
  }
}
