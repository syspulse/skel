package io.syspulse.skel.twit.scrap

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Success, Failure, Random}

import akka.actor.typed.ActorRef
import akka.actor.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.stream.scaladsl.{Sink, Source, RestartSource,RestartFlow, RestartSink}
import akka.stream.ActorMaterializer
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Flow
import akka.stream.RestartSettings
import akka.NotUsed


import com.typesafe.scalalogging.Logger

import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import io.syspulse.skel.twit.TwitData
import akka.stream.scaladsl.SourceQueueWithComplete

class ScrapFlow(scrapUsers:Seq[String],scrapDir:String,queueLimit:Long=10) {

  val log = Logger(s"${this}")

  val zscrap = new ZImageScrap(scrapDir)

  implicit val system: ActorSystem = ActorSystem()
  implicit lazy val materializer: ActorMaterializer = ActorMaterializer()

  val retrySettings = RestartSettings(
    minBackoff = 3.seconds,
    maxBackoff = 5.seconds,
    randomFactor = 0.2 // adds 20% "noise" to vary the intervals slightly
  )

  def scrapFlow = Flow[TwitData].map(t => {
    io.syspulse.skel.twit.App.metricScrapCount.inc()
    zscrap.scrap(t.txt.trim,true)
  })

  val source = Source.queue[TwitData](queueLimit.toInt, OverflowStrategy.backpressure)

  val restartableScrapFlow = RestartFlow.withBackoff(retrySettings) { () => 
    scrapFlow
  }

  val stream = 
    source
    .filter(t => scrapUsers.contains(t.user))
    //.via( scrapFlow )
    .via(restartableScrapFlow)
    .to(Sink.foreach(r => log.info(s"scrapped: ${r}")))
    .run()
  
}
