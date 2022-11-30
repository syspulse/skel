package io.syspulse.skel.video.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import com.typesafe.scalalogging.Logger

import io.syspulse.skel.video._
import io.syspulse.skel.video.VID

import os._
import io.syspulse.skel.video.tms.TmsParser

// Preload from file during start
class VideoStoreDir(dir:String = "store/") extends VideoStoreMem {
  
  load(dir)

  def load(dir:String) = {
    val storeDir = os.Path(dir,os.pwd)
    log.info(s"Loading store: ${storeDir}")

    val vv = os.walk(storeDir)
      .filter(_.toIO.isFile())
      .map(f => {
        log.info(s"Loading file: ${f}")
        os.read(f)
      })
      .map(data => {
        TmsParser.fromString(data).map( tms => {
          Video(VID(tms.id),tms.title)
        })
      })
      .flatten

    vv.foreach(v => this.+(v))

    log.info(s"Loaded store: ${size}")
  }

}