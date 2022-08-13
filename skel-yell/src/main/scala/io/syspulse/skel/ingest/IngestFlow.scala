package io.syspulse.skel.ingest

import scala.jdk.CollectionConverters._

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

import scala.concurrent.ExecutionContext.Implicits.global 
import scala.util.Random
import java.nio.file.{Paths,Files}
import scala.jdk.CollectionConverters._

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.video._

trait IngestFlow[T,D] {
  private val log = Logger(s"${this}")
  implicit val system = ActorSystem("ActorSystem-IngestFlow")

  var defaultSource:Option[Source[ByteString,_]] = None

  def parse(data:String):Seq[T]

  def sink():Sink[D,Any]

  def source():Source[ByteString,_] = if(defaultSource.isDefined) defaultSource.get else IngestFlow.fromStdin()

  def transform(t:T):D

  def debug = Flow.fromFunction( (data:ByteString) => { log.debug(s"data=${data}"); data})

  def run() = {    
    val flow =
      source()
      .via(debug)
      .log("ingest-flow")
      .mapConcat(txt => parse(txt.utf8String))
      .map( data => { log.info(s"data=${data}"); data})
      .viaMat(KillSwitches.single)(Keep.right)
      .map(t => transform(t))
      .runWith(sink())      

    //val r = Await.result(result, timeout())
    log.info(s"flow: ${flow}")
    flow
  }

  def from(src:Source[ByteString,_]):IngestFlow[T,D] = {
    defaultSource = Some(src)
    this
  }
}

object IngestFlow {
  def fromStdin():Source[ByteString, Future[IOResult]] = StreamConverters.fromInputStream(() => System.in)
  // this is non-streaming simple ingester for TmsParser. Reads full file, flattens it and parses into Stream of Tms objects
  def fromFile(file:String = "/dev/stdin"):Source[ByteString, Future[IOResult]] = FileIO.fromPath(Paths.get(file),chunkSize = Files.size(Paths.get(file)).toInt)
}