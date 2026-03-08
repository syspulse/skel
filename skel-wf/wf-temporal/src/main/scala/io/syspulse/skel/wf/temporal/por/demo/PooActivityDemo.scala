package io.syspulse.skel.wf.temporal.por.demo

import scala.util.Random
import io.temporal.activity.Activity
import com.typesafe.scalalogging.Logger
import io.syspulse.skel.wf.temporal.por._

class PooActivityDemo {
  private val log = Logger(getClass.getName)

  def execute(run: PorWorkflowRun): PorWorkflowRun = {
    val activityInfo = Activity.getExecutionContext.getInfo
    val wid = s"[${activityInfo.getWorkflowId} / ${activityInfo.getRunId}]"

    run.input.poo.input match {
      case None =>
        log.warn(s"$wid PoO: No input provided, returning run unchanged")
        run

      case Some(input) =>
        log.info(s"$wid Starting PoO with ${input.wallets.size} wallets, proof type: ${input.proofType}")

        PorActivitiesDemo.simulateWork(1, 3)

        // Generate mock proofs (signatures or transaction hashes)
        val newProofs = input.wallets.map { wallet =>
          val proof = input.proofType match {
            case "signature" => s"0x${Random.alphanumeric.take(128).mkString}"
            case "transaction_hash" => s"0x${Random.alphanumeric.take(64).mkString}"
            case _ => s"0x${Random.alphanumeric.take(64).mkString}"
          }
          wallet.address -> proof
        }.toMap

        // PoO MERGE STRATEGY: Always trust previous outputs and merge
        val mergedProofs = run.output.poo match {
          case Some(previousOutput) =>
            log.info(s"$wid PoO: Merging ${newProofs.size} new proofs with ${previousOutput.proofs.size} previous proofs")
            previousOutput.proofs ++ newProofs // New proofs override previous ones with same key
          case None =>
            newProofs
        }

        val output = PooOutput(
          ts = System.currentTimeMillis(),
          proofs = mergedProofs
        )

        log.info(s"$wid Completed PoO with ${mergedProofs.size} total proofs")
        run.copy(output = run.output.copy(poo = Some(output)))
    }
  }
}
