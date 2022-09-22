package io.syspulse.skel.notify

import scala.collection.immutable
import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

abstract class NotifyReceiver[R] {
  def send(title:String,msg:String):Try[R]
}

class NotifyEmail(to:String) extends NotifyReceiver[String] {
  def send(title:String,msg:String):Try[String] = {
    Success("ok")
  }
}

class NotifyWebsocket(id:String) extends NotifyReceiver[Int] {
  def send(title:String,msg:String):Try[Int] = {
    Success(0)
  }
}

class NotifyStdout() extends NotifyReceiver[Option[_]] {
  def send(title:String,msg:String):Try[Option[_]] = {
    println(s"title=${title},msg=${msg}")
    Success(None)
  }
}

object Notification {
  val log = Logger(s"${this}")

  def send[R](n:NotifyReceiver[R],title:String,msg:String) = {
    log.info(s"($title,$msg)-> ${n}")
    n.send(title,msg)
  }

  def broadcast(n:Seq[NotifyReceiver[_]],title:String,msg:String):Seq[Try[_]] = {
    log.info(s"($title,$msg)-> ${n}")
    n.map( n => n.send(title,msg)).toSeq
  }
}

