package io.syspulse.skel.crypto.tool

import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.util.Util
import io.syspulse.skel.config._

import io.syspulse.skel.crypto.Eth
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import org.web3j.abi.datatypes.generated.Uint256
import java.util.Arrays
import org.web3j.abi.TypeReference
import io.syspulse.skel.crypto.eth.Solidity
import io.syspulse.skel.crypto.eth.SolidityTuple
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.concurrent.Await

object AppEvm extends {
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
  import io.syspulse.skel.FutureAwaitable._

  case class Config(
    ethRpcUrl:String="http://geth:8545",
    from:String = "0x0000000000000000000000000000000000000000",
    block:Option[Long] = None,

    delay:Long = 0L,
    
    cmd:String = "call",
    params:Seq[String] = Seq()
  )
  
  def main(args:Array[String]):Unit = {
    Console.err.println(s"args: ${args.size}: ${args.toSeq}")
    
    val d = Config()
    val c = Configuration.withPriority(Seq(
      new ConfigurationAkka,
      new ConfigurationProp,
      new ConfigurationEnv, 
      new ConfigurationArgs(args,"eth-evm","",
        
        ArgString('r', "eth.rpc.url",s"RPC uri (def: ${d.ethRpcUrl})"),
        ArgString('f', "from",s"From address (def: ${d.from})"),
        ArgString('b', "block",s"Block number (def: ${d.block})"),

        ArgLong('_', "delay",s"Delay in ms (def: ${d.delay})"),
        
        ArgCmd("call","eth_call: [address] function(params,...)(return)"),
        ArgCmd("encode","function(params,...)(return)"),
        ArgCmd("estimate","eth_estimageGas"),
        ArgCmd("balance","eth_getBalance"),
        ArgCmd("balance-erc20","ERC20 balanceOf()"),
        ArgCmd("encode-data","inputType params..."),
        ArgCmd("decode-data","inputType params..."),
        ArgCmd("block","Get block"),

        ArgParam("<params>","..."),

        ArgLogging(),
        ArgConfig(),
      ).withExit(1)
    )).withLogging()
    
    val config = Config(
      ethRpcUrl = c.getString("eth.rpc.url").getOrElse(d.ethRpcUrl),
      from = c.getString("from").getOrElse(d.from),
      block = c.getString("block").map(b => b.toLong),

      delay = c.getLong("delay").getOrElse(d.delay),

      cmd = c.getCmd().getOrElse(d.cmd),
      params = c.getParams()
    )

    Console.err.println(s"Config: ${config}")

    implicit val web3:Web3j = Web3j.build(new HttpService(config.ethRpcUrl))

    Console.err.println(s"web3j: ${web3}")

    val r =config.cmd match {
      case "call" => 
        if(config.params.size < 2) {
          Console.err.println("call: <contract> <function> [params..]")
          sys.exit(1)
        }

        val contractAddress = config.params(0)
        val funcName = config.params(1)
        val params = config.params.drop(2)
        val paramsStr = params.mkString(" ")
          
        //Eth.callFunctionWithParams(config.from,contractAddress,funcName,paramsStr)
        Eth.callFunction(config.from,contractAddress,funcName,params,block=config.block)
              
      case "encode" => 
        if(config.params.size < 1) {
          Console.err.println("encode: <function> [params..]")
          sys.exit(1)
        }

        val funcName = config.params(0)
        val params = config.params.drop(1)
        
        Eth.encodeFunction(funcName,params)

      case "estimate" => 
        if(config.params.size < 2) {
          Console.err.println("estimate: <contract> <function> [params..]")
          sys.exit(1)
        }

        val contractAddress = config.params(0)
        val funcName = config.params(1)
        val params = config.params.drop(2)
        
        Console.err.println(s"params=${params}")
        
        Eth.estimate(config.from,contractAddress,funcName,params)

      case "balance" => 
        if(config.params.size < 1) {
          Console.err.println("balance: <address>")
          sys.exit(1)
        }
        Eth.getBalance(config.params(0),block=config.block)

      case "balance-erc20" | "balance-token" =>         

        if(config.params.size < 2) {
          Console.err.println("balance-erc20: <address> <token>...")
          sys.exit(1)
        }
        //Eth.getBalanceToken(config.params(0),config.params.drop(1))
        
        Eth.getBalanceTokenAsync(config.params(0),config.params.drop(1),delay=config.delay,block=config.block)
          .map(r => {
            Console.err.println(s"r=${r}")
            r
          })
          //.await()

      case "encode-data" => 
        if(config.params.size < 1) {
          Console.err.println("encode-data: type [params..]")
          sys.exit(1)
        }

        val typ = config.params(0)
        val params = config.params.drop(1)
        
        Solidity.encodeData(typ,params)

      case "decode-data" => 
        if(config.params.size < 1) {
          Console.err.println("decode-data: type [params..]")
          sys.exit(1)
        }

        val typ = config.params(0)
        val params = config.params.last
        
        //Solidity.decodeData(typ,params)
        SolidityTuple.decodeData(typ,params)

      case "block" =>         

        val block = config.params.headOption.map(_.toLong)
        val withTx = config.params.lastOption
        
        Eth.getBlock(block,withTx.isDefined)
          .map(b => {
            s"block=${b.getNumber()},timestamp=${b.getTimestamp()},hash=${b.getHash()},tx=${b.getTransactions().size()}"
          })
    }
    
    Console.err.println(s"r = ${r}")

    if(r.isInstanceOf[Future[_]]) {
      r.asInstanceOf[Future[_]].await()
    }
    
  }
}
