package io.syspulse.skel.wf.temporal.por.demo

import scala.util.Random
import io.temporal.activity.Activity
import com.typesafe.scalalogging.Logger
import io.syspulse.skel.wf.temporal.por._

class SolvencyActivityDemo {
  private val log = Logger(getClass.getName)
  
  def execute(run: PorWorkflowRun): PorWorkflowRun = {
    val activityInfo = Activity.getExecutionContext.getInfo
    val wid = s"[${activityInfo.getWorkflowId} / ${activityInfo.getRunId}]"

    (run.output.por, run.output.pol) match {
      case (Some(por), Some(pol)) =>
        log.info(s"$wid Starting Solvency calculation")

        //simulateWork(1, 2)

        // Mock USD conversion rates
        val usdRates = Map(
          "BTC" -> BigDecimal("50000.00"),
          "ETH" -> BigDecimal("3000.00"),
          "LINK" -> BigDecimal("15.00"),
          "AAVE" -> BigDecimal("100.00"),
          "SOL" -> BigDecimal("100.00"),
          "TRX" -> BigDecimal("0.10")
        )

        // Calculate total reserves in USD
        val porTotalUsd = por.balances.map { balance =>
          val amountInToken = BigDecimal(balance.balance) / BigDecimal(10).pow(18)
          val rate = usdRates.getOrElse(balance.asset, BigDecimal("1.00"))
          amountInToken * rate
        }.sum

        // Calculate total liabilities in USD
        val polTotalUsd = pol.liabilities.map { liability =>
          val amountInToken = BigDecimal(liability.balance) / BigDecimal(10).pow(18)
          val rate = usdRates.getOrElse(liability.asset, BigDecimal("1.00"))
          amountInToken * rate
        }.sum

        // Calculate solvency ratio (PoL / PoR)
        val solvencyRatio = if (porTotalUsd > 0) polTotalUsd / porTotalUsd else BigDecimal(0)

        val output = SolvencyOutput(
          porTotalUsd = porTotalUsd,
          polTotalUsd = polTotalUsd,
          solvencyRatio = solvencyRatio
        )

        log.info(s"$wid Completed Solvency: Reserves=$$$porTotalUsd, Liabilities=$$$polTotalUsd, Ratio=${solvencyRatio}")
        run.copy(output = run.output.copy(solvency = Some(output)))

      case _ =>
        log.warn(s"$wid Solvency: Missing PoR or PoL output, cannot calculate solvency")
        run
    }
  }
}
