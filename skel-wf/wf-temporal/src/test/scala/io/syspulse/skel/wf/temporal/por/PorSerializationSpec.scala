package io.syspulse.skel.wf.temporal.por

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import io.syspulse.skel.wf.temporal.ScalaDataConverter
import java.util.UUID

class PorSerializationSpec extends AnyWordSpec with Matchers {

  val dataConverter = ScalaDataConverter.create()

  "PorSerialization" should {

    "serialize and deserialize Wallet" in {
      val wallet = Wallet(
        address = "0x1234567890abcdef1234567890abcdef12345678",
        network = "Ethereum",
        balance = BigInt("451000000000000000000")
      )

      val payload = dataConverter.toPayload(wallet).get()
      val deserialized = dataConverter.fromPayload(payload, classOf[Wallet], classOf[Wallet])

      deserialized should === (wallet)
    }

    "serialize and deserialize PooInput with List of Wallets" in {
      val pooInput = PooInput(
        wallets = List(
          Wallet("0x1234567890abcdef1234567890abcdef12345678", "Ethereum", BigInt("451000000000000000000")),
          Wallet("0xabcdef1234567890abcdef1234567890abcdef12", "Ethereum", BigInt("200000000000000000000"))
        ),
        proofType = "signature"
      )

      val payload = dataConverter.toPayload(pooInput).get()
      val deserialized = dataConverter.fromPayload(payload, classOf[PooInput], classOf[PooInput])

      deserialized should === (pooInput)
      deserialized.wallets should have size 2
      deserialized.proofType should === ("signature")
    }

    "serialize and deserialize PorInput" in {
      val porInput = PorInput(
        wallets = List(
          Wallet("0x1234567890abcdef1234567890abcdef12345678", "Ethereum", BigInt("451000000000000000000"))
        ),
        assets = List("ETH", "BTC", "LINK")
      )

      val payload = dataConverter.toPayload(porInput).get()
      val deserialized = dataConverter.fromPayload(payload, classOf[PorInput], classOf[PorInput])

      deserialized should === (porInput)
      deserialized.wallets should have size 1
      deserialized.assets should === (List("ETH", "BTC", "LINK"))
    }

    "serialize and deserialize PolInput" in {
      val polInput = PolInput(
        fileLink = "s3://bucket/liabilities.json",
        waitForConfirmation = true
      )

      val payload = dataConverter.toPayload(polInput).get()
      val deserialized = dataConverter.fromPayload(payload, classOf[PolInput], classOf[PolInput])

      deserialized should === (polInput)
      deserialized.waitForConfirmation should be (true)
    }

    "serialize and deserialize PorWorkflowInput" in {
      val workflowInput = PorWorkflowInput(
        cexName = "TestExchange",
        timestamp = System.currentTimeMillis(),
        pooRequired = true,
        porRequired = true,
        polRequired = true,
        reportRequired = true
      )

      val payload = dataConverter.toPayload(workflowInput).get()
      val deserialized = dataConverter.fromPayload(payload, classOf[PorWorkflowInput], classOf[PorWorkflowInput])

      deserialized.cexName should === ("TestExchange")
      deserialized.pooRequired should be (true)
    }

    "serialize and deserialize WalletWithAsset" in {
      val walletWithAsset = WalletWithAsset(
        address = "0x1234567890abcdef1234567890abcdef12345678",
        network = "Ethereum",
        asset = "ETH",
        balance = BigInt("451000000000000000000")
      )

      val payload = dataConverter.toPayload(walletWithAsset).get()
      val deserialized = dataConverter.fromPayload(payload, classOf[WalletWithAsset], classOf[WalletWithAsset])

      deserialized should === (walletWithAsset)
      deserialized.asset should === ("ETH")
    }

    "serialize and deserialize Liability with UUID" in {
      val userId = UUID.randomUUID()
      val liability = Liability(
        userId = userId,
        asset = "ETH",
        balance = BigInt("100000000000000000000")
      )

      val payload = dataConverter.toPayload(liability).get()
      val deserialized = dataConverter.fromPayload(payload, classOf[Liability], classOf[Liability])

      deserialized should === (liability)
      deserialized.userId should === (userId)
    }

    "serialize and deserialize PooOutput with Map" in {
      val pooOutput = PooOutput(
        timestamp = System.currentTimeMillis(),
        proofs = Map(
          "0x1234567890abcdef1234567890abcdef12345678" -> "0xabcdef1234567890",
          "0xabcdef1234567890abcdef1234567890abcdef12" -> "0x1234567890abcdef"
        )
      )

      val payload = dataConverter.toPayload(pooOutput).get()
      val deserialized = dataConverter.fromPayload(payload, classOf[PooOutput], classOf[PooOutput])

      deserialized.proofs should have size 2
      deserialized.proofs should contain key "0x1234567890abcdef1234567890abcdef12345678"
    }

    "serialize and deserialize PorOutput with nested case classes" in {
      val porOutput = PorOutput(
        timestamp = System.currentTimeMillis(),
        balances = List(
          WalletWithAsset("0x1234", "Ethereum", "ETH", BigInt("100000000000000000000")),
          WalletWithAsset("0x5678", "Ethereum", "BTC", BigInt("200000000000000000000"))
        )
      )

      val payload = dataConverter.toPayload(porOutput).get()
      val deserialized = dataConverter.fromPayload(payload, classOf[PorOutput], classOf[PorOutput])

      deserialized.balances should have size 2
      deserialized.balances.head.asset should === ("ETH")
      deserialized.balances(1).asset should === ("BTC")
    }

    "serialize and deserialize PolOutput" in {
      val polOutput = PolOutput(
        timestamp = System.currentTimeMillis(),
        liabilities = List(
          Liability(UUID.randomUUID(), "ETH", BigInt("100000000000000000000")),
          Liability(UUID.randomUUID(), "BTC", BigInt("200000000000000000000"))
        ),
        signature = "0xabc123",
        signatureType = "public_key",
        publicKey = "0xdef456"
      )

      val payload = dataConverter.toPayload(polOutput).get()
      val deserialized = dataConverter.fromPayload(payload, classOf[PolOutput], classOf[PolOutput])

      deserialized.liabilities should have size 2
      deserialized.signatureType should === ("public_key")
    }

    "serialize and deserialize SolvencyOutput with BigDecimal" in {
      val solvencyOutput = SolvencyOutput(
        porTotalUsd = BigDecimal("1000000.50"),
        polTotalUsd = BigDecimal("800000.25"),
        solvencyRatio = BigDecimal("0.80")
      )

      val payload = dataConverter.toPayload(solvencyOutput).get()
      val deserialized = dataConverter.fromPayload(payload, classOf[SolvencyOutput], classOf[SolvencyOutput])

      deserialized.porTotalUsd should === (BigDecimal("1000000.50"))
      deserialized.polTotalUsd should === (BigDecimal("800000.25"))
      deserialized.solvencyRatio should === (BigDecimal("0.80"))
    }

    "serialize and deserialize ReportOutput" in {
      val reportOutput = ReportOutput(
        reportFilePath = "/tmp/report.md",
        reportLink = "https://example.com/report.md"
      )

      val payload = dataConverter.toPayload(reportOutput).get()
      val deserialized = dataConverter.fromPayload(payload, classOf[ReportOutput], classOf[ReportOutput])

      deserialized should === (reportOutput)
    }
  }
}
