
package io.syspulse.skel.stream

import scala.concurrent.Future
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, IOResult, Materializer}
import akka.stream.scaladsl.{Sink, Source, StreamConverters,Flow}
import akka.util.ByteString
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Random

import com.typesafe.scalalogging.Logger

class StreamStd(config:Config) {
  val log = Logger(s"${this.getClass().getSimpleName()}")

  implicit val sys: ActorSystem = ActorSystem(s"StreamStd")
  //implicit val mat: Materializer = ActorMaterializer()

  val stdinSource: Source[ByteString, Future[IOResult]] = StreamConverters.fromInputStream(() => System.in)
  val stdoutSink: Sink[ByteString, Future[IOResult]] = StreamConverters.fromOutputStream(() => System.out)

  //def capitaliseByteString(byteString: ByteString): ByteString = ByteString(byteString.utf8String.toUpperCase)

  val flow = stdinSource
    .map(_.utf8String)

  def run(proc:Option[Flow[String,String,_]]) = {
    log.info(s"flow = ${flow}")

    val f = flow
      .via(proc.getOrElse(Flow.fromFunction((s:String)=>{log.debug(s);s})))
      .map(s => ByteString(s))
      .runWith(stdoutSink)

    val r = Await.result(f,Duration.Inf)
    log.info(s"finished: ${r}")
  }
}