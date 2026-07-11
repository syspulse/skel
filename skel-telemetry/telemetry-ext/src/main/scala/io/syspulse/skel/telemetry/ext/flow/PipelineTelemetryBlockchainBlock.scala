package io.syspulse.skel.telemetry.ext.flow

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.{Duration,FiniteDuration}
import scala.concurrent.duration._
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.Future
import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.Logger
import io.jvm.uuid.UUID

import akka.util.ByteString
import akka.http.javadsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.Flow

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout

import io.syspulse.skel
import io.syspulse.skel.Command
import io.syspulse.skel.config._
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._

import io.syspulse.skel.ingest._

import spray.json._
import java.util.concurrent.TimeUnit

import io.syspulse.skel.ingest.flow.Pipeline
import io.syspulse.skel.serde.Parq._

import io.syspulse.skel.telemetry.ext.Config

import io.syspulse.skel.blockchain.Blockchain

import io.syspulse.skel.telemetry.ext.TelemetryExtJson._
import io.syspulse.skel.telemetry.ext.{TelemetryChain,TelemetryExt}
import io.syspulse.skel.telemetry.ext.store.TelemetryExtRegistry._
import io.syspulse.skel.telemetry.ext.Chain

import ObchainJson._

class PipelineTelemetryBlockchainBlock(chain:Blockchain,registry: ActorRef[Command],feed:String,output:String)(implicit config:Config) extends 
      Pipeline[Block,TelemetryChain,TelemetryExt](feed,output,config.throttle,config.delimiter,config.buffer,format=config.format) {
    
  private val log = Logger(s"${this}")

  import akka.actor.typed.scaladsl.adapter._
  implicit val sched:akka.actor.typed.Scheduler = system.toTyped.scheduler
  implicit val ec = system.getDispatcher
  implicit val registryTimeout = Timeout(3000L,TimeUnit.MILLISECONDS)
  // Timeout.create(
  //   Configuration.default.getDuration("http.routes.ask-timeout").getOrElse(java.time.Duration.ofMillis(3000L))
  // )

  val nAll = new AtomicInteger()
  val nTx = new AtomicInteger()
  val nEv = new AtomicInteger()
  @volatile
  var ts0 = System.currentTimeMillis

  override def parse(data: String): Seq[Block] = {
    try {
      Seq(data.parseJson.convertTo[Block])
    } catch {
      case e:Exception => 
        log.error(s"failed to parse: '${data}'",e)
        Seq()
    }    
  }

  def convert(b:Block):Block = {
    b
  }

  override def process:Flow[Block,TelemetryChain,_] = Flow[Block]
    .groupedWithin(config.freq, FiniteDuration(config.throttle, TimeUnit.MILLISECONDS))
    .filter(_.nonEmpty)
    .mapAsync(1)(b => {
      // get and update telemetry
      val f = registry
        .ask(GetTelemetry(None,None,_))
        .map(_.getOrElse(TelemetryChain(key = TelemetryExt.BLOCKCHAIN_KEY, chains = Array(Chain(chain.name, None)))))
        .map(t => {
          t.addTx(
            chain.name, 
            b.foldLeft(0L)(_  + _.transaction_count), 
            Some(b.last.number)
          )
          t
        })
      f
    })
    .mapAsync(1)(t => {
      // save
      val f = registry
        .ask(SaveTelemetry(t,None,_))
        .map(_.getOrElse(TelemetryChain()))
      
      f
    })
    .map(t => {

      t
    }) 
      
  override def transform(o: TelemetryChain): Seq[TelemetryExt] = {
    // match monitor address
    Seq(TelemetryExt(sys = true, sysEventSubject = TelemetryExt.NOTIFY_SUBJECT_BLOCKCHAIN, data = o))
  }
}
