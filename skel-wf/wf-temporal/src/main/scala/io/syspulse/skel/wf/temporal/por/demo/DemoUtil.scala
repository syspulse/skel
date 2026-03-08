package io.syspulse.skel.wf.temporal.por.demo

import io.syspulse.skel.wf.temporal.por.Wallet
import scala.util.Random

object DemoUtil {

  /**
   * Generate mock wallets for demo purposes
   * Creates wallets across different blockchain networks with random balances
   */
  def generateMockWallets(): List[Wallet] = {
    List(
      Wallet("0x1234567890abcdef1234567890abcdef12345678", "Ethereum", BigInt(Random.nextInt(1000)) * BigInt(10).pow(18)),
      Wallet("bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh", "Bitcoin", BigInt(Random.nextInt(100)) * BigInt(10).pow(8)),
      Wallet("0xabcdef1234567890abcdef1234567890abcdef12", "Arbitrum", BigInt(Random.nextInt(500)) * BigInt(10).pow(18)),
      Wallet("9n4NBfQSKMbPDf6xJzewJ5V1Z9v5KnPBkJKHTgV7aoqd", "Solana", BigInt(Random.nextInt(2000)) * BigInt(10).pow(9)),
      Wallet("TXYZupQdGeRgKaFwNTjJFiJNdMdnXBx3K4", "Tron", BigInt(Random.nextInt(10000)) * BigInt(10).pow(6))
    )
  }
}
