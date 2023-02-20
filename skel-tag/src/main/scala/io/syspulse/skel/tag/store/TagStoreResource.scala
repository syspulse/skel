package io.syspulse.skel.tag.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import com.typesafe.scalalogging.Logger

import os._

import io.syspulse.skel.tag._

// Preload from file during start
class TagStoreResource(dir:String = "store/tags-default.csv") extends TagStoreMem {
  
  val feedParser = TagCvs.fmtTag.get
  load(dir)

  def load(storeFile:String) = {
    log.info(s"Loading store: resources://${storeFile}")
    val lines =scala.io.Source.fromResource(storeFile).getLines()
    
    val tt = lines.map( data => 
      try {
        feedParser.parse(data)
      } catch {
        case e:Exception => log.error(s"could not parse data: ${data}",e); Seq()
      }
    ).flatten

    tt.foreach(t => this.+(t))

    log.info(s"Loaded store: ${size}")
  }

}