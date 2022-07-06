package io.syspulse.skel.service

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives.{rawPathPrefix,pathPrefix}

trait Routeable {
  // this is needed for additional composability (mutliple services within the same Service)
  // really messy, should be refactored to more sane approach
  var uriSuffix:String = ""

  protected def routes: Route

  def buildRoutes():Route = {
    if(uriSuffix.isEmpty())
      routes
    else {
      pathPrefix(uriSuffix) { routes }
    }
  }

  def withSuffix(uri:String):Routeable = {
    uriSuffix = (if(uriSuffix.isEmpty || uriSuffix.endsWith("/")) uriSuffix else uriSuffix + "/") + uri
    this
  }
}
