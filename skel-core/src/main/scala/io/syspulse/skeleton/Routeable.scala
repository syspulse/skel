package io.syspulse.skeleton

import akka.http.scaladsl.server.Route

trait Routeable {

  def routes: Route 
}
