package io.syspulse.skel.tag.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import com.typesafe.scalalogging.Logger

import io.syspulse.skel.tag.feed.Feed
import io.syspulse.skel.tag._

import os._

// Preload from file during start
class TagStoreFile(storeFile:String) extends TagStoreMem {
  
  val feedParser = new Feed()

  val storeDir = os.Path(storeFile,os.pwd)
  log.info(s"Loading file: ${storeFile}")
  feedParser
    .parse(os.read(os.Path(storeFile,os.pwd)))
    .foreach( t => this.+(t))
  log.info(s"Loaded store: ${size}")
}