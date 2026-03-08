package io.syspulse.skel.wf.temporal.por.demo

import scala.util.Random
import io.temporal.activity.Activity
import com.typesafe.scalalogging.Logger
import io.syspulse.skel.wf.temporal.por._

class PorActivityDemo {
  private val log = Logger(getClass.getName)
  
  def execute(run: PorWorkflowRun): PorWorkflowRun = {
    val activityInfo = Activity.getExecutionContext.getInfo
    val wid = s"[${activityInfo.getWorkflowId} / ${activityInfo.getRunId}]"

    run.input.por.flatMap(_.input) match {
      case None =>
        log.warn(s"$wid PoR: No input provided, returning run unchanged")
        run

      case Some(input) =>
        log.info(s"$wid Starting PoR with ${input.wallets.size} wallets and ${input.assets.size} assets")

        PorActivitiesDemo.simulateWork(1, 3)

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

        // PoR MERGE STRATEGY: Never trust previous outputs, always use fresh input as output
        run.output.por.foreach { previousOutput =>
          log.info(s"$wid PoR: Ignoring previous output (${previousOutput.balances.size} entries), using fresh data")
        }

        val output = PorOutput(
          ts = System.currentTimeMillis(),
          balances = balances.toList
        )

        log.info(s"$wid Completed PoR with ${balances.size} balance entries")
        run.copy(output = run.output.copy(por = Some(output)))
    }
  }
}
