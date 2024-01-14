package io.syspulse.skel.plugin.store

import scala.util.Try

import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.skel.plugin._
import io.syspulse.skel.store.Store


trait PluginStore extends Store[Plugin,Plugin.ID] {
  
  def getKey(plugin: Plugin): Plugin.ID = plugin.name
  def +(plugin:Plugin):Try[PluginStore]
  
  def del(id:Plugin.ID):Try[PluginStore]
  def ?(id:Plugin.ID):Try[Plugin]  
  def all:Seq[Plugin]
  def size:Long
  }

