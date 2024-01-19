package io.syspulse.skel.plugin.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.plugin._

class PluginStoreMem extends PluginStore {
  val log = Logger(s"${this}")
  
  var plugins: Map[Plugin.ID,Plugin] = Map()

  def all:Seq[Plugin] = plugins.values.toSeq

  def size:Long = plugins.size

  def +(plugin:Plugin):Try[PluginStore] = { 
    plugins = plugins + (plugin.name -> plugin)
    log.info(s"add: ${plugin}")
    Success(this)
  }

  def del(id:Plugin.ID):Try[PluginStore] = { 
    val sz = plugins.size
    plugins = plugins - id;
    log.info(s"del: ${id}")
    if(sz == plugins.size) Failure(new Exception(s"not found: ${id}")) else Success(this)  
  }

  def ?(id:Plugin.ID):Try[Plugin] = plugins.get(id) match {
    case Some(u) => Success(u)
    case None => Failure(new Exception(s"not found: ${id}"))
  }
 
}
