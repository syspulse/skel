package io.syspulse.skel.wf.temporal.por.demo

import scala.util.Random
import java.util.UUID
import com.typesafe.scalalogging.Logger
import io.syspulse.skel.wf.temporal.por._

class PolActivityDemo extends ActivityLogging {
  private val log = Logger(getClass.getName)
  private val SignalPollIntervalMs = 5000L

  def execute(run: PorWorkflowRun): PorWorkflowRun = {
    log.info(s"$wid PoL: ${run.input.pol}")

    run.input.pol.flatMap(_.input) match {
      case None =>
        log.warn(s"$wid PoL: No input provided, returning run unchanged")
        run

      case Some(input) =>
        val signalMode = input.config.get("signalMode").fold("simulate")(_.toString)
        log.info(s"$wid Starting PoL (signalMode=$signalMode)")

        val demoData = generateDemoLiabilitiesFile()

        // Only create demo file for simulate mode
        if (signalMode.toLowerCase == "simulate") {
          val demoFilePath = os.temp.dir() / s"liabilities_${System.currentTimeMillis()}.json"
          val jsonContent = s"""{
  "ts": ${demoData.ts},
  "liabilities": [
${demoData.liabilities.map(l => s"""    {"userId": "${l.userId}", "asset": "${l.asset}", "balance": "${l.balance}"}""").mkString(",\n")}
  ],
  "signature": "${demoData.signature}",
  "signatureType": "${demoData.signatureType}",
  "publicKey": "${demoData.publicKey}"
}"""
          os.write(demoFilePath, jsonContent)
          log.info(s"$wid Demo file generated: $demoFilePath")
        }

        // For API mode, workflow handles signal and creates output directly
        // Activity only called for file/simulate modes or when signal fails
        val finalData = if (input.waitForConfirmation && signalMode.toLowerCase != "api") {
          log.info(s"$wid Waiting for confirmation (signalMode=$signalMode)")
          val ctx = PolSignalContext(workflowId, runId, wid, log, SignalPollIntervalMs)
          PolSignalProcessors.get(signalMode).waitAndResolve(ctx, demoData)
        } else {
          // API mode shouldn't reach here (workflow handles it)
          // But if it does, use demo data
          demoData
        }

        // PoL MERGE STRATEGY: Never trust previous output, always override with fresh input
        run.output.pol.foreach { previousOutput =>
          log.info(s"$wid PoL: Ignoring previous output (${previousOutput.liabilities.size} entries), using fresh data")
        }

        val output = PolOutput(
          ts = finalData.ts,
          liabilities = finalData.liabilities,
          signature = finalData.signature,
          signatureType = finalData.signatureType,
          publicKey = finalData.publicKey
        )

        log.info(s"$wid Completed PoL with ${output.liabilities.size} liability entries")
        run.copy(output = run.output.copy(pol = Some(output)))
    }
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
      ts = System.currentTimeMillis(),
      liabilities = liabilities,
      signature = s"0x${Random.alphanumeric.take(128).mkString}",
      signatureType = "public_key",
      publicKey = s"0x${Random.alphanumeric.take(64).mkString}"
    )
  }
}
