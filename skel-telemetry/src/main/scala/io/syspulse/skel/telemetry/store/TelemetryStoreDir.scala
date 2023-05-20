package io.syspulse.skel.telemetry.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import com.typesafe.scalalogging.Logger

import io.syspulse.skel.telemetry._
import io.syspulse.skel.telemetry.Telemetry.ID

import os._
import io.syspulse.skel.telemetry.parser.TelemetryParser
import io.syspulse.skel.cron.Cron
import io.syspulse.skel.store.StoreDir

import io.syspulse.skel.telemetry.TelemetryJson._
import io.syspulse.skel.store.ExtFormat
import io.syspulse.skel.telemetry.parser.TelemetryParserDefault
import io.syspulse.skel.telemetry.server.Telemetrys


import io.syspulse.skel.telemetry.parser.TelemetryJsonCsvFormat._

class TelemetryStoreDir(dir:String = "store/",parser:TelemetryParser,cron:Option[String],eviction:Option[Long]=None) 
  extends StoreDir[Telemetry,ID](dir) with TelemetryStore { 
  
  val store = new TelemetryStoreMem

  def toKey(id:ID):String = id
  
  def all:Seq[Telemetry] = store.all

  def ???(ts0:Long,ts1:Long,from:Option[Int],size:Option[Int]):Telemetrys = store.???(ts0,ts1,from,size)
  
  //override def all(from:Option[Int],size:Option[Int]):Seq[Telemetry] = store.all(from,size)
  def size:Long = store.size

  override def +(u:Telemetry):Try[TelemetryStoreDir] = super.+(u).flatMap(_ => store.+(u)).map(_ => this)
  override def del(id:ID):Try[TelemetryStoreDir] = {
    store.del(id) match {
      case Success(_) => 
      case Failure(e) =>
        log.warn(s"faild to delete: ${id}: ${e.getMessage()}")
    }
    super.del(id).map(_ => this)
  }
  override def ?(id:ID):Try[Telemetry] = store.?(id)
  override def ??(ids:Seq[ID]):Seq[Telemetry] = store.??(ids)
  override def ?(id:ID,ts0:Long,ts1:Long,op:Option[String] = None):Seq[Telemetry] = store.?(id,ts0,ts1,op)
  override def ??(id:ID,ts0:Long,ts1:Long,op:Option[String]):Option[Telemetry] = store.??(id,ts0,ts1,op)
  override def ??(txt:String,ts0:Long,ts1:Long):Seq[Telemetry] = store.??(txt,ts0,ts1)

  override def scan(txt:String):Seq[Telemetry] = store.scan(txt)
  override def search(txt:String,ts0:Long,ts1:Long):Seq[Telemetry] = store.search(txt,ts0,ts1)

  // not supported !
  override def clean():Try[TelemetryStoreDir] = store.clean().map(_ => this)


  // preload and watch
  load(dir)
  watch(dir)

  // load(dir)
  // def load(dir:String) = {
  
  //   val storeDir = os.Path(dir,os.pwd)
  //   log.info(s"Loading store: ${storeDir}")

  //   val dd = os.walk(storeDir)
  //     .filter(_.toIO.isFile())
  //     .map(f => {
  //       log.info(s"Loading file: ${f}")
  //       os.read(f)
  //     })
  //     .map(data => {
  //       parser.fromString(data)
  //     })
  //     .flatten

  //   dd.foreach(t => this.+(t))

  //   log.info(s"Loaded store: ${size}")
  // }


  if(cron.isDefined) {
    val c = new Cron((_) => {
        load(dir)
        true
      },
      expr = cron.get,
      cronName = "TelemetryStoreDir",
      jobName = "load"
    )
    c.start
  }

  @volatile
  var terminated = false

  if(eviction.isDefined) {
    val thr = new Thread() {
      override def run() = {
        log.info(s"eviction: ${eviction}")
        while( !terminated ) {
          Thread.sleep(eviction.get)

          val now = System.currentTimeMillis
          val tt = store.???(0L,now - eviction.get)
          log.info(s"eviction: ${tt.total}: store=${store.size}")
          
          tt.data.foreach( t => store.del(t.id))

          log.info(s"eviction: store=${store.size}")
        }        
      }
    }
    thr.setDaemon(true)
    thr.start()
  }  
}