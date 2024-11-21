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

object AppEvm extends {

  case class Config(
    ethRpcUrl:String="http://geth:8545",
    from:String = "",

    cmd:String = "call",
    params:Seq[String] = Seq()
  )
    
  def main(args:Array[String]) = {
    Console.err.println(s"args: ${args.size}: ${args.toSeq}")
    
    val d = Config()
    val c = Configuration.withPriority(Seq(
      new ConfigurationAkka,
      new ConfigurationProp,
      new ConfigurationEnv, 
      new ConfigurationArgs(args,"eth-evm","",
        
        ArgString('u', "eth.rpc.url",s"RPC uri (def: ${d.ethRpcUrl})"),
        ArgString('f', "from",s"From address (def: ${d.from})"),
        
        
        ArgCmd("call","eth_call: [address] function(params,...)(return)"),
        ArgCmd("encode","function(params,...)(return)"),
        ArgCmd("estimate","eth_estimageGas"),
        
        ArgParam("<params>","..."),

        ArgLogging(),
        ArgConfig(),
      ).withExit(1)
    )).withLogging()
    
    val config = Config(
      ethRpcUrl = c.getString("uri").getOrElse(d.ethRpcUrl),
      from = c.getString("from").getOrElse(d.from),
      
      cmd = c.getCmd().getOrElse(d.cmd),
      params = c.getParams()
    )

    Console.err.println(s"Config: ${config}")

    implicit val web3:Web3j = Web3j.build(new HttpService(config.ethRpcUrl))

    Console.err.println(s"web3j: ${web3}")

    val r =config.cmd match {
      case "call" => 
        val contractAddress = config.params(0)
        val funcName = config.params(1)
        val params = config.params.drop(2)
        
        Eth.call("0x"+"0".*(40),contractAddress,funcName,params)
              
      case "encode" => 
        val funcName = config.params(0)
        val params = config.params.drop(1)
        
        Eth.encodeFunction(funcName,params)

      case "estimate" => 
        val contractAddress = config.params(0)
        val funcName = config.params(1)
        val params = config.params.drop(2)
        
        Console.err.println(s"params=${params}")
        
        Eth.estimate(config.from,contractAddress,funcName,params)
    }
    
    Console.err.println(s"r = ${r}")
  }
}

