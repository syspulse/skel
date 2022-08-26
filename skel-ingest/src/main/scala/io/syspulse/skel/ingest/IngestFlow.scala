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

trait IngestFlow[I,T,O] {
  private val log = Logger(s"${this}")
  implicit val system = ActorSystem("ActorSystem-IngestFlow")
  
  val retrySettings = RestartSettings(
    minBackoff = FiniteDuration(3000,TimeUnit.MILLISECONDS),
    maxBackoff = FiniteDuration(10000,TimeUnit.MILLISECONDS),
    randomFactor = 0.2
  )
  //.withMaxRestarts(10, 5.minutes)

  var count:Long = 0L

  var defaultSource:Option[Source[ByteString,_]] = None
  var defaultSink:Option[Sink[O,_]] = None

  def parse(data:String):Seq[I]

  def sink():Sink[O,Any] = if(defaultSink.isDefined) defaultSink.get else Sink.foreach(println _)

  def sink0():Sink[O,Any] = Sink.ignore

  def source():Source[ByteString,_] = if(defaultSource.isDefined) defaultSource.get else StreamConverters.fromInputStream(() => System.in)

  def flow:Flow[I,T,_]

  def transform(t:T):Seq[O]

  def debug = Flow.fromFunction( (data:ByteString) => { log.debug(s"data=${data}"); data})

  def counter = Flow[ByteString].map(t => { count = count + 1; t})

  def run() = {    
    val flowGraph =
      source()
      .via(debug)
      .via(counter)      
      .mapConcat(txt => parse(txt.utf8String))
      .via(flow)
      .viaMat(KillSwitches.single)(Keep.right)
      .mapConcat(t => transform(t))
      .log("ingest-flow")
      .alsoTo(sink0())
    
    val flowing = flowGraph
      .runWith(sink())

    //val r = Await.result(result, timeout())
    log.info(s"graph: ${flowGraph}: flow=${flowing}")
    flowing
  }

  def from(src:Source[ByteString,_]):IngestFlow[I,T,O] = {
    defaultSource = Some(src)
    this
  }
}
