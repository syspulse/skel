package io.syspulse.skel.plugin.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import com.typesafe.scalalogging.Logger

import os._
import io.jvm.uuid._

import spray.json._
import DefaultJsonProtocol._

import io.syspulse.skel.store.StoreDir

import io.syspulse.skel.plugin._

import io.syspulse.skel.plugin.PluginJson._

// Preload from file during start
class PluginStoreDir(dir:String = "store/plugins") extends StoreDir[Plugin,Plugin.ID](dir) with PluginStore {
  val store = new PluginStoreMem

  def toKey(id:String):Plugin.ID = id
  def all:Seq[Plugin] = store.all
  def size:Long = store.size
  override def +(u:Plugin):Try[PluginStoreDir] = super.+(u).flatMap(_ => store.+(u)).map(_ => this)

  override def del(id:Plugin.ID):Try[PluginStoreDir] = super.del(id).flatMap(_ => store.del(id)).map(_ => this)
  override def ?(id:Plugin.ID):Try[Plugin] = store.?(id)

  // create directory
  os.makeDir.all(os.Path(dir,os.pwd))

  // preload
  load(dir)
}