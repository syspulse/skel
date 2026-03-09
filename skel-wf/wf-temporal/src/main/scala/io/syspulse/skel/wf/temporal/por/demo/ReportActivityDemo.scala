package io.syspulse.skel.wf.temporal.por.demo

import scala.util.Random
import io.temporal.activity.Activity
import com.typesafe.scalalogging.Logger
import io.syspulse.skel.wf.temporal.por._

class ReportActivityDemo {
  private val log = Logger(getClass.getName)

  def execute(run: PorWorkflowRun): PorWorkflowRun = {
    val activityInfo = Activity.getExecutionContext.getInfo
    val wid = s"[${activityInfo.getWorkflowId} / ${activityInfo.getRunId}]"

    log.info(s"$wid Report: ${run.input.report}")

    //simulateWork(1, 3)

    // Generate report
    val reportFilePath = os.temp.dir() / s"por_report_${System.currentTimeMillis()}.md"
    val reportContent = generateReportMarkdown(run)

    os.write(reportFilePath, reportContent)

    val output = ReportOutput(
      reportFilePath = reportFilePath.toString,
      reportLink = s"file://$reportFilePath"
    )

    log.info(s"$wid Completed Report generation: $reportFilePath")
    run.copy(output = run.output.copy(report = Some(output)))
  }

  private def generateReportMarkdown(run: PorWorkflowRun): String = {
    val sb = new StringBuilder

    sb.append(s"# Proof of Reserves Report\n\n")
    sb.append(s"## Owner Information\n\n")
    sb.append(s"- **Project**: ${run.proj}\n")
    sb.append(s"- **Start**: ${run.ts0}\n")
    sb.append(s"- **End**: ${run.ts1}\n")    

    if (run.output.poo.isDefined) {
      sb.append(s"## Proof of Ownership\n\n")
      sb.append(s"- **Timestamp**: ${run.output.poo.get.ts}\n")
      sb.append(s"- **Proofs Count**: ${run.output.poo.get.proofs.size}\n\n")
    }

    if (run.output.por.isDefined) {
      sb.append(s"## Proof of Reserves\n\n")
      sb.append(s"- **Timestamp**: ${run.output.por.get.ts}\n")
      sb.append(s"- **Balance Entries**: ${run.output.por.get.balances.size}\n\n")

      val assetSummary = run.output.por.get.balances.groupBy(_.asset).map { case (asset, balances) =>
        val total = balances.map(_.balance).sum
        (asset, total)
      }

      sb.append(s"### Asset Summary\n\n")
      assetSummary.foreach { case (asset, total) =>
        sb.append(s"- **$asset**: ${BigDecimal(total) / BigDecimal(10).pow(18)}\n")
      }
      sb.append("\n")
    }

    if (run.output.pol.isDefined) {
      sb.append(s"## Proof of Liabilities\n\n")
      sb.append(s"- **Timestamp**: ${run.output.pol.get.ts}\n")
      sb.append(s"- **Liability Entries**: ${run.output.pol.get.liabilities.size}\n")
      sb.append(s"- **Signature Type**: ${run.output.pol.get.signatureType}\n\n")

      val assetSummary = run.output.pol.get.liabilities.groupBy(_.asset).map { case (asset, liabilities) =>
        val total = liabilities.map(_.balance).sum
        (asset, total)
      }

      sb.append(s"### Liability Asset Summary\n\n")
      assetSummary.foreach { case (asset, total) =>
        sb.append(s"- **$asset**: ${BigDecimal(total) / BigDecimal(10).pow(18)}\n")
      }
      sb.append("\n")
    }

    if (run.output.solvency.isDefined) {
      val solvency = run.output.solvency.get
      sb.append(s"## Solvency Analysis\n\n")
      sb.append(s"- **Total Reserves (USD)**: $$${solvency.porTotalUsd}\n")
      sb.append(s"- **Total Liabilities (USD)**: $$${solvency.polTotalUsd}\n")
      sb.append(s"- **Solvency Ratio**: ${solvency.solvencyRatio}\n\n")

      val status = if (solvency.solvencyRatio <= 1.0) "✅ SOLVENT" else "❌ INSOLVENT"
      sb.append(s"### Status: $status\n\n")
    }

    sb.append(s"---\n")
    sb.append(s"*Report generated at ${new java.util.Date()}*\n")

    sb.toString
  }
}
