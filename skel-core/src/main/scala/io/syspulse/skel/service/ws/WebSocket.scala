package io.syspulse.skel.service.ws

import com.typesafe.scalalogging.Logger

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

import scala.concurrent.Future
import akka.util.Timeout

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.StatusCodes._

import io.syspulse.skel.service.CommonRoutes
import akka.actor.typed.ActorSystem
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.SourceQueueWithComplete
import akka.stream.OverflowStrategy
import akka.NotUsed
import akka.http.scaladsl.model.ws.TextMessage
import akka.http.scaladsl.model.ws.Message
import akka.stream.ActorMaterializer
import akka.actor.ActorRef
import scala.concurrent.ExecutionContext
import scala.util.{Success,Failure}
import akka.stream.Materializer


abstract class WebSocket()(implicit ex:ExecutionContext) {
  val log = Logger(s"${this}")

  protected var clients: List[ActorRef] = List()
  
  def process(m:Message,a:ActorRef):Message = ???

  def wsFlow()(implicit mat:Materializer): Flow[Message, Message, Any] = {
    val (wsActor, wsSource) = Source.actorRef[Message](32, OverflowStrategy.dropNew).preMaterialize()

    clients = clients :+ wsActor

    // it must be coupled to detect WS client disconnects!
    val flow = Flow.fromSinkAndSourceCoupled(
      Sink.foreach{ m:Message =>         
        process(m,wsActor)
      },
      wsSource
        .map(m => {
          log.debug(s"connection=${wsActor}: ${m}")
          m
        })
    ).watchTermination()( (prevMatValue, f) => {
      // this function will be run when the stream terminates
      // the Future provided as a second parameter indicates whether the stream completed successfully or failed
      f.onComplete {
        case Failure(e) => log.error(s"connection: ${wsActor}",e)
        case Success(_) => {
          clients = clients.filter(_ != wsActor)
          log.debug(s"clients: ${clients}")
        }
      }
    })

    log.debug(s"flow=${flow}, clients: ${clients}")
    flow
  }

  def listen()(implicit mat:Materializer): Flow[Message, Message, Any] = {
    wsFlow()
  }

  def broadcastText(text: String): Unit = {
    log.info(s"broadcasting: '${text}' -> ${clients}")
    for (client <- clients) client ! TextMessage.Strict(text)
  }

  def sendText(actor:ActorRef, text: String): Unit = {
    clients.filter(_.toString == actor).foreach{ a => a ! TextMessage.Strict(text) }
  }
}
