package io.syspulse.skel.wf.temporal.por.demo

import scala.util.Random
import io.temporal.activity.Activity
import com.typesafe.scalalogging.Logger
import io.syspulse.skel.wf.temporal.por._

class PorActivityDemo {
  private val log = Logger(getClass.getName)

  private def simulateWork(minSeconds: Int = 1, maxSeconds: Int = 3): Unit = {
    val delay = (Random.nextInt(maxSeconds - minSeconds + 1) + minSeconds) * 1000
    Thread.sleep(delay)
  }

  def execute(input: PorInput): PorOutput = {
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
}
