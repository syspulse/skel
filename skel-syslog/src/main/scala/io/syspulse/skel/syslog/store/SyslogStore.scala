package io.syspulse.skel.syslog.store

import scala.util.Try

import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.skel.syslog._
import io.syspulse.skel.store.Store
import io.syspulse.skel.syslog.Syslog.ID

trait SyslogStore extends Store[Syslog,ID] {
  def getKey(y: Syslog): ID = Syslog.uid(y)
  def +(syslog:Syslog):Try[SyslogStore]
  def del(id:ID):Try[SyslogStore]
  def ?(id:ID):Try[Syslog]
  def all:Seq[Syslog]
  def size:Long

  def ??(txt:String):Seq[Syslog]

  def connect(config:Config):SyslogStore = this

  def scan(txt:String):Seq[Syslog]
  def search(txt:String):Seq[Syslog]
  def grep(txt:String):Seq[Syslog]
}
