package io.syspulse.skel.crypto.eth.abi

import com.typesafe.scalalogging.Logger

// ATTENTION
import scala.util.Try
import scala.util.Success

import codegen.Decoder
import codegen.AbiDefinition
import os._
import scala.util.Failure

class AbiStoreDir(dir:String) extends AbiStore {

  var functionSignatures:Map[String,String] = Map(
    "0xa9059cbb" -> "transfer",
    "0x23b872dd" -> "transferFrom",
  )

  var store:Map[String,ContractAbi] = Map()

  def size = store.size

  def find(contractAddr:String,functionName:String = "transfer",name:String=""):Try[ContractAbi] = {
    
    val contract = store.get(contractAddr)

    // find function names
    val function = contract.flatMap(c => c.getAbi().filter(d => d.isFunction).find(_.name.get == functionName))
    
    if(function.isDefined) {

      val parToName = function.get.inputs.get(0).name
      val parValueName = function.get.inputs.get(1).name
      Success(ContractERC20( contractAddr, contract.get.getAbi(), parToName, parValueName, name ))

    } else {
      Failure(new Exception(s"could not find function: ${contractAddr}: '${functionName}'"))      
    }
  }

  override def load():Try[AbiStore] = {
    load(dir)
    Success(this)
  }

  def load(dir:String):Map[String,ContractAbi] = {
    log.info(s"scanning ABI: ${dir}")
    
    val abis = os.walk(os.Path(dir,os.pwd))
      .filter(_.toIO.isFile())
      .flatMap( f => {

        log.info(s"Loading file: ${f}")

        val (label:String,addr:String) = f.last.split("[-.]").toList match {
          case label :: addr :: _ => (label,addr.toLowerCase())
          case addr :: Nil => ("",addr)
          case _ => ("","")
        }
        
        val abi = Decoder.loadAbi(scala.io.Source.fromFile(f.toString).getLines().mkString("\n"))
          
        if(abi.size != 0) {
          log.info(s"${f}: ${abi.size}")
          Some(addr.toLowerCase -> abi)
          
        } else {
          log.warn(s"failed to load: ${f}: ${abi}")
          None
        }
      }) 
      
    store = store ++ abis.map{ case(addr,abi) => addr -> new ContractAbi(addr,abi)}

    log.info(s"Loaded ABI: ${store.size}")
    store
  }

  def decodeInput(contract:String,input:String,selector:String):Try[Seq[(String,String,Any)]] = {
    val abi = store.get(contract.toLowerCase())

    val funcHash = input.take(ABI.FUNC_HASH_SIZE).toLowerCase()
    val func = if(selector.isEmpty) functionSignatures.get(funcHash).getOrElse("") else selector

    val intputData = input.drop(ABI.FUNC_HASH_SIZE)

    if(abi.isDefined && (!func.isEmpty)) {
      Decoder.decodeInput(abi.get.getAbi(),func,intputData)
    } else 
      Failure(new Exception(s"could not find ABI for contract: '${contract}': '${func}'"))
  }
}

