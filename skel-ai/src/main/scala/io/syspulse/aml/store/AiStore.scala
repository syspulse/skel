package io.syspulse.ai.store

import scala.util.Try
import scala.concurrent.Future
import scala.collection.immutable
import io.jvm.uuid._

import io.syspulse.skel.store.Store
import io.syspulse.ai.{Ai}

trait AiStore extends Store[Ai,String] {  
  
  def getKey(w: Ai): String = w.question
  
  def +++(w:Ai):Try[Ai]
  
  def del(question:String,oid:Option[String]):Try[Ai]
  
  def ???(question:String,oid:Option[String]):Try[Ai]

  // advanced info
  def ????(question:String,model:Option[String],oid:Option[String]):Try[Ai]
  
  def all(oid:Option[String]):Seq[Ai]
  
  def size:Long

  def findByOid(oid:String):Seq[Ai]

  def ?(question:String):Try[Ai] = ???(question,None)
  
  def all:Seq[Ai] = all(None)
  def del(question:String):Try[String] = del(question,None).map(_ => question)  
}

