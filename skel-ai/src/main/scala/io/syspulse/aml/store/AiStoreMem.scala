package io.syspulse.ai.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.ai.{Ai}
import io.syspulse.skel.util.Util

class AiStoreMem extends AiStore {
  val log = Logger(s"${this}")
  
  var ais: Map[String,Ai] = Map()

  def all(oid:Option[String]):Seq[Ai] = 
    if(oid==None) 
      ais.values.toSeq 
    else 
      ais.values.filter(_.oid == oid).toSeq

  def size:Long = ais.size

  def findByOid(oid:String):Seq[Ai] = 
    ais.values.filter(_.oid == Some(oid)).toSeq

  def +++(w:Ai):Try[Ai] = {     
    ais = ais + (Util.sha256(w.question.toLowerCase) -> w)
    Success(w)
  }

  def +(w:Ai):Try[Ai] = +++(w)

  def del(question0:String,oid:Option[String]):Try[Ai] = {         
    val question = Util.sha256(question0.toLowerCase)
    ais.get(question) match {
      case Some(w) if w.oid == oid =>
        ais = ais - question
        Success(w)
      case Some(_) | None => 
        Failure(new Exception(s"not found: ${question}"))
    }
  }

  def ???(question:String,oid:Option[String]):Try[Ai] = ais.get(Util.sha256(question.toLowerCase)) match {
    case Some(w) if(!oid.isDefined) => Success(w)
    case Some(w) if(w.oid == oid) => Success(w)
    case _ => Failure(new Exception(s"not found: ${question}"))
  }

  def ????(question:String,model:Option[String],oid:Option[String]):Try[Ai] =  ???(question,oid)
 
  def getProviderId():String = "cache"
}
