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
import java.net.URLClassLoader
import java.net.URL

// Preload from file during start
class PluginStoreDir(dir:String = "plugins") extends StoreDir[PluginDescriptor,PluginDescriptor.ID](dir) with PluginStore {
  val store = new PluginStoreMem

  def toKey(id:String):PluginDescriptor.ID = id

  def all:Seq[PluginDescriptor] = store.all

  def scan():Seq[PluginDescriptor] = {
    load()
    all
  }

  def size:Long = store.size
  
  // all these should not be supported
  override def +(u:PluginDescriptor):Try[PluginDescriptor] = super.+(u).flatMap(_ => store.+(u))
  override def del(id:PluginDescriptor.ID):Try[PluginDescriptor.ID] = super.del(id).flatMap(_ => store.del(id))
  override def ?(id:PluginDescriptor.ID):Try[PluginDescriptor] = store.?(id)

  def loadPlugins():Int = {
    val pp = scan()
    all.size
  }

  // create directory
  // os.makeDir.all(os.Path(dir,os.pwd))
}