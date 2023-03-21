package io.syspulse.skel.tag

import scala.jdk.CollectionConverters._

import scala.util.Random
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.util.Util
import io.syspulse.skel.tag.Tag
import io.syspulse.skel.store.ExtFormat

class TagCvs extends ExtFormat[Tag] {
  val log = Logger(s"${this}")

  def parse(data:String):Seq[Tag] = {
    decode(data) match {
      case Success(dd) => dd
      case Failure(e) => Seq()
    }
  }

  def decode(data:String):Try[Seq[Tag]] = {
    log.debug(s"data='${data}'")
    val dd = data.split("\\n").toIndexedSeq.map(_.trim).filter(_.nonEmpty).flatMap( s => s.split(",",-1).toList match {      
      case id :: ts :: cat :: tags :: score :: sid :: Nil =>         
        try {
          val ts1 = if(ts.toLong > 9000000000L) ts.toLong else ts.toLong * 1000L
          Some(Tag(id, ts1, cat, tags = Util.csvToList(tags),Some(score.toLong),Some(sid.toLong)))
        } catch { case e:Exception => log.error(s"failed to parse: ${s}"); None}
      case id :: ts :: cat :: tags :: score :: Nil => 
        try {
          val ts1 = if(ts.toLong > 9000000000L) ts.toLong else ts.toLong * 1000L
          Some(Tag(id, ts1, cat, tags = Util.csvToList(tags),Some(score.toLong)))
        } catch { case e:Exception => log.error(s"failed to parse: ${s}"); None}
      case id :: ts :: cat :: tags :: Nil => 
        try {
          val ts1 = if(ts.toLong > 9000000000L) ts.toLong else ts.toLong * 1000L
          Some(Tag(id,ts1,cat, tags = Util.csvToList(tags)))
        } catch { case e:Exception => log.error(s"failed to parse: ${s}"); None}

      // old format
      case id :: ts :: cat_tags :: Nil => 
        try {
          val ts1 = if(ts.toLong > 9000000000L) ts.toLong else ts.toLong * 1000L          
          val cat::tags = cat_tags.split(";").toList

          Some(Tag(id,ts1,cat, tags = Util.csvToList(tags.mkString(";"))))
        } catch { case e:Exception => log.error(s"failed to parse: ${s}"); None}
      case _ => 
        log.error(s"faile to decode: '${data}'")
        None
    })
    Success(dd)
  }

  def encode(e:Tag):String = e.toCSV
}

object TagCvs {
  
  implicit val fmtTag = Some(new TagCvs())
}