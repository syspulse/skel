package io.syspulse.skel.crypto.eth.abi

import com.typesafe.scalalogging.Logger

// ATTENTION
import scala.util.Try
import scala.util.Success

import codegen.Decoder
import codegen.AbiDefinition
import os._
import scala.util.Failure

import io.syspulse.skel.crypto.eth.abi.{AbiResult, AbiStore}
class AbiStoreDir(dir:String) extends AbiStore {

  var funcSignatures:Map[String,String] = Map(
    "0xa9059cbb" -> "transfer",
    "0x23b872dd" -> "transferFrom",
  )
  var eventSignatures:Map[String,String] = Map(
    "0xddf252ad" -> "Transfer",
    "0x8c5be1e5" -> "Approval",
  )

  def resolveFuncSignature(sig:String) = funcSignatures.get(sig.toLowerCase())
  def resolveEventSignature(sig:String) = eventSignatures.get(sig.toLowerCase())

  var store:Map[String,ContractAbi] = Map()

  def size = store.size

  def find(addr:String,functionName:String) = resolve(addr,Some("function"),Some(functionName))

  def resolve(contractAddr:String,entity:Option[String]=None,entityName:Option[String] = None):Try[Seq[AbiDefinition]] = {
    
    val contract = store.get(contractAddr.toLowerCase())
    if(! contract.isDefined) {
      return Failure(new Exception(s"not found: ${contractAddr}"))
    }

    val abi = (entity,entityName) match {
      case (Some("event"),Some(name)) =>
        contract.get.getAbi().filter(d => d.isEvent).filter(_.name == Option(name))
      case (Some("event"),None) =>
        contract.get.getAbi().filter(d => d.isEvent)
      case (Some("function"),Some(name)) =>
        contract.get.getAbi().filter(d => d.isFunction).filter(_.name == Option(name))
      case (Some("function"),None) =>
        contract.get.getAbi().filter(d => d.isFunction)      
      case _ =>
        contract.get.getAbi()
    }

    Success(abi)
  }

  def decodeInput(contract:String,data:Seq[String],entity:String):Try[AbiResult] = {
    val abi = resolve(contract,Some(entity),None)

    if(abi.isFailure) {
      return Failure(new Exception(s"could not resolve contract: '${contract}'"))
    }

    val (r,payload) = entity match {
      case "event" => 
        val sig = data.head.take(ABI.EVENT_HASH_SIZE).toLowerCase()
        val payload = data.tail.map(_.drop(2)).mkString("")
        val selector = resolveEventSignature(sig)
        
        if(!selector.isDefined) {
          return Failure(new Exception(s"could not find selector: ${entity}: '${data}'"))
        }
        
        val r = Decoder.decodeEvent(abi.get,selector.get,payload).map(r => AbiResult(selector.get,r))
        
        (r,payload)
      case "function" | _ =>
        val sig = data.head.take(ABI.FUNC_HASH_SIZE).toLowerCase()
        val payload = data.head.drop(ABI.FUNC_HASH_SIZE)
        val selector = resolveFuncSignature(sig)

        if(!selector.isDefined) {
          return Failure(new Exception(s"could not find selector: ${entity}: '${data}'"))
        }

        val r = Decoder.decodeFunction(abi.get,selector.get,payload).map(r => AbiResult(selector.get,r))
        
        (r,payload)
    }
    
    r
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
          
        if(abi.isSuccess && abi.get.size != 0) {
          log.info(s"${f}: ${abi.get.size}")
          Some(addr.toLowerCase -> abi.get)
          
        } else {
          log.warn(s"failed to load: ${f}: ${abi}")
          None
        }
      }) 
      
    store = store ++ abis.map{ case(addr,abi) => addr.toLowerCase -> new ContractAbi(addr,abi)}

    log.info(s"Loaded ABI: ${store.size}")
    store
  }

}

