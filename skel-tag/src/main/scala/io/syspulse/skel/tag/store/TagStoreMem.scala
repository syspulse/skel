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
    log.info(s"add: ${tag}")
    Success(this)
  }

  def del(id:String):Try[TagStore] = { 
    val sz = tags.size
    tags = tags - id;
    log.info(s"del: ${id}")
    if(sz == tags.size) Failure(new Exception(s"not found: ${id}")) else Success(this)  
  }

  def ?(id:String):Try[Tag] = tags.get(id) match {
    case Some(t) => Success(t)
    case None => Failure(new Exception(s"not found: ${id}"))
  }

  def ??(tags:String,from:Option[Int],size:Option[Int]):Tags = {
    val tt =
      this.tags.values.filter{ t => 
        t.tags.filter( t => t.toLowerCase.matches(tags.toLowerCase)).size != 0
      }
      .toList.sortBy(_.score).reverse
    
    Tags(tt.drop(from.getOrElse(0)).take(size.getOrElse(10)),Some(tt.size))
  }

}
