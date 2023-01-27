package io.syspulse.skel.crypto.tool

import io.syspulse.skel.util.Util
import io.syspulse.skel.config._

import com.typesafe.scalalogging.Logger
import scopt.OParser

import scala.util.Success
import scala.util.Try

import io.syspulse.skel.crypto.eth.abi._
import codegen.Decoder
import codegen.AbiDefinition

object AppABI extends {  

  case class Config(
    abiStore: String = "dir://store/abi",
    eventStore:String = "dir://store/event",
    funStore:String = "dir://store/func",

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
        
        ArgCmd("abi","Show ABI for contract: <contract> [entity] [name] (entity: event,function)"),
        ArgCmd("decode","Decode data: <contract> [entity] <data> (entity: event,function)"),
        ArgCmd("func","Func Signature store"),
        ArgCmd("event","Event Signature store"),
        
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
      case "dir" :: dir :: _ => new AbiStoreDir(dir) with AbiStoreStoreSignaturesMem
      case dir :: Nil => new AbiStoreDir(dir) with AbiStoreStoreSignaturesMem
      case _ => new AbiStoreDir(config.abiStore) with AbiStoreStoreSignaturesMem
    }

    val eventStore = config.eventStore.split("://").toList match {
      case "dir" :: dir :: _ => new EventSignatureStoreDir(dir)
      case dir :: Nil => new EventSignatureStoreDir(dir)
      case "mem" :: _ => new SignatureStoreMem[EventSignature]()
      case _ => new SignatureStoreMem[EventSignature]()
    }

    val funcStore = config.funStore.split("://").toList match {
      case "dir" :: dir :: _ => new FuncSignatureStoreDir(dir)
      case dir :: Nil => new FuncSignatureStoreDir(dir)
      case "mem" :: _ => new SignatureStoreMem[FuncSignature]()
      case _ => new SignatureStoreMem[FuncSignature]()
    }

    abiStore.load()

    val r = config.cmd match {
      case "decode" => 
        val (contract,data,entity) = 
        config.params.toList match {
          case contract :: data :: Nil => (contract,Seq(data),"function")
          case contract :: Nil => (contract,Seq(),"function")
          case contract :: entity :: data => (contract,data,entity)
          case _ => (
            "0x1f9840a85d5af5bf1d1762f925bdaddc4201f984",
            Seq("0xa9059cbb000000000000000000000000f6bdeb12aba8bf4cfbfc2a60c805209002223e22000000000000000000000000000000000000000000000005a5a62f156c710000"),
            "function")            
        }

        Console.err.println(s"Decoding: ${contract}: ${entity}: ${data}")
        
        abiStore.decodeInput(contract,data,entity)
      
      case "abi" => 
        val (contract,entity,entityName) = 
        config.params.toList match {
          case contract :: entity :: entityName :: Nil => (contract,Some(entity),Some(entityName))
          case contract :: entity :: Nil => (contract,Some(entity),None)
          case contract :: Nil => (contract,None,None)
          case _ => ("",None,None)
        }

        def abiToString(abi:Try[Seq[AbiDefinition]]) = abi.map(_.map(ad => 
            s"\nname = ${ad.name.getOrElse("")}\n  in  = ${ad.inputs.getOrElse(Seq()).mkString(",")}\n  out = ${ad.outputs.getOrElse(Seq()).mkString(",")}"
          ).mkString("\n")).toString + "\n"
        
        if(contract == "") {
          abiStore.all

        } else { 
          val abi = 
            if(contract.toLowerCase.endsWith(".json")) {
              Decoder.loadAbi(os.read(os.Path(contract,os.pwd)))
            } else 
              abiStore.resolve(contract,entity,entityName)

          abiToString(abi)
        }
          
              
      case "func" =>
        config.params.toList match {
          case sig :: Nil => funcStore.??(sig).map(_.mkString("\n"))
          case _ => funcStore.all.mkString("\n")
        }
        
      case "event" =>
        config.params.toList match {
          case sig :: Nil => eventStore.??(sig).map(_.mkString("\n"))
          case _ => eventStore.all.mkString("\n")
        }
        
      case _ => {
        Console.err.println(s"Unknown command: ${config.cmd}")
        sys.exit(1)
      }
    }

    println(s"${r}")
  }
}
