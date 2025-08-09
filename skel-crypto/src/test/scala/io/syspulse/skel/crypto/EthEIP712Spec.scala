package io.syspulse.skel.crypto

import scala.util.{Try,Success,Failure}
import scala.jdk.CollectionConverters._

import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import java.time._
import io.syspulse.skel.util.Util

class EthEIP712Spec extends AnyWordSpec with Matchers {

  val typedData1 =
        """
          |{
          |  "types": {
          |    "EIP712Domain": [
          |      {"name": "name", "type": "string"},
          |      {"name": "version", "type": "string"},
          |      {"name": "chainId", "type": "uint256"},
          |      {"name": "verifyingContract", "type": "address"}
          |    ],
          |    "Person": [
          |      {"name": "name", "type": "string"},
          |      {"name": "wallet", "type": "address"}
          |    ],
          |    "Mail": [
          |      {"name": "from", "type": "Person"},
          |      {"name": "to", "type": "Person"},
          |      {"name": "contents", "type": "string"}
          |    ]
          |  },
          |  "primaryType": "Mail",
          |  "domain": {
          |    "name": "Ether Mail",
          |    "version": "1",
          |    "chainId": 1,
          |    "verifyingContract": "0xCcCCccccCCCCcCCCCCCcCcCccCcCCCcCcccccccC"
          |  },
          |  "message": {
          |    "from": {
          |      "name": "Cow",
          |      "wallet": "0xCD2a3d9F938E13CD947Ec05AbC7FE734Df8DD826"
          |    },
          |    "to": {
          |      "name": "Bob",
          |      "wallet": "0xbBbBBBBbbBBBbbbBbbBbbbbBBbBbbbbBbBbbBBbB"
          |    },
          |    "contents": "Hello, Bob!"
          |  }
          |}
          |""".stripMargin

  val typedData2 = """
    {
  "types": {
    "EIP712Domain": [
      {
        "name": "name",
        "type": "string"
      },
      {
        "name": "version",
        "type": "string"
      },
      {
        "name": "chainId",
        "type": "uint256"
      },
      {
        "name": "verifyingContract",
        "type": "address"
      }
    ],
    "grantProviderRoleToContract": [
      {
        "name": "monitoredContract",
        "type": "address"
      },
      {
        "name": "provider",
        "type": "address"
      },
      {
        "name": "nonce",
        "type": "uint256"
      }
    ]
  },
  "primaryType": "grantProviderRoleToContract",
  "domain": {
    "chainId": 31337,
    "name": "Firewall Contract",
    "verifyingContract": "0x0000000000000000000000000000000000000999",
    "version": "1.0.0",
    "salt": null
  },
  "message": {
    "monitoredContract": "0x1111111111111111111111111111111111111111",
    "nonce": 7013,
    "provider": "0x2222222222222222222222222222222222222222"
  }
}
"""

