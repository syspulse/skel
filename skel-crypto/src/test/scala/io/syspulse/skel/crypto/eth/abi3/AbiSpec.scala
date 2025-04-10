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
  val testDir = this.getClass.getClassLoader.getResource(".").getPath 

  info(s"testDir=${testDir}")

  val ABI_ERC20 = """[
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
  
  val ABI_ERC20_1 = Source.fromFile(testDir + "ABI_ERC20_1.json").mkString

  "Abi should parse simple function" in {  
    val abi1 = """{"name":"decimals","type":"function"}"""
    val a = Abi.parseDef(abi1)
    a should === (AbiDef(name=Some("decimals"),`type`="function"))
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
      name=Some("decimals"),
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
    
    val a = Abi.parse(ABI_ERC20)
    //
  }

  "parse trasfer() function" in {    
    val a = Abi.parse(ABI_ERC20_1)
    val f = a.getFunctionCall("transfer")
    f.get should === ("transfer(address,uint256)(bool)")
  }

  "parse name() function" in {
    val a = Abi.parse(ABI_ERC20_1)
    val f = a.getFunctionCall("name()")
    f.get should === ("name()(string)")
  }

  "parse name( ) function" in {
    val a = Abi.parse(ABI_ERC20_1)
    val f = a.getFunctionCall("name( )")
    f.get should === ("name()(string)")
  }

  "parse 'name  ( )' function" in {
    val a = Abi.parse(ABI_ERC20_1)
    val f = a.getFunctionCall("name  ( )")
    f.get should === ("name()(string)")
  }

  "parse 'name' function as name()" in {
    val a = Abi.parse(ABI_ERC20_1)
    val f = a.getFunctionCall("name")
    f.get should === ("name()(string)")
  }

  "merge 2 ABIs" in {
    val abi1 = """
  [{
    "constant": true,  "inputs": [], "name": "function1", "outputs": [ { "name": "", "type": "uint256" }], "payable": false,"stateMutability": "view","type": "function" 
  }] """
    val abi2 = """
  [{
    "constant": true,  "inputs": [], "name": "function2", "outputs": [ { "name": "", "type": "string" }], "payable": false,"stateMutability": "view","type": "function" 
  }] """

    val a1 = Abi(abi1)
    val a2 = Abi(abi2)
    val a3 = a1 ++ a2
    a3.getDefinitions().size should === (2)
    a3.getFunctions().size should === (2)
    a3.getEvents().size should === (0)
    a3.getConstructors().size should === (0)
    a3.getFunctionCall("function1").get should === ("function1()(uint256)")
    a3.getFunctionCall("function2").get should === ("function2()(string)")    
  }
  
}