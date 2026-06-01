package io.syspulse.skel.ai.store

import scala.util.Try
import scala.concurrent.Future
import scala.collection.immutable
import io.jvm.uuid._

import io.syspulse.skel.store.Store
import io.syspulse.skel.ai.{Ai}
import io.syspulse.skel.ai.provider.AiProvider

trait AiStore extends Store[Ai,String] {

  def getKey(w: Ai): String = w.question

  def +++(w:Ai):Future[Ai]

  def del(question:String,oid:Option[String]):Future[Ai]

  def ???(question:String,oid:Option[String]):Future[Ai]

  // advanced info
  def ????(question:String,model:Option[String],oid:Option[String]):Future[Ai]

  def all(oid:Option[String]):Future[Seq[Ai]]

  def size:Future[Long]

  def findByOid(oid:String):Seq[Ai]

  def ?(question:String):Future[Ai] = ???(question,None)

  def all:Future[Seq[Ai]] = all(None)
  def del(question:String):Future[String] = del(question,None).map(_ => question)(scala.concurrent.ExecutionContext.global)

  def getProviderId():String

  def getProvider(oid:Option[String]):Option[AiProvider]
}
