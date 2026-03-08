package io.syspulse.skel.wf.temporal.por

import io.temporal.activity.ActivityInterface
import io.temporal.activity.ActivityMethod

@ActivityInterface
trait PorActivities {

  @ActivityMethod
  def executeProofOfOwnership(run: PorWorkflowRun): PorWorkflowRun

  @ActivityMethod
  def executeProofOfReserves(run: PorWorkflowRun): PorWorkflowRun

  @ActivityMethod
  def executeProofOfLiability(run: PorWorkflowRun): PorWorkflowRun

  @ActivityMethod
  def executeSolvency(run: PorWorkflowRun): PorWorkflowRun

  @ActivityMethod
  def executeCommit(run: PorWorkflowRun): PorWorkflowRun

  @ActivityMethod
  def executeReport(run: PorWorkflowRun): PorWorkflowRun
}
