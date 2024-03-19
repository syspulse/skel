package io.syspulse.crypto.eth.abi3

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
//import org.scalatest.TryValues._
import scala.io.Source

import io.syspulse.skel.util.Util
import io.syspulse.skel.crypto.Hash

import codegen.Decoder
import codegen.AbiDefinition
import io.syspulse.crypto.eth.Tx

import io.syspulse.skel.crypto.eth.abi._
import scala.util.Success
import io.syspulse.skel.crypto.eth.abi3._

class AbiSpec extends AnyWordSpec with Matchers {
  val testDir = this.getClass.getClassLoader.getResource(".").getPath + "../../../"

  info(s"testDir=${testDir}")
  
  "Abi should parse simple function" in {  
    val abi1 = """{"name":"decimals","type":"function"}"""
    val a = Abi.parseDef(abi1)
    a should === (AbiDef(name="decimals",`type`="function"))
  }

  "Abi should load ERC20 decimals() function" in {
    val abi1 = """
  {
    "constant": true,
    "inputs": [],
    "name": "decimals",
    "outputs": [
      {
        "name": "",
        "type": "uint256"
      }
    ],
    "payable": false,
    "stateMutability": "view",
    "type": "function"
  }
  """

    val a = Abi.parseDef(abi1)
    a should === (AbiDef(
      name="decimals",
      `type`="function",
      constant=Some(true), 
      inputs=Some(Seq()), 
      outputs=Some(Seq( AbiType(name="",`type`="uint256") )), 
      payable=Some(false),
      stateMutability=Some("view")
    ))
  }

  "parse ERC20 Contract" in {
    val contract = "0x1111111111111111111111111111111111111111"

    val abi1 = """[
        {
          "anonymous": false,
          "inputs": [
            {
              "indexed": true,
              "internalType": "address",
              "name": "owner",
              "type": "address"
            },
            {
              "indexed": true,
              "internalType": "address",
              "name": "spender",
              "type": "address"
            },
            {
              "indexed": false,
              "internalType": "uint256",
              "name": "amount",
              "type": "uint256"
            }
          ],
          "name": "Approval",
          "type": "event"
        },    
        {
          "anonymous": false,
          "inputs": [
            {
              "indexed": true,
              "internalType": "address",
              "name": "from",
              "type": "address"
            },
            {
              "indexed": true,
              "internalType": "address",
              "name": "to",
              "type": "address"
            },
            {
              "indexed": false,
              "internalType": "uint256",
              "name": "amount",
              "type": "uint256"
            }
          ],
          "name": "Transfer",
          "type": "event"
        }    
      ]"""  

    val a = Abi.parse(abi1)
    info(s"ERC20 = ${a}") 
  }
}