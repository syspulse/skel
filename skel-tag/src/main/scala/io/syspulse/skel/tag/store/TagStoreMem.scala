package io.syspulse.skel.tag.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import com.typesafe.scalalogging.Logger

import io.syspulse.skel.tag._

class TagStoreMem extends TagStore {
  val log = Logger(s"${this}")
  
  var tags: Map[String,Tag] = Map()

  def all:Seq[Tag] = tags.values.toSeq

  def size:Long = tags.size

  def +(tag:Tag):Try[TagStore] = { 
    tags = tags + (tag.id -> tag)
    Success(this)
  }

  def ?(tags:String):List[Tag] = {
    this.tags.values.filter{ t => 
      t.tags.filter( t => t.toLowerCase.matches(tags.toLowerCase)).size != 0
    }
    .toList.sortBy(_.score).reverse
  }

}
