
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
import akka.stream.scaladsl.Framing

class JSScript(val src:String) extends JS

class StreamStd() {
  val log = Logger(s"${this.getClass().getSimpleName()}")
  
  protected var config:Configuration = Configuration.default
  protected var js:Option[JSScript] = None
  protected var script:Option[String] = None

  def withConfig(c:Configuration):StreamStd = {
    this.config = c
        
    val s = c.getString("script").getOrElse("")
    script = if(s.trim.startsWith("@")) {
      Some(scala.io.Source.fromFile(s.trim.drop(1)).getLines().mkString("\n"))
    } else 
      None

    js = script.map(s => new JSScript(s))

    this
  }

  implicit val sys: ActorSystem = ActorSystem(s"StreamStd")
  

  val stdinSource: Source[ByteString, Future[IOResult]] = StreamConverters.fromInputStream(() => System.in,chunkSize = 8192)
  val outSink = Sink.foreach((data:String) => print(data)) //Sink[ByteString, Future[IOResult]] = StreamConverters.fromOutputStream(() => System.out)
  val outFlow = Flow.fromFunction((s:String) => s)

  val flow = stdinSource
    .via(Framing.delimiter(ByteString("\n"), Int.MaxValue))
    .map(_.utf8String)
    // .map(_.utf8String.split("\n").toList)
    // .mapConcat(identity)

  def preProc(data:String):String = {
    if(js.isDefined) {
      js.get.run(js.get.src,Map( ("input" -> data) )).toString
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
      .via(outFlow)
      .runWith(outSink)

    val r = Await.result(f,Duration.Inf)
    log.info(s"finished: ${r}")
  }
}