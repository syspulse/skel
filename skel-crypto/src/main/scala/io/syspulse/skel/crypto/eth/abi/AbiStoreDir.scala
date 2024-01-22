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

  def toKey(id:String):String = id

  def functions:SignatureStore[FuncSignature] = funcStore
  def events:SignatureStore[EventSignature] = eventStore 
  
  def size = store.size
  def all:Seq[AbiContract] = store.values.map( ca => AbiContract(ca.getAddr(),ca.getJson())).toSeq

  def all(from:Option[Int],size:Option[Int]):(Seq[AbiContract],Long) = {
    val aa = all
    (aa.drop(from.getOrElse(0)).take(size.getOrElse(10)),aa.size)
  }
  
  override def +(a:AbiContract):Try[AbiContract] = {
    ContractAbi(a.addr,a.json).map( ca => {
      store = store + (a.addr.toLowerCase -> ca)

      if(! loading)
        writeFile(a)

      a
    })
  }

  def add(a:AbiContract) = {
    loading = true
    this.+(a)
    loading = false
  }
  
  override def del(id:String):Try[String] = {
    log.info(s"del: ${id}")
    store = store - id.toLowerCase
    delFileById(id.toLowerCase)
  }

  def ?(id:String):Try[AbiContract] = {
    store.get(id.toLowerCase) match {
      case Some(ca) => Success(AbiContract(ca.getAddr(),ca.getJson()))
      case None => Failure(new Exception(s"not found: ${id}"))
    }
  }

  def search(txt:String,from:Option[Int],size:Option[Int]):(Seq[AbiContract],Long) = {
    if(txt.trim.size < 3) 
      return (Seq(),0L)

    val term = txt.toLowerCase + ".*"

    val vv = store.values.filter(v => {
        v.getAddr().toLowerCase.matches(term) || 
        v.getJson().toLowerCase.matches(term)
    })
    .map( ca => AbiContract(ca.getAddr(),ca.getJson()))
    
    (vv.drop(from.getOrElse(0)).take(size.getOrElse(10)).toList,vv.size)
  }


  def find(addr:String,functionName:String) = resolve(addr,Some(AbiStore.FUNCTION),Some(functionName))

  def resolve(contractAddr:String,entity:Option[String]=None,entityName:Option[String] = None):Try[Seq[AbiDefinition]] = {
    
    val contract = store.get(contractAddr.toLowerCase)
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

  override def load():Unit = {
    load(dir)
    watch(dir)
  }

  // NOTE: replce with standard StoreDir watcher !
  override def watch(dir:String) = {
    import better.files._
    import io.methvin.better.files._
    import io.methvin.watcher.hashing.FileHasher
    import java.nio.file.{Path, StandardWatchEventKinds => EventType, WatchEvent}
    import scala.concurrent.ExecutionContext.Implicits.global

    @volatile
    var modifying = false
    
    // try to prevent modifications by touching file
    val watcher = new RecursiveFileMonitor(
      File(dir),
      fileHasher = Some(FileHasher.LAST_MODIFIED_TIME)) {
      override def onCreate(file: File, count: Int) = {
        if(! modifying) {
          modifying = true
          val id = file.nameWithoutExtension
          log.info(s"${file}: added")
          val aa = addAsFile(os.Path(file.toString,os.pwd))
          aa.foreach( a => add(a))
          modifying = false
        }
      }
      override def onModify(file: File, count: Int) = {
        if(! modifying) {
          modifying = true
          val id = file.nameWithoutExtension
          log.info(s"${file}: modified")
          //del(id)
          val aa = addAsFile(os.Path(file.toString,os.pwd))
          aa.foreach( a => add(a))
          modifying = false
        }
      }
      override def onDelete(file: File, count: Int) = {
        if(! modifying) {
          modifying = true
          val id = file.nameWithoutExtension
          log.info(s"${file}: deleted")
          
          del(id)
          modifying = false
        }
      }
    }

    watcher.start()
    log.info(s"watching: ${dir}")
  }

  def addAsFile(f:Path) = {
    log.info(s"Loading file: ${f}")

    val (label:String,addr:String) = f.last.split("[-.]").toList match {
      case label :: addr :: _ => (label,addr.toLowerCase())
      case addr :: Nil => ("",addr.toLowerCase())
      case _ => ("","")
    }
    
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
  }

  override def load(dir:String,hint:String = "") = {
    log.info(s"scanning ABI: ${dir}")
    
    loading = true
    val aa = os.walk(os.Path(dir,os.pwd))
      .filter(_.toIO.isFile())
      .flatMap( f => {
        addAsFile(f)
      })

    aa.foreach( a => this.+(a))
    loading = false

    //store = store ++ abis.map{ case(addr,abi) => addr.toLowerCase -> new ContractAbi(addr,abi)}

    log.info(s"Loaded ABI: ${store.size}")    
  }

  def resolveFunc(sig:String) = funcStore.first(sig.toLowerCase()).map(_.tex).toOption
  def resolveEvent(sig:String) = eventStore.first(sig.toLowerCase()).map(_.tex).toOption
}

