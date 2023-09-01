package io.syspulse.skel.syslog.store

import scala.util.Try
import scala.util.{Success,Failure}
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

  def toKey(id:String):ID = id
  def all:Seq[Syslog] = store.all
  def size:Long = store.size
  override def +(u:Syslog):Try[SyslogStoreDir] = super.+(u).flatMap(_ => store.+(u)).map(_ => this)

  override def del(uid:ID):Try[SyslogStoreDir] = super.del(uid).flatMap(_ => store.del(uid)).map(_ => this)
  override def ?(uid:ID):Try[Syslog] = store.?(uid)

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