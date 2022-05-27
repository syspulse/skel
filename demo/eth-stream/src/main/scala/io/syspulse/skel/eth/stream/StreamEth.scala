package io.syspulse.skel.eth.stream

import scala.concurrent.Future
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, IOResult, Materializer}
import akka.stream.scaladsl.{Sink, Source, StreamConverters,Flow}
import akka.util.ByteString
import akka.stream.scaladsl.Framing
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Random

import com.typesafe.scalalogging.Logger

// ATTENTION
import io.syspulse.skel.service.JsonCommon
import spray.json._
import spray.json.{DefaultJsonProtocol,NullOptions}

import io.syspulse.skel.stream.StreamStd
import io.syspulse.skel.dsl.JS
import java.time.LocalDateTime
import java.time.ZonedDateTime
import scala.util.Try
import scala.util.Success

import io.syspulse.skel.eth.script.Scripts

class StreamEth(config:Config) {
  val log = Logger(s"${this.getClass().getSimpleName()}")
  implicit val sys: ActorSystem = ActorSystem(s"StreamEth")

  import EthJson._
  import DefaultJsonProtocol._

  Scripts.+(config.script)
  println(Scripts.scripts)

  val source: Source[ByteString, Future[IOResult]] = StreamConverters
    .fromInputStream(() => System.in,chunkSize = 8192)
    .via(Framing.delimiter(ByteString("\n"), Int.MaxValue))

  val outSink = Sink.foreach((data:String) => print(data)) //Sink[ByteString, Future[IOResult]] = StreamConverters.fromOutputStream(() => System.out)
  
  val outFlow = Flow.fromFunction((s:String) => s)

  // val flow = source  
    // .map(_.utf8String)
    // .map(_.utf8String.split("\n").toList)
    // .mapConcat(identity)

  //override val outSink = Sink.ignore

  val txFlow = Flow[String].map { data =>
    log.debug(s"${data}")
    data.parseJson.convertTo[Tx]
  }

  val interceptFlow = Flow[Tx].map { tx =>
    Scripts.scripts.map {
      case(userScript,userAlarms) => {
        val js = userScript.js
        val r = if(js.isDefined()) js.run(
          Map( 
            ("from_address" -> tx.fromAddress),
            ("to_address" -> tx.toAddress.getOrElse("null")),
            ("value" -> tx.value),
          )
        ) else null

        log.debug(s"tx: ${tx}: ${userScript}: ${Console.YELLOW}${r}${Console.RESET}")

        if(r == null) "" 
        else {
          val txt = s"${r}"
          userAlarms.filter(_.to.isEnabled).map{ ua => 
            ua.to.send(txt)
          }
          .mkString(",") + "\n"
        }
      }
    }
    
  }

  def run() = {

    val f = source
      .map(_.utf8String)
      .via(txFlow)
      .via(interceptFlow)
      .mapConcat(identity)
      .filter(!_.isBlank())
      .via(outFlow)
      .runWith(outSink)

    val r = Await.result(f,Duration.Inf)
    log.info(s"finished: ${r}") 
  }
}