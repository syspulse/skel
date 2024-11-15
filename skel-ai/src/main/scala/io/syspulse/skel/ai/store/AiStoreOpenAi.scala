package io.syspulse.skel.ai.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable
import com.typesafe.scalalogging.Logger

import os._
import io.jvm.uuid._

import spray.json._
import DefaultJsonProtocol._
import io.syspulse.skel.service.JsonCommon

import io.syspulse.skel.store.StoreDir

import io.syspulse.skel.ai.Ai
import io.syspulse.skel.ai.server.AiJson._
import io.syspulse.skel.ai.Config
import io.syspulse.skel.ai.source.Sources

import io.syspulse.skel.util.Util
import io.syspulse.skel.ai.source.openai.OpenAi

// Preload from file during start
class AiStoreOpenAi(uri:String) extends AiStore with JsonCommon {

  // TODO: Change !
  val engine = new OpenAi(Util.replaceEnvVar(uri))

  val store = new AiStoreMem()
      
  def toKey(question:String):String = Util.sha256(question)

  def all(oid:Option[String]):Seq[Ai] = store.all(oid)
  
  def size:Long = store.size
  
  override def +++(w:Ai):Try[Ai] = for {
    _ <- store.+(w)
  } yield w

  override def +(w:Ai):Try[Ai] = store.+(w).map(_ => w)

  override def del(question:String,oid:Option[String]):Try[Ai] = for {
    w <- store.del(question,oid)
    _ <- super.del(question)
  } yield w

  override def del(question:String):Try[String] = this.del(question).map(_ => question)
  
  def ???(question:String,oid:Option[String]):Try[Ai] = store.???(question,oid)
    

  def ????(question:String,model:Option[String],oid:Option[String]):Try[Ai] = {
    val o = ???(question,oid) match {
      case s @ Success(o) => 
        s
      case Failure(e) => 
        engine.chat(question,model)
    }
    o    
  }

  override def findByOid(oid:String):Seq[Ai] = store.findByOid(oid)

  def getProviderId():String = "openai"
    
  // add test questioness
  //`+`(Ai("0x0000000000000000000000000000000000000007",Seq("Ai","test"),0L,oid=Some(Sources.GLOBAL_LEDGER))))
  //`+`(Ai("0x0000000000000000000000000000000000001012",Seq("Ai","test"),0L,oid=Some(Sources.GLOBAL_LEDGER))))
}