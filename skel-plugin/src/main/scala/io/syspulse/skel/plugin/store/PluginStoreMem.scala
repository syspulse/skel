package io.syspulse.skel.plugin.store

import scala.util.{Success,Failure}
import scala.concurrent.Future
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

  def all: Future[Seq[PluginDescriptor]] = Future.successful(plugins.values.toSeq)

  def size: Future[Long] = Future.successful(plugins.size.toLong)

  def +(p:PluginDescriptor): Future[PluginDescriptor] = {
    plugins = plugins + (p.name -> p)
    log.info(s"add: ${p}")
    Future.successful(p)
  }

  def del(id:PluginDescriptor.ID): Future[PluginDescriptor.ID] = {
    val sz = plugins.size
    plugins = plugins - id
    log.info(s"del: ${id}")
    if(sz == plugins.size) Future.failed(new Exception(s"not found: ${id}")) else Future.successful(id)
  }

  def ?(id:PluginDescriptor.ID): Future[PluginDescriptor] = plugins.get(id) match {
    case Some(u) => Future.successful(u)
    case None => Future.failed(new Exception(s"not found: ${id}"))
  }

  def loadPlugins():Int = 0

}
