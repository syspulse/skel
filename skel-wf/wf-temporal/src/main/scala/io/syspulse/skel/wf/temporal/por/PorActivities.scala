package io.syspulse.skel.wf.temporal.por

import io.temporal.activity.ActivityInterface
import io.temporal.activity.ActivityMethod

@ActivityInterface
trait PorActivities {

  @ActivityMethod
  def executeProofOfOwnership(input: PooInput): PooOutput

  @ActivityMethod
  def executeProofOfReserves(input: PorInput): PorOutput

  @ActivityMethod
  def executeProofOfLiability(input: PolInput): PolOutput

  @ActivityMethod
  def executeSolvency(porOutput: PorOutput, polOutput: PolOutput): SolvencyOutput

  @ActivityMethod
  def executeReport(
    workflowInput: PorWorkflowInput,
    pooOutput: Option[PooOutput],
    porOutput: Option[PorOutput],
    polOutput: Option[PolOutput],
    solvencyOutput: Option[SolvencyOutput]
  ): ReportOutput
}
