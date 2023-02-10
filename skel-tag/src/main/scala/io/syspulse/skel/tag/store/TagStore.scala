package io.syspulse.skel.tag.store

import scala.util.Try

import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.skel.tag._
import io.syspulse.skel.store.Store

import io.syspulse.skel.tag.Config
import io.syspulse.skel.tag.Tag

trait TagStore extends Store[Tag,String] {
  
  def getKey(e:Tag):String = e.id
  def ??(tags:String):Seq[Tag]
}
