package io.syspulse.skel

import akka.actor.typed.ActorSystem
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.Behavior
import akka.actor.typed.SupervisorStrategy
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._

import scala.util.Failure
import scala.util.Success


import akka.http.scaladsl.model._
import akka.http.scaladsl.server.RejectionHandler
import akka.http.scaladsl.model.StatusCodes._

import akka.http.scaladsl.server.RejectionHandler
import akka.http.scaladsl.model.{HttpEntity, StatusCodes, _}
import akka.http.scaladsl.server._
import StatusCodes._
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route


import io.syspulse.skel.service.swagger.{Swagger}
import io.syspulse.skel.service.telemetry.{TelemetryRegistry,TelemetryRoutes}
import io.syspulse.skel.service.info.{InfoRegistry,InfoRoutes}
import io.syspulse.skel.service.health.{HealthRegistry,HealthRoutes}
import io.syspulse.skel.service.Routeable
import scala.concurrent.duration._
import akka.actor.ActorContext
import akka.actor.typed.scaladsl


trait Server {

  private def startHttpServer(host:String,port:Int, routes: Route)(implicit system: ActorSystem[_]): Unit = {  
    import system.executionContext

    val http = Http().newServerAt(host, port).bind(routes)

    http.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info(s"Listening: http://${address.getHostString}:${address.getPort}")
        
      case Failure(ex) =>
        system.log.error(s"Failed to bind: ${host}:${port}", ex)
        system.terminate()
    }
  }

  def jsonEntity(json:String) = HttpEntity(ContentTypes.`application/json`, json)

  //def getHandlers(context:akka.actor.typed.scaladsl.ActorContext[_]):(RejectionHandler,ExceptionHandler) = {
  def getHandlers():(RejectionHandler,ExceptionHandler) = {
    val rejectionHandler = RejectionHandler.newBuilder()
        .handle { case MissingQueryParamRejection(param) =>
            complete(HttpResponse(BadRequest,   entity = jsonEntity(s"""["error": "missing parameter"]"""")))
        }
        .handle { case AuthorizationFailedRejection =>
          complete(HttpResponse(BadRequest, entity = jsonEntity(s"""["error": "authorization"]"""")))
        }
        .handleAll[MethodRejection] { methodRejections =>
          val names = methodRejections.map(_.supported.name)
          complete(HttpResponse(MethodNotAllowed, entity = jsonEntity(s"""["error": "rejected"]"""")))
        }
        .handleNotFound { extractUnmatchedPath { p =>
          complete(HttpResponse(NotFound, entity = jsonEntity(s"""["error": "not found: '${p}']"""")))
        }}
        .result()
    
    val exceptionHandler: ExceptionHandler =
      ExceptionHandler {
        case e: java.lang.IllegalArgumentException =>
          extractUri { uri =>
            //context.system.log.error(s"Request '$uri' failed:",e)
            complete(HttpResponse(InternalServerError, entity = jsonEntity(s"""["error": "${e}"]""")))
          }
        // case e: Exception => complete(HttpResponse(InternalServerError))
      }
    (rejectionHandler,exceptionHandler)
  }

  def parseUri(uri:String) = {
    val (apiUri,apiVersion,serviceUri) = uri.split("/").filter(!_.isEmpty()) match {
      case Array(p,v,s) => (Slash ~ p,Slash ~ v,Slash ~ s)
      case Array(p,s) => (Slash ~ p,PathMatcher(""),Slash ~ s)
      case Array(s) => (PathMatcher(""),PathMatcher(""),Slash ~ s)
      case Array() => (PathMatcher(""),PathMatcher(""),PathMatcher(""))
      case Array(p,v,s,_*) => (Slash ~ p,Slash ~ v,Slash ~ s)
    }  
    (apiUri,apiVersion,serviceUri)
  }

  def getRoutes(rejectionHandler:RejectionHandler,exceptionHandler:ExceptionHandler,
                uri:String,
                systemRoutes:Seq[Route],
                appRoutes:Seq[Route]) = {
    val (apiUri,apiVersion,serviceUri) = parseUri(uri)
    val routes: Route =
      handleRejections(rejectionHandler) {
        handleExceptions(exceptionHandler) {
          rawPathPrefix(apiUri) {
            rawPathPrefix(apiVersion) {
              rawPathPrefix(serviceUri) {
                concat(
                  // telemetryRoutes.routes,
                  // infoRoutes.routes,
                  // healthRoutes.routes,
                  // swaggerRoutes,
                  // swaggerUI
                  systemRoutes:_*
                ) ~
                concat(appRoutes:_*) 
              } 
            }
          }
        }
      }
    routes
  }

  def run(host:String, port:Int, uri:String,
          app:Seq[(Behavior[Command],String,(ActorRef[Command],ActorSystem[_])=>Routeable)]
          ): Unit = {
    
    val httpBehavior = Behaviors.setup[Nothing] { context =>
      val telemetryRegistryActor = context.spawn(TelemetryRegistry(), "Actor-TelemetryRegistry")
      val infoRegistryActor = context.spawn(InfoRegistry(), "Actor-InfoRegistry")
      val healthRegistryActor = context.spawn(HealthRegistry(), "Actor-HealthRegistry")
      context.watch(telemetryRegistryActor)
      context.watch(infoRegistryActor)
      context.watch(healthRegistryActor)

      val (rejectionHandler:RejectionHandler,exceptionHandler:ExceptionHandler) = getHandlers() //(context)

      val appServices:Seq[Routeable] = app.map{ case(behavior,name,routeFun) => {
          val survivingBehavior = Behaviors.supervise[Command] {
            behavior  
          }.onFailure[Exception](SupervisorStrategy.resume)

          val actor:ActorRef[Command] = context.spawn(survivingBehavior, s"Actor-${name}")
          context.watch(actor)
          routeFun(actor,context.system) 
        }
      }
      val appRoutes:Seq[Route] = appServices.map(r => r.routes)
      val appClasses:Seq[Class[_]] = appServices.map(r => r.getClass())

      val swaggerRoutes = Swagger.routes
      val swaggerUI = path("swagger") { getFromResource("swagger/index.html") } ~ getFromResourceDirectory("swagger")
      val telemetryRoutes = new TelemetryRoutes(telemetryRegistryActor)(context.system)
      val infoRoutes = new InfoRoutes(infoRegistryActor)(context.system)
      val healthRoutes = new HealthRoutes(healthRegistryActor)(context.system)

      val routes: Route = 
        getRoutes(
          rejectionHandler,exceptionHandler,
          uri,
          Seq(telemetryRoutes.routes,infoRoutes.routes,healthRoutes.routes,swaggerRoutes,swaggerUI),
          appRoutes
        )

      // handleRejections(rejectionHandler) {
      //   handleExceptions(exceptionHandler) {
      //     rawPathPrefix(apiUri) {
      //       rawPathPrefix(apiVersion) {
      //         rawPathPrefix(serviceUri) {
      //           concat(
      //             telemetryRoutes.routes,
      //             infoRoutes.routes,
      //             healthRoutes.routes,
      //             swaggerRoutes,
      //             swaggerUI
      //           ) ~
      //           concat(appRoutes:_*) 
      //         } 
      //       }
      //     }
      //   }
      // }
          
      Swagger.withClass(appClasses).withVersion(parseUri(uri)._2.toString).withHost(host,port)
    
      // should not be here...
      startHttpServer(host, port, routes)(context.system)

      Behaviors.empty
    }
    
    val rootBehavior = { Behaviors.supervise[Nothing] { httpBehavior }}.onFailure[Exception](SupervisorStrategy.resume)
    //.onFailure[Exception](SupervisorStrategy.restart.withLimit(maxNrOfRetries = 10, withinTimeRange = 10.seconds))

    val system = ActorSystem[Nothing](rootBehavior, "ActorSystem-HttpServer")
    
  }
}

