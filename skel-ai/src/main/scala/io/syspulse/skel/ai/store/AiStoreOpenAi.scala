package io.syspulse.skel.ai.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.concurrent.Future
import scala.collection.immutable
import com.typesafe.scalalogging.Logger

import os._
import io.jvm.uuid._

import spray.json._
import DefaultJsonProtocol._

import io.syspulse.skel.service.JsonCommon
import io.syspulse.skel.util.Util
import io.syspulse.skel.store.StoreDir
import io.syspulse.skel.store.Store

import io.syspulse.skel.ai.Ai
import io.syspulse.skel.ai.server.AiJson._
import io.syspulse.skel.ai.Config
import io.syspulse.skel.ai.core.Providers
import io.syspulse.skel.ai.provider.openai.OpenAi
import io.syspulse.skel.ai.provider.AiProvider
import io.syspulse.skel.ai.core.OpenAiURI

// Preload from file during start
class AiStoreOpenAi(uri:String) extends AiStore {

  // TODO: Change !
  val engine = new OpenAi(OpenAiURI(Util.replaceEnvVar(uri)))

  val store = new AiStoreMem()

  def toKey(question:String):String = Util.sha256(question)

  def all(oid:Option[String]):Future[Seq[Ai]] = store.all(oid)

  def size:Future[Long] = store.size

  override def +++(w:Ai):Future[Ai] =
    store.+(w).map(_ => w)(scala.concurrent.ExecutionContext.global)

  override def +(w:Ai):Future[Ai] = store.+(w).map(_ => w)(scala.concurrent.ExecutionContext.global)

  override def del(question:String,oid:Option[String]):Future[Ai] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    store.del(question,oid)
  }

  override def del(question:String):Future[String] = del(question,None).map(_ => question)(scala.concurrent.ExecutionContext.global)

  def ???(question:String,oid:Option[String]):Future[Ai] = store.???(question,oid)

  def ????(question:String,model:Option[String],oid:Option[String]):Future[Ai] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    ???(question,oid).recoverWith { case _ => Store.toFuture(engine.ask(question,model)) }
  }

  override def findByOid(oid:String):Seq[Ai] = store.findByOid(oid)

  def getProviderId():String = "openai"

  def getProvider(oid:Option[String]):Option[AiProvider] = Some(engine)

  // add test questioness
  //`+`(Ai("0x0000000000000000000000000000000000000007",Seq("Ai","test"),0L,oid=Some(Sources.GLOBAL_LEDGER))))
  //`+`(Ai("0x0000000000000000000000000000000000001012",Seq("Ai","test"),0L,oid=Some(Sources.GLOBAL_LEDGER))))

}
