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

// ATTENTION
import io.syspulse.skel.service.JsonCommon
import spray.json._
import spray.json.{DefaultJsonProtocol,NullOptions}

import io.syspulse.skel.stream.StreamStd
import io.syspulse.skel.stream.Config
import io.syspulse.skel.dsl.JS
import akka.stream.scaladsl.Tcp
import java.net.InetSocketAddress
import akka.stream.RestartSettings
import akka.stream.scaladsl.RestartFlow
import scala.concurrent.duration.FiniteDuration
import akka.stream.scaladsl.Framing
import akka.NotUsed


class StreamTcp() extends StreamStd {

  val restartSettings = RestartSettings(FiniteDuration(1000L,"ms"), FiniteDuration(1000L,"ms"), 0.2)//.withMaxRestarts(10, 1.minute)

  val flowTcp = Tcp()
    .outgoingConnection(remoteAddress = new InetSocketAddress("localhost",1883))
    .log("TCP")

  val flowTcpRestart = RestartFlow.withBackoff(restartSettings)(() => flowTcp)
  
  val source:Source[ByteString, Future[IOResult]] = StreamConverters.fromInputStream(() => System.in)
    
  val flow1 = Flow[ByteString].map(v => {println(s"====> ${v.utf8String}"); v})
        
  val g = Flow.fromSinkAndSourceCoupled(Sink.foreach((v:Any) => println(v)),source) //to(Sink.foreach(println(_)))
  val rg = flowTcpRestart.join(g)
}