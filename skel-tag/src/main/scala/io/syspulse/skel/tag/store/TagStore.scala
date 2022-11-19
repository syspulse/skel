package io.syspulse.skel.tag.store

import scala.util.Try

import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.skel.tag._
import io.syspulse.skel.store.Store

import io.syspulse.skel.tag.Config
import io.syspulse.skel.tag.Tag

trait TagStore { //extends Store[Tag,String] {
  
  def +(tag:Tag):Try[TagStore]
  def ?(tags:String):List[Tag]
  def all:Seq[Tag]
  def size:Long
}
