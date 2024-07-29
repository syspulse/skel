package io.syspulse.skel.plugin.store

import scala.util.Try

import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.skel.plugin._
import io.syspulse.skel.store.Store


trait PluginStore extends Store[PluginDescriptor,PluginDescriptor.ID] {
  
  def getKey(plugin: PluginDescriptor): PluginDescriptor.ID = plugin.name
  def +(plugin:PluginDescriptor):Try[PluginDescriptor]
  
  def del(id:PluginDescriptor.ID):Try[PluginDescriptor.ID]
  def ?(id:PluginDescriptor.ID):Try[PluginDescriptor]  
  def all:Seq[PluginDescriptor]
  def size:Long
  }