  "Eth.signEIP712" should {
    "produce a valid signature for known EIP-712 typed data and private key" in {
      

      val privateKey = "0x0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
      val signature = Eth.signEIP712(typedData1, privateKey)

      // Signature should be 0x-prefixed and 65 bytes (130 hex chars + 2 for 0x)
      signature should startWith ("0x")
      signature.length shouldBe 132

      // Optionally, check that the signature is deterministic for the same input
      val signature2 = Eth.signEIP712(typedData1, privateKey)
      signature shouldBe signature2

      val signature3 = Eth.signEIP712(typedData2, privateKey)
      signature3 should startWith ("0x")
      signature3.length shouldBe 132

    }

    "throw for invalid JSON" in {
      val invalidJson = """{ "foo": "bar" }"""
      val privateKey = "0x0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
      an [Exception] should be thrownBy Eth.signEIP712(invalidJson, privateKey)
    }

    "throw for invalid private key" in {
      val typedData = """{ "types": {}, "primaryType": "", "domain": {}, "message": {} }"""
      val invalidKey = "0x123"
      an [Exception] should be thrownBy Eth.signEIP712(typedData, invalidKey)
    }

    "generate same signature for different input methods" in {
      // Test data
      
      val name = "Ether Mail"
      val version = "1"
      val chainId = 1
      val verifyingContract = "0xCcCCccccCCCCcCCCCCCcCcCccCcCCCcCcccccccC"
      val salt = None

      val types = Map(
        "EIP712Domain" -> List(
          Map("name" -> "name", "type" -> "string"),
          Map("name" -> "version", "type" -> "string"),
          Map("name" -> "chainId", "type" -> "uint256"),
          Map("name" -> "verifyingContract", "type" -> "address")
        ),
        "Person" -> List(
          Map("name" -> "name", "type" -> "string"),
          Map("name" -> "wallet", "type" -> "address")
        ),
        "Mail" -> List(
          Map("name" -> "from", "type" -> "Person"),
          Map("name" -> "to", "type" -> "Person"),
          Map("name" -> "contents", "type" -> "string")
        )
      )

      val value = Map(
        "from" -> Map(
          "name" -> "Cow",
          "wallet" -> "0xCD2a3d9F938E13CD947Ec05AbC7FE734Df8DD826"
        ),
        "to" -> Map(
          "name" -> "Bob",
          "wallet" -> "0xbBbBBBBbbBBBbbbBbbBbbbbBBbBbbbbBbBbbBBbB"
        ),
        "contents" -> "Hello, Bob!"
      )

      val privateKey = "0x0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

      // Get signatures from different methods
      val sig1 = Eth.signEIP712(typedData1, privateKey)
      val sig2 = Eth.signEIP712(name, version, chainId, verifyingContract, salt, types, value, "Mail", privateKey)

      // Verify signatures are identical
      sig1 shouldBe sig2

      // Verify signature format
      sig1 should startWith("0x")
      sig1 should have length 132
    }

    "handle nested types correctly" in {
      val name = "NestedTest"
      val version = "1"
      val chainId = 1
      val verifyingContract = "0xCcCCccccCCCCcCCCCCCcCcCccCcCCCcCcccccccC"
      val salt = None

      val types = Map(
        "Level1" -> List(
          Map("name" -> "level2", "type" -> "Level2")
        ),
        "Level2" -> List(
          Map("name" -> "level3", "type" -> "Level3")
        ),
        "Level3" -> List(
          Map("name" -> "value", "type" -> "string")
        )
      )

      val value = Map(
        "level2" -> Map(
          "level3" -> Map(
            "value" -> "deeply nested"
          )
        )
      )

      val privateKey = "0x0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
      val sig = Eth.signEIP712(name, version, chainId, verifyingContract, salt, types, value, "Level1", privateKey)
      sig should startWith("0x")
      sig should have length 132
    }

    "handle array types" in {
      val name = "ArrayTest"
      val version = "1"
      val chainId = 1
      val verifyingContract = "0xCcCCccccCCCCcCCCCCCcCcCccCcCCCcCcccccccC"
      val salt = None

      val types = Map(
        "ArrayType" -> List(
          Map("name" -> "items", "type" -> "string[]")
        )
      )

      val value = Map(
        "items" -> List("item1", "item2", "item3")
      )

      val privateKey = "0x0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
      val sig = Eth.signEIP712(name, version, chainId, verifyingContract, salt, types, value, "ArrayType", privateKey)
      sig should startWith("0x")
      sig should have length 132
    }

    "be compatible with ext-firewall" in {
      // Test data
      
      val name = "Ether Mail"
      val version = "1"
      val chainId = 1
      val verifyingContract = "0xCcCCccccCCCCcCCCCCCcCcCccCcCCCcCcccccccC"
      val salt = None

      val types = Map(
        "EIP712Domain" -> List(
          Map("name" -> "name", "type" -> "string"),
          Map("name" -> "version", "type" -> "string"),
          Map("name" -> "chainId", "type" -> "uint256"),
          Map("name" -> "verifyingContract", "type" -> "address")
        ),
        "Person" -> List(
          Map("name" -> "name", "type" -> "string"),
          Map("name" -> "wallet", "type" -> "address")
        ),
        "Mail" -> List(
          Map("name" -> "from", "type" -> "Person"),
          Map("name" -> "to", "type" -> "Person"),
          Map("name" -> "contents", "type" -> "string")
        )
      )

      val value = Map(
        "from" -> Map(
          "name" -> "Cow",
          "wallet" -> "0xCD2a3d9F938E13CD947Ec05AbC7FE734Df8DD826"
        ),
        "to" -> Map(
          "name" -> "Bob",
          "wallet" -> "0xbBbBBBBbbBBBbbbBbbBbbbbBBbBbbbbBbBbbBBbB"
        ),
        "contents" -> "Hello, Bob!"
      )

      val privateKey = "0x0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

      // Get signatures from different methods
      val sig1 = Eth.signEIP712(typedData1, privateKey)
      val sig2 = Eth.signEIP712(name, version, chainId, verifyingContract, salt, types, value, "Mail", privateKey)

      // Verify signatures are identical
      sig1 shouldBe sig2

      // Verify signature format
      sig1 should startWith("0x")
      sig1 should have length 132
    }
  }
  
}

