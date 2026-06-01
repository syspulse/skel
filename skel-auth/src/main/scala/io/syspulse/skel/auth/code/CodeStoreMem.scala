package io.syspulse.skel.auth.code

import scala.concurrent.Future
import scala.collection.immutable

import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

class CodeStoreMem extends CodeStore {
  val log = Logger(s"${this}")

  var codes: Map[String,Code] = Map()

  def all:Future[Seq[Code]] = Future.successful(codes.values.toSeq)

  def getByToken(accessToken:String):Future[Option[Code]] = {
    Future.successful(codes.values.find(_.accessToken == Some(accessToken)))
  }

  def size:Future[Long] = Future.successful(codes.size.toLong)

  def +(code:Code):Future[Code] = {
    codes = codes + (code.code -> code); Future.successful(code)
  }

  def !(code:Code):Future[Code] = {
    val old = codes.getOrElse(code.code,code)
    // update only with userId
    codes = codes + (code.code -> code.copy(xid = old.xid));
    Future.successful(code)
  }

  def del(c:String):Future[String] = {
    codes.get(c) match {
      case Some(code) => { codes = codes - c; Future.successful(c) }
      case None => Future.failed(new Exception(s"not found: ${c}"))
    }
  }

  def ?(c:String):Future[Code] = codes.get(c) match {
    case Some(code) => Future.successful(code)
    case None => Future.failed(new Exception(s"not found: ${c}"))
  }
}
