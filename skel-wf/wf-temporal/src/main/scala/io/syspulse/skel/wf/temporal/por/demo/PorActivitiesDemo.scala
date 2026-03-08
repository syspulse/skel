package io.syspulse.skel.wf.temporal.por.demo

import com.typesafe.scalalogging.Logger

import io.syspulse.skel.wf.temporal.por._
import scala.util.Random

class PorActivitiesDemo extends PorActivities {
  private val log = Logger(getClass.getName)

  // Instantiate demo activity implementations
  private val pooActivity = new PooActivityDemo()
  private val porActivity = new PorActivityDemo()
  private val polActivity = new PolActivityDemo()
  private val solvencyActivity = new SolvencyActivityDemo()
  private val commitActivity = new CommitActivityDemo()
  private val reportActivity = new ReportActivityDemo()

  override def executeProofOfOwnership(run: PorWorkflowRun): PorWorkflowRun = {
    pooActivity.execute(run)
  }

  override def executeProofOfReserves(run: PorWorkflowRun): PorWorkflowRun = {
    porActivity.execute(run)
  }

  override def executeProofOfLiability(run: PorWorkflowRun): PorWorkflowRun = {
    polActivity.execute(run)
  }

  override def executeSolvency(run: PorWorkflowRun): PorWorkflowRun = {
    solvencyActivity.execute(run)
  }

  override def executeCommit(run: PorWorkflowRun): PorWorkflowRun = {
    commitActivity.execute(run)
  }

  override def executeReport(run: PorWorkflowRun): PorWorkflowRun = {
    reportActivity.execute(run)
  }
}

object PorActivitiesDemo {
  def simulateWork(minSeconds: Int = 1, maxSeconds: Int = 3): Unit = {
    val delay = (Random.nextInt(maxSeconds - minSeconds + 1) + minSeconds) * 1000
    Thread.sleep(delay)
  }
}
