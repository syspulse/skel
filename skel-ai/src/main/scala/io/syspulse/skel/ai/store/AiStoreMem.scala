package io.syspulse.skel.ai.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.concurrent.Future
import scala.collection.immutable

import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.util.Util

import io.syspulse.skel.ai.{Ai}
import io.syspulse.skel.ai.provider.AiProvider

class AiStoreMem extends AiStore {
  val log = Logger(s"${this}")

  var ais: Map[String,Ai] = Map()

  def all(oid:Option[String]):Future[Seq[Ai]] =
    Future.successful(
      if(oid==None)
        ais.values.toSeq
      else
        ais.values.filter(_.oid == oid).toSeq
    )

  def size:Future[Long] = Future.successful(ais.size)

  def findByOid(oid:String):Seq[Ai] =
    ais.values.filter(_.oid == Some(oid)).toSeq

  def +++(w:Ai):Future[Ai] = {
    ais = ais + (Util.sha256(w.question.toLowerCase) -> w)
    Future.successful(w)
  }

  def +(w:Ai):Future[Ai] = +++(w)

  def del(question0:String,oid:Option[String]):Future[Ai] = {
    val question = Util.sha256(question0.toLowerCase)
    ais.get(question) match {
      case Some(w) if w.oid == oid =>
        ais = ais - question
        Future.successful(w)
      case Some(_) | None =>
        Future.failed(new Exception(s"not found: ${question}"))
    }
  }

  def ???(question:String,oid:Option[String]):Future[Ai] = ais.get(Util.sha256(question.toLowerCase)) match {
    case Some(w) if(!oid.isDefined) => Future.successful(w)
    case Some(w) if(w.oid == oid) => Future.successful(w)
    case _ => Future.failed(new Exception(s"not found: '${question}'"))
  }

  def ????(question:String,model:Option[String],oid:Option[String]):Future[Ai] = ???(question,oid)

  def getProviderId():String = "cache"

  def getProvider(oid:Option[String]):Option[AiProvider] = None
}
