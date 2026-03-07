package io.syspulse.skel.wf.temporal.por.demo

import scala.util.Random
import java.util.UUID
import io.temporal.activity.Activity
import com.typesafe.scalalogging.Logger
import io.syspulse.skel.wf.temporal.por._

class PolActivityDemo {
  private val log = Logger(getClass.getName)

  private def simulateWork(minSeconds: Int = 1, maxSeconds: Int = 3): Unit = {
    val delay = (Random.nextInt(maxSeconds - minSeconds + 1) + minSeconds) * 1000
    Thread.sleep(delay)
  }

  def execute(input: PolInput): PolOutput = {
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
}
