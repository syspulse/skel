package io.syspulse.skel.syslog.store

import scala.util.{Try, Success, Failure}
import scala.concurrent.{Future, ExecutionContext}
import scala.collection.immutable

import com.typesafe.scalalogging.Logger

import os._

import spray.json._
import DefaultJsonProtocol._
import io.syspulse.skel.store.StoreDir

import java.time.format.DateTimeFormatter
import java.time.LocalDateTime
import java.time.ZoneOffset
import io.syspulse.skel.util.Util


import io.syspulse.skel.syslog.Syslog
import io.syspulse.skel.syslog.server.SyslogJson._
import io.syspulse.skel.syslog.Syslog.ID
import io.syspulse.skel.syslog.store._

object SyslogStoreDir {

}

class SyslogStoreDir(dir:String = "store/") extends StoreDir[Syslog,ID](dir) with SyslogStore {
  val store = new SyslogStoreMem

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  def toKey(id:String):ID = id
  def all: Future[Seq[Syslog]] = store.all
  def size: Future[Long] = store.size
  override def +(u:Syslog): Future[Syslog] = super.+(u).flatMap(_ => store.+(u))

  override def del(uid:ID): Future[ID] = super.del(uid).flatMap(_ => store.del(uid))
  override def ?(uid:ID): Future[Syslog] = store.?(uid)

  override def ??(txt:String):Seq[Syslog] = store.??(txt)

  // override def findByXid(xid:String):Option[Syslog] = store.findByXid(xid)
  // override def findByEmail(email:String):Option[Syslog] = store.findByEmail(email)

  override def scan(txt:String):Seq[Syslog] = store.scan(txt)
  override def search(txt:String):Seq[Syslog] = store.search(txt)
  override def grep(txt:String):Seq[Syslog] = store.grep(txt)

  // preload and watch
  load(dir)
  watch(dir)
}
