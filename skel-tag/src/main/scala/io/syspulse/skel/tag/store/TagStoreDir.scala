package io.syspulse.skel.tag.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import com.typesafe.scalalogging.Logger

import io.syspulse.skel.tag.feed.Feed
import io.syspulse.skel.tag._

import os._

// Preload from file during start
class TagStoreDir(dir:String = "store/",file:Option[String] = None) extends TagStoreMem {
  
  load(dir,file)

  val feedParser = new Feed()

  def load(dir:String,file:Option[String]) = {
    val storeDir = os.Path(dir,os.pwd)
    log.info(s"Loading store: ${storeDir}")

    val tt = os.list(storeDir)
      .filter(f => file.isDefined && file.get == f.toString)
      .map(f => {
        log.info(s"Loading file: ${f}")
        os.read(f)
      })
      .map(data => {
        feedParser.parse(data)
      })
      .flatten

    tt.foreach(t => this.+(t))

    log.info(s"Loaded store: ${size}")
  }

}