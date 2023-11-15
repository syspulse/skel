package io.syspulse.skel.ingest

import scala.jdk.CollectionConverters._

import java.nio.file.StandardOpenOption._

import akka.actor.ActorSystem
import akka.stream.scaladsl.Keep
import akka.{Done, NotUsed}
import akka.util.ByteString

import com.typesafe.scalalogging.Logger

import scala.concurrent.duration.{Duration,FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future}

import akka.stream.ActorMaterializer
import akka.stream._
import akka.stream.scaladsl._

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter

import java.nio.file.{Path,Paths, Files}
import akka.stream.alpakka.file.scaladsl.LogRotatorSink

import scala.concurrent.ExecutionContext.Implicits.global 
import scala.util.Random
import java.nio.file.{Paths,Files}
import scala.jdk.CollectionConverters._

import io.syspulse.skel.Ingestable
import io.syspulse.skel
import io.syspulse.skel.util.Util
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.Http
import java.util.concurrent.TimeUnit
import akka.event.Logging

// Source[ByteString] -> InputObject [I] -> TransformedObject [T] -> OutputObject [O] -> Sink[O]
trait IngestFlow[I,T,O] {
  private val log = Logger(s"${this}")
  implicit val system = ActorSystem("ActorSystem-IngestFlow")

  def ingestFlowName() = "ingest-flow"
  
  val retrySettings:Option[RestartSettings] = None
  // RestartSettings(
  //   minBackoff = FiniteDuration(3000,TimeUnit.MILLISECONDS),
  //   maxBackoff = FiniteDuration(10000,TimeUnit.MILLISECONDS),
  //   randomFactor = 0.2
  // )
  //.withMaxRestarts(10, 5.minutes)
  
  // used by Flow.log()
  val logLevels =
      Attributes.createLogLevels(Logging.DebugLevel, Logging.DebugLevel, Logging.DebugLevel)

  val cr = new CollectorRegistry(true);
  val countBytes: Counter = Counter.build().name("ingest_bytes").help("total bytes").register(cr)
  val countInput: Counter = Counter.build().name("ingest_input").help("input data").register(cr)
  val countObj: Counter = Counter.build().name("ingest_obj").help("total objects")register(cr)
  val countOutput: Counter = Counter.build().name("ingest_output").help("output data").register(cr)

  var defaultSource:Option[Source[ByteString,_]] = None
  var defaultSink:Option[Sink[O,_]] = None

  def parse(data:String):Seq[I]

  def sink():Sink[O,Any] = if(defaultSink.isDefined) defaultSink.get else Sink.foreach(println _)

  def sink0():Sink[O,Any] = Sink.ignore

  def source():Source[ByteString,_] = if(defaultSource.isDefined) defaultSource.get else StreamConverters.fromInputStream(() => System.in)

  // flow shaping (throttle, etc)
  def shaping:Flow[T,T,_]

  def process:Flow[I,T,_]

  def transform(t:T):Seq[O]

  def debug = Flow.fromFunction( (data:ByteString) => { log.debug(s"data=${data}"); data})

  def counterBytes = Flow[ByteString].map(t => { countBytes.inc(t.size); t})
  def counterI = Flow[I].map(t => { countInput.inc(); t})
  def counterT = Flow[T].map(t => { countObj.inc(); t})
  def counterO = Flow[O].map(t => { countOutput.inc(); t})

  def run() = {
    val f0 = source()
      .via(debug)
      .via(counterBytes)      
      .mapConcat(txt => {
        // ATTENTION: replace with ByteString because it is impossible to properly parse BinaryData !
        parse(txt.utf8String)
      })
      .via(counterI)
    
    val f1 = if(retrySettings.isDefined) {
      RestartSource.onFailuresWithBackoff(retrySettings.get) { () =>
        log.info(s"source retry: ${retrySettings.get}")
        f0
      } 
    }
    else
      f0

    val f2 = f1
      //.via(shaping)
      .via(process)
      .via(shaping)
      .via(counterT)
      .viaMat(KillSwitches.single)(Keep.right)
      .mapConcat(t => transform(t))
      .via(counterO)
      .log(ingestFlowName()).withAttributes(logLevels)
      .alsoTo(sink0())
    
    val f3 = f2

    val mat = f3.runWith(sink())

    log.debug(s"f1=${f1}: f2=${f2}: graph: ${f3}: flow=${mat}")
    mat
  }

  def from(src:Source[ByteString,_]):IngestFlow[I,T,O] = {
    defaultSource = Some(src)
    this
  }
}
