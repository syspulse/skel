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
    // update existing
    // val tag1 = tags.get(tag.id) match {
    //   case Some(tag0) => 
    //     tags = tags + (tag0.id -> tag0.copy(tags = tag0.tags ++ tag.tags))
    //     tag0
    //   case None => 
    //     tags = tags + (tag.id -> tag)
    //     tag
    // }
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

  override def ??(ids:Seq[String]):Seq[Tag] = {
    ids.flatMap(tags.get(_))
  }

  def search(txt:String,from:Option[Int],size:Option[Int]):Tags = {
    if(txt.trim.size < 3 )
      Tags(Seq())
    else
      ???(".*" + txt + ".*",from,size)
  }

  def typing(txt:String,from:Option[Int],size:Option[Int]):Tags = {
    if(txt.trim.size < 3 )
      Tags(Seq())
    else
      ???(txt+".*",from,size)
  }

  def ???(txt:String,from:Option[Int],size:Option[Int]):Tags = {
    val terms = txt.toLowerCase
    val tt =
      this.tags.values.filter{ t => 
        t.id.toLowerCase.matches(terms) ||
        t.tags.filter( tag => tag.toLowerCase.matches(terms)).size > 0
      }
      .toList.sortBy(_.score).reverse
    
    Tags(tt.drop(from.getOrElse(0)).take(size.getOrElse(10)),Some(tt.size))
  }

  def !(id:String,cat:Option[String],tags:Option[Seq[String]]):Try[Tag] = {
    log.info(s"update: ${id},${cat},${tags}")
    val t = for {
        t0 <- {
          // create new 
          ?(id) match {
            case Failure(_) => Success(Tag(id,ts = System.currentTimeMillis, "", Seq()))
            case t0 => t0
          }
        }
        t1 <- Success(if(cat.isDefined) t0.copy(cat = cat.get) else t0)
        t2 <- Success(if(tags.isDefined) t1.copy(tags = tags.get) else t1)
        t3 <- `+`(t2)
      } yield t2
    t
  }

  def find(attr:String,v:Any,from:Option[Int],size:Option[Int]):Tags = {
    log.info(s"attr=(${attr},${v})")
    import io.syspulse.skel.util.Reflect._
    val tt = tags.values.filter(t => t.valueOf[String](attr).map(av => av.toLowerCase.equals(v.toString.toLowerCase)).getOrElse(false)).toSeq
    
    Tags(tt.drop(from.getOrElse(0)).take(size.getOrElse(10)),Some(tt.size))
  }
}
