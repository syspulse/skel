package io.syspulse.skel.wf.temporal.por.demo

import scala.util.Random
import io.temporal.activity.Activity
import com.typesafe.scalalogging.Logger
import io.syspulse.skel.wf.temporal.por._

class PooActivityDemo {
  private val log = Logger(getClass.getName)

  private def simulateWork(minSeconds: Int = 1, maxSeconds: Int = 3): Unit = {
    val delay = (Random.nextInt(maxSeconds - minSeconds + 1) + minSeconds) * 1000
    Thread.sleep(delay)
  }

  def execute(input: PooInput): PooOutput = {
    val activityInfo = Activity.getExecutionContext.getInfo
    val wid = s"[${activityInfo.getWorkflowId} / ${activityInfo.getRunId}]"
    log.info(s"$wid Starting PoO with ${input.wallets.size} wallets, proof type: ${input.proofType}")

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

    log.info(s"$wid Completed PoO with ${proofs.size} proofs")
    output
  }
}
