package io.syspulse.skel

import akka.http.scaladsl.server.Route

trait Routeable {

  def routes: Route 
}
