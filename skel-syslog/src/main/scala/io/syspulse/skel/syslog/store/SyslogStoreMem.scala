package io.syspulse.skel.syslog.store

import scala.util.Try
import scala.util.{Success,Failure}
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

  def all:Seq[Syslog] = syslogs.values.toSeq

  def size:Long = syslogs.size

  def +(syslog:Syslog):Try[SyslogStore] = { 
    syslogs = syslogs + (Syslog.uid(syslog) -> syslog)
    log.info(s"${syslog}")
    Success(this)
  }

  def del(id:ID):Try[SyslogStore] = { 
    val sz = syslogs.size
    syslogs = syslogs - id;
    log.info(s"${id}")
    if(sz == syslogs.size) Failure(new Exception(s"not found: ${id}")) else Success(this)  
  }

  def ?(id:ID):Try[Syslog] = syslogs.get(id) match {
    case Some(y) => Success(y)
    case None => Failure(new Exception(s"not found: ${id}"))
  }

  def ??(txt:String):List[Syslog] = {
    syslogs.values.filter(y => 
      y.area.matches(txt) || 
      y.msg.matches(txt)
    ).toList
  }

  def scan(txt:String):List[Syslog] = ??(txt)
  def search(txt:String):List[Syslog] = ??(txt)
  def grep(txt:String):List[Syslog] = ??(txt)

}
