package io.syspulse.skel.crypto.tool

import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.Logger
import scala.jdk.CollectionConverters._

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
import io.syspulse.skel.crypto.eth.Web3jTrace

object AppEvm extends {
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
  import io.syspulse.skel.FutureAwaitable._

  case class Config(
    ethRpcUrl:String="http://geth:8545",
    from:String = "0x0000000000000000000000000000000000000000",
    block:Option[Long] = None,
    
    tracer:String = "callTracer",
    tracerConfig:Map[String,String] = Map(),

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
        
        ArgString('_', "tracer",s"Tracer ([callTracer,prestateTracer] def: ${d.tracer})"),
        ArgString('_', "tracerConfig",s"Tracer config callTracer: [onlyTopCall=true, withLog=false], prestateTracer: [diffMode=true,disableStorage=true,disableCode=true,enableCode=false] (def: ${d.tracerConfig})"),

        ArgLong('_', "delay",s"Delay in ms (def: ${d.delay})"),
        
        ArgCmd("call","eth_call: [address] function(params,...)(return)"),
        ArgCmd("call-async","eth_call: [address] function(params,...)(return)"),
        ArgCmd("encode","function(params,...)(return)"),
        ArgCmd("estimate","eth_estimageGas"),
        ArgCmd("balance","eth_getBalance"),
        ArgCmd("balance-erc20","ERC20 balanceOf()"),
        ArgCmd("abi-encode","inputType params..."),
        ArgCmd("abi-decode","inputType params..."),
        ArgCmd("block","Get block"),
        ArgCmd("call-trace","debug_traceCall"),

        ArgParam("<params>","..."),

        ArgLogging(),
        ArgConfig(),
      ).withExit(1)
    )).withLogging()
    
    val config = Config(
      ethRpcUrl = c.getString("eth.rpc.url").getOrElse(d.ethRpcUrl),
      from = c.getString("from").getOrElse(d.from),
      block = c.getString("block").map(b => b.toLong),

      tracer = c.getString("tracer").getOrElse(d.tracer),
      tracerConfig = c.getMap("tracerConfig",d.tracerConfig),

      delay = c.getLong("delay").getOrElse(d.delay),

      cmd = c.getCmd().getOrElse(d.cmd),
      params = c.getParams()
    )

    Console.err.println(s"Config: ${config}")

    // implicit val web3:Web3j = Web3j.build(new HttpService(config.ethRpcUrl))
    implicit val web3:Web3jTrace = Web3jTrace.build(new HttpService(config.ethRpcUrl))

    Console.err.println(s"web3j: ${web3}")

    val r = config.cmd match {
      case "call-trace" => 
        if(config.params.size < 3) {
          Console.err.println("trace-call: <from> <to> function(params,...)(return) [params...]")
          sys.exit(1)
        }

        val to = config.params(0)
        val funcName = config.params(1)
        val params = config.params.drop(2)
        //val paramsStr = params.mkString(" ")

        val tracer = config.tracer
        val tracerConfig = config.tracerConfig

        for {
          (data,_) <- Try { Eth.encodeFunction(funcName,params) }
          r <- {
            Try{ 
              web3.traceCall(config.from,to,data,tracer,tracerConfig.map{ case (k,v) => (k,v.toBoolean) }.asInstanceOf[Map[String,Object]].asJava)
            }
          }
          r <- Try{ r.send() }
          r <- Try{ 
            if(r.hasError())
              throw new Exception(s"Error: ${r.getError().getCode()}: ${r.getError().getMessage()}: ${r.getError().getData()}")
            else
              r.getResult() 
          }
        } yield r.toString()

      case "call" |  "call-async" => 
        if(config.params.size < 2) {
          Console.err.println("call: <contract> <function> [params..]")
          sys.exit(1)
        }

        val contractAddress = config.params(0)
        val funcName = config.params(1)
        val params = config.params.drop(2)
        val paramsStr = params.mkString(" ")
          
        //Eth.callFunctionWithParams(config.from,contractAddress,funcName,paramsStr)
        if(config.cmd == "call-async") {
          Eth.callFunctionAsync(config.from,contractAddress,funcName,params,block=config.block)
        } else {
          Eth.callFunction(config.from,contractAddress,funcName,params,block=config.block)
        }
              
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

      case "abi-encode" => 
        if(config.params.size < 1) {
          Console.err.println("abi-encode: type [params..]")
          sys.exit(1)
        }

        val typ = config.params(0)
        val params = config.params.drop(1)
        
        Solidity.encodeData(typ,params)

      case "abi-decode" => 
        if(config.params.size < 1) {
          Console.err.println("abi-decode: type [params..]")
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
    
    Console.println(s"${r}")

    if(r.isInstanceOf[Future[_]]) {
      var rf = r.asInstanceOf[Future[_]].await()
      Console.println(s"${rf}")
    }
    
  }
}
