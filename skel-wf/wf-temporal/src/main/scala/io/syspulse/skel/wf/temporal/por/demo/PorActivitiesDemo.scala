package io.syspulse.skel.wf.temporal.por.demo

import com.typesafe.scalalogging.Logger

import io.syspulse.skel.wf.temporal.por._

class PorActivitiesDemo extends PorActivities {
  private val log = Logger(getClass.getName)

  // Instantiate demo activity implementations
  private val pooActivity = new PooActivityDemo()
  private val porActivity = new PorActivityDemo()
  private val polActivity = new PolActivityDemo()
  private val solvencyActivity = new SolvencyActivityDemo()
  private val reportActivity = new ReportActivityDemo()

  override def executeProofOfOwnership(input: PooInput): PooOutput = {
    pooActivity.execute(input)
  }

  override def executeProofOfReserves(input: PorInput): PorOutput = {
    porActivity.execute(input)
  }

  override def executeProofOfLiability(input: PolInput): PolOutput = {
    polActivity.execute(input)
  }

  override def executeSolvency(porOutput: PorOutput, polOutput: PolOutput): SolvencyOutput = {
    solvencyActivity.execute(porOutput, polOutput)
  }

  override def executeReport(
    workflowInput: PorWorkflowInput,
    pooOutput: Option[PooOutput],
    porOutput: Option[PorOutput],
    polOutput: Option[PolOutput],
    solvencyOutput: Option[SolvencyOutput]
  ): ReportOutput = {
    reportActivity.execute(workflowInput, pooOutput, porOutput, polOutput, solvencyOutput)
  }
}
