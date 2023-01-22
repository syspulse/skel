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

  def +(code:Code):Try[CodeStore] = { 
    codes = codes + (code.authCode -> code); Success(this)
  }

  def !(code:Code):Try[CodeStore] = { 
    val old = codes.getOrElse(code.authCode,code)
    // update onl with userId
    codes = codes + (code.authCode -> code.copy(xid = old.xid)); 
    Success(this)
  }
  
  def del(c:String):Try[CodeStore] = { 
    codes.get(c) match {
      case Some(auth) => { codes = codes - c; Success(this) }
      case None => Failure(new Exception(s"not found: ${c}"))
    }
  }

  def -(code:Code):Try[CodeStore] = { 
    val sz = codes.size
    codes = codes - code.authCode;
    if(sz == codes.size) Failure(new Exception(s"not found: ${code}")) else Success(this)
  }

  def ?(c:String):Try[Code] = codes.get(c) match {
    case Some(code) => Success(code)
    case None => Failure(new Exception(s"not found: ${c}"))
  }
}


