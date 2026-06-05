package io.syspulse.skel.service

import akka.util.Timeout

import io.syspulse.skel.config.Configuration

trait CommonRoutes {  
  protected implicit val timeout: Timeout = Timeout.create(
    Configuration.default.getDuration("http.routes.ask-timeout").getOrElse(CommonRoutes.DEF_DURATION)
  ) 
}

object CommonRoutes {
  val DEF_TIMEOUT = 3000L
  val DEF_DURATION = java.time.Duration.ofMillis(DEF_TIMEOUT)
}
