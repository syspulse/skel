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
  
  def ??(tags:String,from:Option[Int],size:Option[Int]):Tags

  def search(txt:String,from:Option[Int],size:Option[Int]):Tags 

  def typing(txt:String,from:Option[Int],size:Option[Int]):Tags

  def limit(from:Option[Int]=None,size:Option[Int]=None):Seq[Tag] = {
    if(!from.isDefined && !size.isDefined)
      return all
    
    all.drop(from.getOrElse(0)).take(size.getOrElse(10))
  }
}
