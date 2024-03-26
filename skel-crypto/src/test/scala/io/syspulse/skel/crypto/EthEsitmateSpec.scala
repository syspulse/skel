package io.syspulse.skel.crypto

import scala.util.{Try,Success,Failure}

import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import java.time._
import io.syspulse.skel.util.Util

class EthEstimateSpec extends AnyWordSpec with Matchers with TestData {

  implicit val web3 = Eth.web3(rpcUri="https://eth.llamarpc.com")

  val USDT = "0xdAC17F958D2ee523a2206206994597C13D831ec7"
  val USDT_SEPOLIA = "0xaA8E23Fb1079EA71e0a56F48a2aA51851D8433D0"

  val ABI_ERC20 = """
  [
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
    },
    {
      "constant": true,
      "inputs": [
        {
          "internalType": "address",
          "name": "account",
          "type": "address"
        }
      ],
      "name": "balanceOf",
      "outputs": [
        {
          "internalType": "uint256",
          "name": "",
          "type": "uint256"
        }
      ],
      "payable": false,
      "stateMutability": "view",
      "type": "function"
    },
    {
      "constant": false,
      "inputs": [
        {
          "name": "_to",
          "type": "address"
        },
        {
          "name": "_value",
          "type": "uint256"
        }
      ],
      "name": "transfer",
      "outputs": [],
      "payable": false,
      "stateMutability": "nonpayable",
      "type": "function"
    },
    {
      "constant": false,
      "inputs": [
        {
          "internalType": "address",
          "name": "spender",
          "type": "address"
        },
        {
          "internalType": "uint256",
          "name": "rawAmount",
          "type": "uint256"
        }
      ],
      "name": "approve",
      "outputs": [
        {
          "internalType": "bool",
          "name": "",
          "type": "bool"
        }
      ],
      "payable": false,
      "stateMutability": "nonpayable",
      "type": "function"
    },
    {
      "constant": false,
      "inputs": [
        {
          "name": "_evilUser",
          "type": "address"
        }
      ],
      "name": "addBlackList",
      "outputs": [],
      "payable": false,
      "stateMutability": "nonpayable",
      "type": "function"
    }
  ]
  """

  // Run 'anvil' befire running tests !
  "EthEstimateSpec" should {
    
    // "estimate gas for decimals():uint256" in {
    //   val r = Eth.estimateFunc(
    //     "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266",
    //     USDT,
    //     "decimals",
    //     ABI_ERC20,
    //     Seq()
    //   )
      
    //   info(s"r=${r}")
    //   r.isSuccess should === (true)
    // }

    // "estimate gas for balanceOf(addr):uint256" in {
    //   val r = Eth.estimateFunc(
    //     "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266",
    //     USDT,
    //     "balanceOf",
    //     ABI_ERC20,
    //     Seq("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266")
    //   )
      
    //   info(s"r=${r}")
    //   r.isSuccess should === (true)
    // }

    "estimate gas for approve(address,value)" in {
      val r = Eth.estimateFunc(
        "0xe20005735dC18CC809308B845eccF0f1c6a017f8",
        USDT,
        "approve",
        ABI_ERC20,
        Seq("0xdac17f958d2ee523a2206206994597c13d831ec7",BigInt(10).bigInteger)
      )
      
      info(s"r=${r}")
      r.isSuccess should === (true)
    }
    
    // "estimate gas for trasfer(address,value) for USDC -> USDT Token contracts" in {
    //   val r = Eth.estimateFunc(
    //     "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48",
    //     USDT,
    //     "transfer",
    //     ABI_ERC20,
    //     Seq("0xdac17f958d2ee523a2206206994597c13d831ec7",BigInt(10).bigInteger)
    //   )
      
    //   info(s"r=${r}")
    //   r.isSuccess should === (true)
    // }
    
  }
}
