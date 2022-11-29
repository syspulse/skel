package io.syspulse.skel.telemetry.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import com.typesafe.scalalogging.Logger

import io.syspulse.skel.telemetry._
import io.syspulse.skel.telemetry.Telemetry.ID

import os._
import io.syspulse.skel.telemetry.parser.TelemetryParser

class TelemetryStoreDir(dir:String = "store/",parser:TelemetryParser) extends TelemetryStoreMem {
 
  load(dir)

  def load(dir:String) = {
    val storeDir = os.Path(dir,os.pwd)
    log.info(s"Loading store: ${storeDir}")

    val dd = os.walk(storeDir)
      .filter(_.toIO.isFile())
      .map(f => {
        log.info(s"Loading file: ${f}")
        os.read(f)
      })
      .map(data => {
        parser.fromString(data)
      })
      .flatten

    dd.foreach(t => this.+(t))

    log.info(s"Loaded store: ${size}")
  }
}