package io.syspulse.skel.service

import scala.concurrent.Future
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout

import io.syspulse.skel.config.Configuration
import io.syspulse.skel.service.telemetry.TelemetryRegistry._

// class CommonRoutes(implicit val context: ActorContext[_]) {
//   protected implicit val timeout = Timeout.create(
//     Configuration.default.getDuration("http.routes.ask-timeout").getOrElse(java.time.Duration.ofMillis(3000L))
//   )
// }

trait CommonRoutes {
  protected implicit val timeout = Timeout.create(
    Configuration.default.getDuration("http.routes.ask-timeout").getOrElse(java.time.Duration.ofMillis(3000L))
  )
}

