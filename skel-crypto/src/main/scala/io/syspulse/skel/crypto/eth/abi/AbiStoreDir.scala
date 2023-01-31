package io.syspulse.skel.crypto.eth.abi

import com.typesafe.scalalogging.Logger

import scala.util.Try
import scala.util.Success

import codegen.Decoder
import codegen.AbiDefinition
import os._
import scala.util.Failure
import io.syspulse.skel.store.StoreDir

import spray.json._
import DefaultJsonProtocol._
import AbiContractJson._

class AbiStoreDir(dir:String,funcStore:SignatureStore[FuncSignature],eventStore:SignatureStore[EventSignature]) extends StoreDir[AbiContract,String](dir) with AbiStore {

  var store:Map[String,ContractAbi] = Map()
  
  def size = store.size
  def all:Seq[AbiContract] = store.values.map( ca => AbiContract(ca.getAddr(),ca.getJson())).toSeq
  
  override def +(a:AbiContract):Try[AbiStoreDir] = {
    ContractAbi(a.addr,a.json).map( ca => {
      store = store + (a.addr -> ca)

      if(! loading)
        writeFile(a)

      this
    })
  }
  
  override def del(id:String):Try[AbiStoreDir] = {
    store = store - id
    delFileById(id).map(_ => this)
  }

  def ?(id:String):Try[AbiContract] = {
    store.get(id) match {
      case Some(ca) => Success(AbiContract(ca.getAddr(),ca.getJson()))
      case None => Failure(new Exception(s"not found: ${id}"))
    }
  }


  def find(addr:String,functionName:String) = resolve(addr,Some(AbiStore.FUNCTION),Some(functionName))

  def resolve(contractAddr:String,entity:Option[String]=None,entityName:Option[String] = None):Try[Seq[AbiDefinition]] = {
    
    val contract = store.get(contractAddr.toLowerCase())
    if(! contract.isDefined) {
      return Failure(new Exception(s"not found: ${contractAddr}"))
    }

    val abi = (entity,entityName) match {
      case (Some(AbiStore.EVENT),Some(name)) =>
        contract.get.getAbi().filter(d => d.isEvent).filter(_.name == Option(name))
      case (Some(AbiStore.EVENT),None) =>
        contract.get.getAbi().filter(d => d.isEvent)
      case (Some(AbiStore.FUNCTION),Some(name)) =>
        contract.get.getAbi().filter(d => d.isFunction).filter(_.name == Option(name))
      case (Some(AbiStore.FUNCTION),None) =>
        contract.get.getAbi().filter(d => d.isFunction)      
      case _ =>
        contract.get.getAbi()
    }

    Success(abi)
  }

  def decodeInput(contract:String,data:Seq[String],entity:String):Try[AbiResult] = {
    val abi = resolve(contract,Some(entity),None)

    if(abi.isFailure) {      
      return Failure(new Exception(s"could not resolve Contract: '${contract}'"))
    }

    val (r,payload) = entity match {
      case AbiStore.EVENT => 
        val sig = data.head.take(ABI.EVENT_HASH_SIZE).toLowerCase()
        val payload = data.tail.map(_.drop(2)).mkString("")
        val selector = resolveEvent(sig)
        
        if(!selector.isDefined) {
          return Failure(new Exception(s"could not resolve Event sig: ${entity}: '${data}'"))
        }
        
        val r = if(abi.isSuccess)
          Decoder.decodeEvent(abi.get,selector.get,payload).map(r => AbiResult(selector.get,r))
        else
          Success(AbiResult(selector.get,Seq()))
        
        (r,payload)
      case AbiStore.FUNCTION | _ =>
        val sig = data.head.take(ABI.FUNC_HASH_SIZE).toLowerCase()
        val payload = data.head.drop(ABI.FUNC_HASH_SIZE)
        val selector = resolveFunc(sig)

        if(!selector.isDefined) {
          return Failure(new Exception(s"could not resolve Func sig: ${entity}: '${data}'"))
        }

        val r = if(abi.isSuccess)
          Decoder.decodeFunction(abi.get,selector.get,payload).map(r => AbiResult(selector.get,r))
        else
          Success(AbiResult(selector.get,Seq()))
        
        (r,payload)
    }
    
    r
  }

  override def load():Try[AbiStore] = {
    load(dir)
    Success(this)
  }

  override def load(dir:String) = {
    log.info(s"scanning ABI: ${dir}")
    
    loading = true
    val aa = os.walk(os.Path(dir,os.pwd))
      .filter(_.toIO.isFile())
      .flatMap( f => {

        log.info(s"Loading file: ${f}")

        val (label:String,addr:String) = f.last.split("[-.]").toList match {
          case label :: addr :: _ => (label,addr.toLowerCase())
          case addr :: Nil => ("",addr)
          case _ => ("","")
        }
        
        // val abi = Decoder.loadAbi(scala.io.Source.fromFile(f.toString).getLines().mkString("\n"))
          
        // if(abi.isSuccess && abi.get.size != 0) {
        //   log.info(s"${f}: ${abi.get.size}")
        //   Some(addr.toLowerCase -> abi.get)
          
        // } else {
        //   log.warn(s"failed to load: ${f}: ${abi}")
        //   None
        // }

        val fileData = os.read(f)
        
        // try to load as AbiContract format (multiple are possibe)
        val aa = 
          fileData.split("\n").map { data =>
            try {
              data.parseJson.convertTo[AbiContract]              
            } catch {
              case e:Exception => 
                // interpret it as one single Json
                AbiContract(addr,fileData)
            }
          } 
        aa               
      })

    aa.foreach( a => this.+(a))
    loading = false

    //store = store ++ abis.map{ case(addr,abi) => addr.toLowerCase -> new ContractAbi(addr,abi)}

    log.info(s"Loaded ABI: ${store.size}")    
  }

  def resolveFunc(sig:String) = funcStore.first(sig.toLowerCase()).map(_.tex).toOption
  def resolveEvent(sig:String) = eventStore.first(sig.toLowerCase()).map(_.tex).toOption
}

