package io.syspulse.skel.syslog.store

import scala.util.{Success,Failure}
import scala.concurrent.Future
import scala.collection.immutable

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.syslog._
import io.syspulse.skel.syslog.Syslog.ID

class SyslogStoreMem extends SyslogStore {
  val log = Logger(s"${this}")

  var syslogs: Map[ID,Syslog] = Map()

  def all: Future[Seq[Syslog]] = Future.successful(syslogs.values.toSeq)

  def size: Future[Long] = Future.successful(syslogs.size.toLong)

  def +(syslog:Syslog): Future[Syslog] = {
    syslogs = syslogs + (Syslog.uid(syslog) -> syslog)
    log.info(s"${syslog}")
    Future.successful(syslog)
  }

  def del(id:ID): Future[ID] = {
    val sz = syslogs.size
    syslogs = syslogs - id
    log.info(s"${id}")
    if(sz == syslogs.size) Future.failed(new Exception(s"not found: ${id}")) else Future.successful(id)
  }

  def ?(id:ID): Future[Syslog] = syslogs.get(id) match {
    case Some(y) => Future.successful(y)
    case None => Future.failed(new Exception(s"not found: ${id}"))
  }

  def ??(txt:String):Seq[Syslog] = {
    syslogs.values.filter(y =>
      y.scope.map(_.matches(txt)).getOrElse(false) ||
      y.msg.matches(txt)
    ).toSeq
  }

  def scan(txt:String):Seq[Syslog] = ??(txt)
  def search(txt:String):Seq[Syslog] = ??(txt)
  def grep(txt:String):Seq[Syslog] = ??(txt)

}
