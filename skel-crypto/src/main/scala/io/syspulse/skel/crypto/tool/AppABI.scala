package io.syspulse.skel.crypto.tool

import io.syspulse.skel.util.Util
import io.syspulse.skel.config._

import com.typesafe.scalalogging.Logger
import scopt.OParser

import scala.util.Success
import scala.util.Try

import io.syspulse.skel.crypto.eth.abi._

object AppABI extends {  

  case class Config(
    abiStore: String = "dir://store/abi",
    eventStore:String = "dir://store/event",
    funStore:String = "dir://store/fun",

    cmd:String = "decode",
    params:Seq[String] = Seq()
  )
  
  def main(args:Array[String]) = {
    Console.err.println(s"args: ${args.size}: ${args.toSeq}")
    
    val d = Config()
    val c = Configuration.withPriority(Seq(
      new ConfigurationAkka,
      new ConfigurationProp,
      new ConfigurationEnv, 
      new ConfigurationArgs(args,"tool-abi","",
        ArgString('_', "store.abi",s"ABI definitions store (def: ${d.abiStore})"),
        ArgString('_', "store.event",s"Event Signatures store (def: ${d.eventStore})"),
        ArgString('_', "store.fun",s"Function signatures store (def: ${d.funStore})"),
        
        ArgCmd("read","read command"),
        ArgCmd("search","Search signature"),
        ArgCmd("decode","Decode input (func or event)"),
        
        ArgParam("<params>","...")
      )
    ))
    
    val config = Config(
      abiStore = c.getString("store.abi").getOrElse(d.abiStore),
      eventStore = c.getString("store.event").getOrElse(d.eventStore),
      funStore = c.getString("store.fun").getOrElse(d.funStore),
      
      cmd = c.getCmd().getOrElse(d.cmd),
      params = c.getParams()
    )

    Console.err.println(s"config=${config}")

    val abiStore = config.abiStore.split("://").toList match {
      case "dir" :: dir :: _ => new AbiStoreDir(dir)
      case dir :: Nil => new AbiStoreDir(dir)
      case _ => new AbiStoreDir(config.abiStore)
    }

    abiStore.load()

    val r = config.cmd match {
      case "decode" => 
        val (contract,input,selector) = 
        config.params.toList match {
          case contract :: input :: selector :: Nil => (contract,input,selector)
          case contract :: input :: Nil => (contract,input,"")
          case contract :: Nil => (contract,"","")
          case _ => (
            "0x1f9840a85d5af5bf1d1762f925bdaddc4201f984",
            "0xa9059cbb000000000000000000000000f6bdeb12aba8bf4cfbfc2a60c805209002223e22000000000000000000000000000000000000000000000005a5a62f156c710000",
            "transfer")            
        }

        abiStore.decodeInput(contract,input,selector)
      case "read" =>         
      
      case _ => {
        Console.err.println(s"Unknown command: ${config.cmd}")
        sys.exit(1)
      }
    }

    println(s"${r}")
  }
}

