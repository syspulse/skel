package io.syspulse.skel.tag.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import com.typesafe.scalalogging.Logger

import os._

import io.syspulse.skel.tag._

import io.syspulse.skel.tag.TagCsv
// Preload from file during start
class TagStoreFile(storeFile:String) extends TagStoreMem {
  
  val feedParser = TagCsv.fmtTag.get

  val storeDir = os.Path(storeFile,os.pwd)

  log.info(s"Loading file: ${storeFile}")
  
  feedParser
    .parse(os.read(os.Path(storeFile,os.pwd)))
    .foreach( t => this.+(t))
  
  log.info(s"Loaded store: ${size}")
}