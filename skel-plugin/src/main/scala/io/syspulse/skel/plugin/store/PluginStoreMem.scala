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

  def +(p:Plugin):Try[Plugin] = { 
    plugins = plugins + (p.name -> p)
    log.info(s"add: ${p}")
    Success(p)
  }

  def del(id:Plugin.ID):Try[Plugin.ID] = { 
    val sz = plugins.size
    plugins = plugins - id;
    log.info(s"del: ${id}")
    if(sz == plugins.size) Failure(new Exception(s"not found: ${id}")) else Success(id)  
  }

  def ?(id:Plugin.ID):Try[Plugin] = plugins.get(id) match {
    case Some(u) => Success(u)
    case None => Failure(new Exception(s"not found: ${id}"))
  }
 
}
