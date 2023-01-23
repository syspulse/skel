package io.syspulse.skel.auth.cred

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import com.typesafe.scalalogging.Logger

import os._

import spray.json._
import DefaultJsonProtocol._

// Preload from file during start
class CredStoreDir(dir:String = "store/") extends CredStoreMem {
  import CredJson._

  load(dir)

  def load(dir:String) = {
    val storeDir = os.Path(dir,os.pwd)
    log.info(s"Loading dir store: ${storeDir}")

    val vv = os.walk(storeDir)
      .filter(_.toIO.isFile())
      .map(f => {
        log.info(s"Loading file: ${f}")
        os.read(f)
      })
      .map(fileData => fileData.split("\n").map { data =>
        try {
          val c = data.parseJson.convertTo[Cred]
          log.debug(s"c=${c}")
          Seq(c)
        } catch {
          case e:Exception => log.error(s"could not parse data: ${data}",e); Seq()
        }
      })
      .flatten // file
      .flatten // files

    vv.foreach(v => this.+(v))

    log.info(s"Loaded store: ${size}")
  }

}