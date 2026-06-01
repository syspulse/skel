package io.syspulse.skel.crypto.eth.abi

import com.typesafe.scalalogging.Logger

import scala.util.Try
import scala.util.Success
import scala.concurrent.Future

import codegen.Decoder
import codegen.AbiDefinition
import os._
import scala.util.Failure
import io.syspulse.skel.store.StoreDir

import io.methvin.better.files.RecursiveFileMonitor

import spray.json._
import DefaultJsonProtocol._
import AbiContractJson._

class AbiStoreDir(dir:String,funcStore:SignatureStore[FuncSignature],eventStore:SignatureStore[EventSignature]) extends StoreDir[AbiContract,String](dir) with AbiStore {

  var store:Map[String,ContractAbi] = Map()

  def toKey(id:String):String = id

  def functions:SignatureStore[FuncSignature] = funcStore
  def events:SignatureStore[EventSignature] = eventStore

  private def allSync:Seq[AbiContract] = store.values.map(ca => AbiContract(ca.getAddr(),ca.getJson())).toSeq

  def size:Future[Long] = Future.successful(store.size.toLong)
  def all:Future[Seq[AbiContract]] = Future.successful(allSync)

  def all(from:Option[Int],size:Option[Int]):(Seq[AbiContract],Long) = {
    val aa = allSync
    (aa.drop(from.getOrElse(0)).take(size.getOrElse(10)),aa.size)
  }

  override def +(a:AbiContract):Future[AbiContract] = Future.fromTry {
    ContractAbi(a.addr,a.json).map( ca => {
      val addrKey = a.addr.toLowerCase
      // Prefer ABIs with more functions when there are duplicates
      val existing = store.get(addrKey)
      val shouldReplace = existing match {
        case Some(existingCa) =>
          val existingFuncCount = existingCa.getAbi().count(_.isFunction)
          val newFuncCount = ca.getAbi().count(_.isFunction)
          // Replace if new ABI has more functions, or if existing has no functions and new has some
          newFuncCount > existingFuncCount || (existingFuncCount == 0 && newFuncCount > 0)
        case None => true
      }

      if(shouldReplace) {
        store = store + (addrKey -> ca)
        if(! loading)
          writeFile(a)
      }

      a
    })
  }

  def add(a:AbiContract) = {
    loading = true
    this.+(a)
    loading = false
  }
  
  override def del(id:String):Future[String] = {
    log.info(s"del: ${id}")
    store = store - id.toLowerCase
    Future.fromTry(delFileById(id.toLowerCase))
  }

  def ?(id:String):Future[AbiContract] = {
    store.get(id.toLowerCase) match {
      case Some(ca) => Future.successful(AbiContract(ca.getAddr(),ca.getJson()))
      case None => Future.failed(new Exception(s"not found: ${id}"))
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
    // Get full ABI (not filtered) as Decoder needs complete ABI to find functions/events
    val abi = resolve(contract,None,None)

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

        // Find the function in the ABI and build its full signature
        val funcSignature = if(abi.isSuccess) {
          abi.get.find(d => d.isFunction && d.name == Option(selector.get))
            .map(AbiSignature.toSig)
            .getOrElse(selector.get)
        } else {
          selector.get
        }

        val r = if(abi.isSuccess)
          Decoder.decodeFunction(abi.get,funcSignature,payload).map(r => AbiResult(selector.get,r))
        else
          Success(AbiResult(selector.get,Seq()))
        
        (r,payload)
    }
    
    r
  }

  override def load():String = {
    val d = load(dir)
    watch(dir)
    d
  }

  // NOTE: replce with standard StoreDir watcher !
  override def watch(dir:String):RecursiveFileMonitor = {
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
    watcher
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

  override def load(dir:String,hint:String = ""):String = {
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
    dir
  }

  def resolveFunc(sig:String) = funcStore.first(sig.toLowerCase()).map(_.tex).toOption
  def resolveEvent(sig:String) = eventStore.first(sig.toLowerCase()).map(_.tex).toOption
}

