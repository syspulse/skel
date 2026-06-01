package io.syspulse.skel.plugin.store

import scala.util.{Try, Success, Failure}
import scala.concurrent.{Future, ExecutionContext}
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

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  def toKey(id:String):PluginDescriptor.ID = id

  def all: Future[Seq[PluginDescriptor]] = store.all

  def scan():Seq[PluginDescriptor] = {
    load()
    store.all.value.flatMap(_.toOption).getOrElse(Seq.empty)
  }

  def size: Future[Long] = store.size

  // all these should not be supported
  override def +(u:PluginDescriptor): Future[PluginDescriptor] = super.+(u).flatMap(_ => store.+(u))
  override def del(id:PluginDescriptor.ID): Future[PluginDescriptor.ID] = super.del(id).flatMap(_ => store.del(id))
  override def ?(id:PluginDescriptor.ID): Future[PluginDescriptor] = store.?(id)

  def loadPlugins():Int = {
    val pp = scan()
    pp.size
  }

  // create directory
  // os.makeDir.all(os.Path(dir,os.pwd))
}
