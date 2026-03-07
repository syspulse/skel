package io.syspulse.skel.wf.temporal.por

import scala.util.Random
import java.util.UUID
import io.temporal.activity.Activity
import com.typesafe.scalalogging.Logger

class PorActivitiesImpl extends PorActivities {
  private val log = Logger(getClass.getName)

  private def simulateWork(minSeconds: Int = 1, maxSeconds: Int = 3): Unit = {
    val delay = (Random.nextInt(maxSeconds - minSeconds + 1) + minSeconds) * 1000
    Thread.sleep(delay)
  }

  override def executeProofOfOwnership(input: PooInput): PooOutput = {
    val activityInfo = Activity.getExecutionContext.getInfo
    val workflowId = activityInfo.getWorkflowId
    log.info(s"[proof_of_ownership][wf:$workflowId] Starting PoO with ${input.wallets.size} wallets, proof type: ${input.proofType}")

    simulateWork(1, 3)

    // Generate mock proofs (signatures or transaction hashes)
    val proofs = input.wallets.map { wallet =>
      val proof = input.proofType match {
        case "signature" => s"0x${Random.alphanumeric.take(128).mkString}"
        case "transaction_hash" => s"0x${Random.alphanumeric.take(64).mkString}"
        case _ => s"0x${Random.alphanumeric.take(64).mkString}"
      }
      wallet.address -> proof
    }.toMap

    val output = PooOutput(
      timestamp = System.currentTimeMillis(),
      proofs = proofs
    )

    log.info(s"[proof_of_ownership][wf:$workflowId] Completed PoO with ${proofs.size} proofs")
    output
  }

  override def executeProofOfReserves(input: PorInput): PorOutput = {
    val activityInfo = Activity.getExecutionContext.getInfo
    val workflowId = activityInfo.getWorkflowId
    log.info(s"[proof_of_reserves][wf:$workflowId] Starting PoR with ${input.wallets.size} wallets and ${input.assets.size} assets")

    simulateWork(1, 3)

    // Generate mock balances for each wallet and asset
    val balances = for {
      wallet <- input.wallets
      asset <- input.assets
    } yield {
      val balance = BigInt(Random.nextInt(1000000)) * BigInt(10).pow(18) // Mock balance
      WalletWithAsset(
        address = wallet.address,
        network = wallet.network,
        asset = asset,
        balance = balance
      )
    }

    val output = PorOutput(
      timestamp = System.currentTimeMillis(),
      balances = balances.toList
    )

    log.info(s"[proof_of_reserves][wf:$workflowId] Completed PoR with ${balances.size} balance entries")
    output
  }

  override def executeProofOfLiability(input: PolInput): PolOutput = {
    val activityInfo = Activity.getExecutionContext.getInfo
    val workflowId = activityInfo.getWorkflowId
    log.info(s"[proof_of_liability][wf:$workflowId] Starting PoL - waiting for human input")
    log.info(s"[proof_of_liability][wf:$workflowId] Timer is waiting for human input")

    // Generate demo file
    val demoFilePath = os.temp.dir() / s"liabilities_${System.currentTimeMillis()}.json"
    val demoData = generateDemoLiabilitiesFile()

    // Write demo file
    val jsonContent = s"""{
  "timestamp": ${demoData.timestamp},
  "liabilities": [
${demoData.liabilities.map(l => s"""    {"userId": "${l.userId}", "asset": "${l.asset}", "balance": "${l.balance}"}""").mkString(",\n")}
  ],
  "signature": "${demoData.signature}",
  "signatureType": "${demoData.signatureType}",
  "publicKey": "${demoData.publicKey}"
}"""

    os.write(demoFilePath, jsonContent)
    log.info(s"[proof_of_liability][wf:$workflowId] Demo file generated: $demoFilePath")

    // Simulate waiting for confirmation
    if (input.waitForConfirmation) {
      log.info(s"[proof_of_liability][wf:$workflowId] Please confirm to use file: $demoFilePath")
      log.info(s"[proof_of_liability][wf:$workflowId] Press Enter to continue...")
      // In real implementation, this would wait for user input
      // For simulation, we just add a delay
      simulateWork(2, 4)
    }

    val output = PolOutput(
      timestamp = demoData.timestamp,
      liabilities = demoData.liabilities,
      signature = demoData.signature,
      signatureType = demoData.signatureType,
      publicKey = demoData.publicKey
    )

    log.info(s"[proof_of_liability][wf:$workflowId] Completed PoL with ${output.liabilities.size} liability entries")
    output
  }

  override def executeSolvency(porOutput: PorOutput, polOutput: PolOutput): SolvencyOutput = {
    val activityInfo = Activity.getExecutionContext.getInfo
    val workflowId = activityInfo.getWorkflowId
    log.info(s"[solvency][wf:$workflowId] Starting Solvency calculation")

    simulateWork(1, 2)

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
    val porTotalUsd = porOutput.balances.map { balance =>
      val amountInToken = BigDecimal(balance.balance) / BigDecimal(10).pow(18)
      val rate = usdRates.getOrElse(balance.asset, BigDecimal("1.00"))
      amountInToken * rate
    }.sum

    // Calculate total liabilities in USD
    val polTotalUsd = polOutput.liabilities.map { liability =>
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

    log.info(s"[solvency][wf:$workflowId] Completed Solvency: Reserves=$$$porTotalUsd, Liabilities=$$$polTotalUsd, Ratio=${solvencyRatio}")
    output
  }

  override def executeReport(
    workflowInput: PorWorkflowInput,
    pooOutput: Option[PooOutput],
    porOutput: Option[PorOutput],
    polOutput: Option[PolOutput],
    solvencyOutput: Option[SolvencyOutput]
  ): ReportOutput = {
    val activityInfo = Activity.getExecutionContext.getInfo
    val workflowId = activityInfo.getWorkflowId
    log.info(s"[report][wf:$workflowId] Starting Report generation")

    simulateWork(1, 3)

    // Generate report
    val reportFilePath = os.temp.dir() / s"por_report_${System.currentTimeMillis()}.md"
    val reportContent = generateReportMarkdown(workflowInput, pooOutput, porOutput, polOutput, solvencyOutput)

    os.write(reportFilePath, reportContent)

    val output = ReportOutput(
      reportFilePath = reportFilePath.toString,
      reportLink = s"file://$reportFilePath"
    )

    log.info(s"[report][wf:$workflowId] Completed Report generation: $reportFilePath")
    output
  }

  private def generateDemoLiabilitiesFile(): PolFileData = {
    val liabilities = (1 to 10).map { i =>
      Liability(
        userId = UUID.randomUUID(),
        asset = Seq("BTC", "ETH", "LINK", "AAVE", "SOL")(Random.nextInt(5)),
        balance = BigInt(Random.nextInt(500000)) * BigInt(10).pow(18)
      )
    }.toList

    PolFileData(
      timestamp = System.currentTimeMillis(),
      liabilities = liabilities,
      signature = s"0x${Random.alphanumeric.take(128).mkString}",
      signatureType = "public_key",
      publicKey = s"0x${Random.alphanumeric.take(64).mkString}"
    )
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
