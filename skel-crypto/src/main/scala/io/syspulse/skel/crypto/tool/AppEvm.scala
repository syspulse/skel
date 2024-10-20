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
    uri:String="http://geth:8545",
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
        
        ArgString('u', "uri",s"RPC uri (def: ${d.uri})"),
        ArgString('f', "from",s"From address (def: ${d.from})"),
        
        
        ArgCmd("call","eth_call"),
        ArgCmd("estimate","eth_estimageGas"),
        
        ArgParam("<params>","..."),

        ArgLogging(),
        ArgConfig(),
      ).withExit(1)
    )).withLogging()
    
    val config = Config(
      uri = c.getString("uri").getOrElse(d.uri),
      from = c.getString("from").getOrElse(d.from),
      
      cmd = c.getCmd().getOrElse(d.cmd),
      params = c.getParams()
    )

    Console.err.println(s"Config: ${config}")

    implicit val web3:Web3j = Web3j.build(new HttpService(config.uri))

    val r =config.cmd match {
      case "call" => 
        val contractAddress = config.params(0)
        val funcName = config.params(1)
        val inputs = config.params.lift(2).getOrElse("")
        
        Eth.call("0x0",contractAddress,funcName,inputs)
              
      case "estimate" => 
        val contractAddress = config.params(0)
        val funcName = config.params(1)
        val inputs = config.params.lift(2).getOrElse("")
        Console.err.println(s"intputs=${inputs}")
        Eth.estimate(config.from,contractAddress,funcName,inputs)
    }
    
    Console.err.println(s"r = ${r}")
  }
}

