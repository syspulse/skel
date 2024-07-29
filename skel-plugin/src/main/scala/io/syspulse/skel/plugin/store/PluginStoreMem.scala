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
  
  var plugins: Map[PluginDescriptor.ID,PluginDescriptor] = Map()

  def all:Seq[PluginDescriptor] = plugins.values.toSeq

  def size:Long = plugins.size

  def +(p:PluginDescriptor):Try[PluginDescriptor] = { 
    plugins = plugins + (p.name -> p)
    log.info(s"add: ${p}")
    Success(p)
  }

  def del(id:PluginDescriptor.ID):Try[PluginDescriptor.ID] = { 
    val sz = plugins.size
    plugins = plugins - id;
    log.info(s"del: ${id}")
    if(sz == plugins.size) Failure(new Exception(s"not found: ${id}")) else Success(id)  
  }

  def ?(id:PluginDescriptor.ID):Try[PluginDescriptor] = plugins.get(id) match {
    case Some(u) => Success(u)
    case None => Failure(new Exception(s"not found: ${id}"))
  }
 
}
