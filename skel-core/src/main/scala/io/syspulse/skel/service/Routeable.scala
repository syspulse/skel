package io.syspulse.skel.service

import akka.http.scaladsl.server.Route

trait Routeable {

  def routes: Route 
}
