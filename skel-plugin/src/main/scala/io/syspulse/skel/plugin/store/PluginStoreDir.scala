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
class PluginStoreDir(dir:String = "plugins",classMask:Option[String]=None) extends StoreDir[PluginDescriptor,PluginDescriptor.ID](dir) with PluginStore {
  val store = new PluginStoreMem

  def toKey(id:String):PluginDescriptor.ID = id
  def all:Seq[PluginDescriptor] = {
    if(store.size == 0) {
      val storeDir = os.Path(dir,os.pwd)
      if(! os.exists(storeDir)) {
        os.makeDir.all(storeDir)
      }
      
      log.info(s"Scanning dir: ${storeDir}")

      val parent = this.getClass().getClassLoader()
      val cc = os.walk(storeDir)
        .filter(_.toIO.isFile())
        .sortBy(_.toIO.lastModified())
        .flatMap(f => {
          log.info(s"Loading file: ${f}")
          val child:URLClassLoader = new URLClassLoader(Array[URL](new URL(s"file://${f}")), parent)

          log.debug(s"child=${child}, parent=${parent}")
          
          classMask match {
            case Some(mask) => PluginStoreJava.loadFromJars(child,classMask)
            case None => PluginStoreJava.loadFromManifest(child)
          }
          
        })

        cc        

    } else
      store.all    
  }

  def size:Long = store.size
  
  // all these should not be supported
  override def +(u:PluginDescriptor):Try[PluginDescriptor] = super.+(u).flatMap(_ => store.+(u))
  override def del(id:PluginDescriptor.ID):Try[PluginDescriptor.ID] = super.del(id).flatMap(_ => store.del(id))
  override def ?(id:PluginDescriptor.ID):Try[PluginDescriptor] = store.?(id)

  // create directory
  os.makeDir.all(os.Path(dir,os.pwd))
  
}