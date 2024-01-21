package io.syspulse.skel.auth.code

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

class CodeStoreMem extends CodeStore {
  val log = Logger(s"${this}")

  var codes: Map[String,Code] = Map()

  def all:Seq[Code] = codes.values.toSeq

  def getByToken(accessToken:String):Option[Code] = {
    codes.values.find(_.accessToken == Some(accessToken))
  }

  def size:Long = codes.size

  def +(code:Code):Try[Code] = { 
    codes = codes + (code.code -> code); Success(code)
  }

  def !(code:Code):Try[Code] = { 
    val old = codes.getOrElse(code.code,code)
    // update onl with userId
    codes = codes + (code.code -> code.copy(xid = old.xid)); 
    Success(code)
  }
  
  def del(c:String):Try[String] = { 
    codes.get(c) match {
      case Some(auth) => { codes = codes - c; Success(c) }
      case None => Failure(new Exception(s"not found: ${c}"))
    }
  }

  def ?(c:String):Try[Code] = codes.get(c) match {
    case Some(code) => Success(code)
    case None => Failure(new Exception(s"not found: ${c}"))
  }
}


