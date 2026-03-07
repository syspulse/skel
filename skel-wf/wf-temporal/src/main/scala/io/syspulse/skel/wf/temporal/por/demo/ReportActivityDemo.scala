package io.syspulse.skel.wf.temporal.por.demo

import scala.util.Random
import io.temporal.activity.Activity
import com.typesafe.scalalogging.Logger
import io.syspulse.skel.wf.temporal.por._

class ReportActivityDemo {
  private val log = Logger(getClass.getName)

  private def simulateWork(minSeconds: Int = 1, maxSeconds: Int = 3): Unit = {
    val delay = (Random.nextInt(maxSeconds - minSeconds + 1) + minSeconds) * 1000
    Thread.sleep(delay)
  }

  def execute(
    workflowInput: PorWorkflowInput,
    pooOutput: Option[PooOutput],
    porOutput: Option[PorOutput],
    polOutput: Option[PolOutput],
    solvencyOutput: Option[SolvencyOutput]
  ): ReportOutput = {
    val activityInfo = Activity.getExecutionContext.getInfo
    val wid = activityInfo.getWorkflowId
    log.info(s"[$wid] Starting Report generation")

    simulateWork(1, 3)

    // Generate report
    val reportFilePath = os.temp.dir() / s"por_report_${System.currentTimeMillis()}.md"
    val reportContent = generateReportMarkdown(workflowInput, pooOutput, porOutput, polOutput, solvencyOutput)

    os.write(reportFilePath, reportContent)

    val output = ReportOutput(
      reportFilePath = reportFilePath.toString,
      reportLink = s"file://$reportFilePath"
    )

    log.info(s"[$wid] Completed Report generation: $reportFilePath")
    output
  }

  private def generateReportMarkdown(
    workflowInput: PorWorkflowInput,
    pooOutput: Option[PooOutput],
    porOutput: Option[PorOutput],
    polOutput: Option[PolOutput],
    solvencyOutput: Option[SolvencyOutput]
  ): String = {
    val sb = new StringBuilder

    sb.append(s"# Proof of Reserves Report\n\n")
    sb.append(s"## CEX Information\n\n")
    sb.append(s"- **CEX Name**: ${workflowInput.cexName}\n")
    sb.append(s"- **Timestamp**: ${workflowInput.timestamp}\n")
    sb.append(s"- **Date**: ${new java.util.Date(workflowInput.timestamp)}\n\n")

    if (pooOutput.isDefined) {
      sb.append(s"## Proof of Ownership\n\n")
      sb.append(s"- **Timestamp**: ${pooOutput.get.timestamp}\n")
      sb.append(s"- **Proofs Count**: ${pooOutput.get.proofs.size}\n\n")
    }

    if (porOutput.isDefined) {
      sb.append(s"## Proof of Reserves\n\n")
      sb.append(s"- **Timestamp**: ${porOutput.get.timestamp}\n")
      sb.append(s"- **Balance Entries**: ${porOutput.get.balances.size}\n\n")

      val assetSummary = porOutput.get.balances.groupBy(_.asset).map { case (asset, balances) =>
        val total = balances.map(_.balance).sum
        (asset, total)
      }

      sb.append(s"### Asset Summary\n\n")
      assetSummary.foreach { case (asset, total) =>
        sb.append(s"- **$asset**: ${BigDecimal(total) / BigDecimal(10).pow(18)}\n")
      }
      sb.append("\n")
    }

    if (polOutput.isDefined) {
      sb.append(s"## Proof of Liabilities\n\n")
      sb.append(s"- **Timestamp**: ${polOutput.get.timestamp}\n")
      sb.append(s"- **Liability Entries**: ${polOutput.get.liabilities.size}\n")
      sb.append(s"- **Signature Type**: ${polOutput.get.signatureType}\n\n")

      val assetSummary = polOutput.get.liabilities.groupBy(_.asset).map { case (asset, liabilities) =>
        val total = liabilities.map(_.balance).sum
        (asset, total)
      }

      sb.append(s"### Liability Asset Summary\n\n")
      assetSummary.foreach { case (asset, total) =>
        sb.append(s"- **$asset**: ${BigDecimal(total) / BigDecimal(10).pow(18)}\n")
      }
      sb.append("\n")
    }

    if (solvencyOutput.isDefined) {
      val solvency = solvencyOutput.get
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
