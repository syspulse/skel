package io.syspulse.skel.plugin.store

import scala.concurrent.Future

import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.skel.plugin._
import io.syspulse.skel.store.Store


trait PluginStore extends Store[PluginDescriptor,PluginDescriptor.ID] {

  def getKey(plugin: PluginDescriptor): PluginDescriptor.ID = plugin.name
  def +(plugin:PluginDescriptor):Future[PluginDescriptor]

  def del(id:PluginDescriptor.ID):Future[PluginDescriptor.ID]
  def ?(id:PluginDescriptor.ID):Future[PluginDescriptor]
  def all:Future[Seq[PluginDescriptor]]
  def size:Future[Long]

  def loadPlugins():Int
}

