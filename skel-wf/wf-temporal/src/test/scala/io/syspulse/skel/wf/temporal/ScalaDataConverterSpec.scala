package io.syspulse.skel.wf.temporal

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import io.syspulse.skel.wf.temporal.por._
import java.util.UUID

class ScalaDataConverterSpec extends AnyWordSpec with Matchers {

  "ScalaDataConverter" should {

    "create ObjectMapper with Scala module" in {
      val mapper = ScalaDataConverter.createObjectMapper()

      mapper should not be null
      // Verify Scala module is registered by checking it can handle case classes
      val wallet = Wallet("0xtest", "Ethereum", BigInt(100))
      val json = mapper.writeValueAsString(wallet)
      json should include ("0xtest")
      json should include ("Ethereum")
    }

    "create DataConverter instance" in {
      val dataConverter = ScalaDataConverter.create()

      dataConverter should not be null
    }

    "serialize and deserialize simple case class" in {
      val dataConverter = ScalaDataConverter.create()

      val wallet = Wallet(
        address = "0x1234567890abcdef1234567890abcdef12345678",
        network = "Ethereum",
        balance = BigInt("451000000000000000000")
      )

      val payload = dataConverter.toPayload(wallet).get()
      payload should not be null
      payload.getData.size() should be > 0

      val deserialized = dataConverter.fromPayload(payload, classOf[Wallet], classOf[Wallet])
      deserialized should === (wallet)
    }

    "serialize and deserialize case class with List" in {
      val dataConverter = ScalaDataConverter.create()

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
      deserialized.wallets.head.address should === ("0x1234567890abcdef1234567890abcdef12345678")
    }

    "serialize and deserialize case class with Map" in {
      val dataConverter = ScalaDataConverter.create()

      val pooOutput = PooOutput(
        timestamp = System.currentTimeMillis(),
        proofs = Map(
          "0x1234567890abcdef1234567890abcdef12345678" -> "0xabcdef1234567890",
          "0xabcdef1234567890abcdef1234567890abcdef12" -> "0x1234567890abcdef"
        )
      )

      val payload = dataConverter.toPayload(pooOutput).get()
      val deserialized = dataConverter.fromPayload(payload, classOf[PooOutput], classOf[PooOutput])

      deserialized.timestamp should === (pooOutput.timestamp)
      deserialized.proofs should have size 2
      deserialized.proofs("0x1234567890abcdef1234567890abcdef12345678") should === ("0xabcdef1234567890")
    }

    "serialize and deserialize case class with UUID" in {
      val dataConverter = ScalaDataConverter.create()

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

    "handle BigInt serialization correctly" in {
      val dataConverter = ScalaDataConverter.create()

      val largeBalance = BigInt("999999999999999999999999999999")
      val wallet = Wallet("0xtest", "Ethereum", largeBalance)

      val payload = dataConverter.toPayload(wallet).get()
      val deserialized = dataConverter.fromPayload(payload, classOf[Wallet], classOf[Wallet])

      deserialized.balance should === (largeBalance)
    }
  }
}
