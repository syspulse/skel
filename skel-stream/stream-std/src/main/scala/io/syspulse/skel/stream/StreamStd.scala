
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
import io.syspulse.skel.config.Configuration
import io.syspulse.skel.dsl.JS

class StreamStd() {
  val log = Logger(s"${this.getClass().getSimpleName()}")
  
  protected var config:Configuration = Configuration.default
  protected var js:Option[JS] = None

  def withConfig(c:Configuration):StreamStd = {
    this.config = c
    js = config.getString("script").map(s => new JS(s))
    
    this
  }

  implicit val sys: ActorSystem = ActorSystem(s"StreamStd")
  

  val stdinSource: Source[ByteString, Future[IOResult]] = StreamConverters.fromInputStream(() => System.in)
  val outSink = Sink.foreach((data:String) => print(data)) //Sink[ByteString, Future[IOResult]] = StreamConverters.fromOutputStream(() => System.out)

  val flow = stdinSource
    .map(_.utf8String)

  def preProc(data:String):String = {
    if(js.isDefined) {
      js.get.run(Map( ("input" -> data) )).toString
    }
      else data
  }
  def postProc(data:String):String = data

  def run(proc:Option[Flow[String,String,_]]) = {
    log.info(s"flow = ${flow}")

    val f = flow
      .via(Flow.fromFunction(preProc))
      .via(proc.getOrElse(Flow.fromFunction((s:String)=>{log.debug(s);s})))
      .via(Flow.fromFunction(postProc))
      .runWith(outSink)

    val r = Await.result(f,Duration.Inf)
    log.info(s"finished: ${r}")
  }
}