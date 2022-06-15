package io.syspulse.skel

import scala.concurrent.duration._
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl
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

import com.typesafe.scalalogging.Logger

import io.syspulse.skel.config.Configuration
import io.syspulse.skel.service.config.{ConfigRegistry,ConfigRoutes}
import io.syspulse.skel.service.Routeable

import io.syspulse.skel.service.swagger.{Swagger}
import io.syspulse.skel.service.telemetry.{TelemetryRegistry,TelemetryRoutes}
import io.syspulse.skel.service.metrics.{MetricsRegistry,MetricsRoutes}
import io.syspulse.skel.service.info.{InfoRegistry,InfoRoutes}
import io.syspulse.skel.service.health.{HealthRegistry,HealthRoutes}

import fr.davit.akka.http.metrics.core.{HttpMetricsRegistry, HttpMetricsSettings}
import fr.davit.akka.http.metrics.core.HttpMetrics._

import io.syspulse.skel.service.ws.{WebSocketEcho,WsRoutes}
import akka.stream.ActorMaterializer
import scala.concurrent.Future

trait Server {
  val logger = Logger(s"${this}")

  private def startHttpServer(host:String,port:Int, routes: Route)(implicit system: ActorSystem[_]): Unit = {  
    import system.executionContext

    try {
      val http:Future[Http.ServerBinding] =
      Http().newMeteredServerAt(host, port,TelemetryRegistry.prometheusRegistry).bind(routes)
      http.onComplete {
        case Success(binding) =>
          val address = binding.localAddress
          system.log.info(s"Listening: http://${address.getHostString}:${address.getPort}")
          
        case Failure(ex) =>
          system.log.error(s"Failed to bind: ${host}:${port}", ex)
          system.terminate()
      }
    } catch {
      case e:Exception => {
        system.log.error(s"Failed to bind: ${host}:${port}", e)
        system.terminate()
      }
    }
  }

  def jsonEntity(json:String) = HttpEntity(ContentTypes.`application/json`, json)

  def getHandlers():(RejectionHandler,ExceptionHandler) = {
    val rejectionHandler = RejectionHandler.newBuilder()
        .handle { case MissingQueryParamRejection(param) =>
            complete(HttpResponse(BadRequest,   entity = jsonEntity(s"""["error": "missing parameter"]"""")))
        }
        .handle { case AuthorizationFailedRejection =>
          complete(HttpResponse(Forbidden, entity = jsonEntity(s"""["error": "authorization"]"""")))
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
            logger.error(s"Request '$uri' failed:",e)
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
                  systemRoutes:_*
                ) ~
                concat(
                  appRoutes:_*
                ) 
              } 
            }
          }
        }
      }
    routes
  }

  def run(host:String, port:Int, uri:String, configuration:Configuration,
          app:Seq[(Behavior[Command],String,(ActorRef[Command],scaladsl.ActorContext[_])=>Routeable)]
          ): Unit = {
    
    val httpBehavior = Behaviors.setup[Nothing] { context =>
      val telemetryRegistryActor = context.spawn(TelemetryRegistry(), "Actor-Skel-TelemetryRegistry")
      val infoRegistryActor = context.spawn(InfoRegistry(), "Actor-Skel-InfoRegistry")
      val healthRegistryActor = context.spawn(HealthRegistry(), "Actor-Skel-HealthRegistry")
      val configRegistryActor = context.spawn(ConfigRegistry(configuration), "Actor-Skel-ConfigRegistry")
      val metricsRegistryActor = context.spawn(MetricsRegistry(), "Actor-Skel-MetricsRegistry")
      context.watch(telemetryRegistryActor)
      context.watch(infoRegistryActor)
      context.watch(healthRegistryActor)
      context.watch(configRegistryActor)
      context.watch(metricsRegistryActor)

      val (rejectionHandler:RejectionHandler,exceptionHandler:ExceptionHandler) = getHandlers() //(context)

      val appServices:Seq[Routeable] = app.map{ case(behavior,name,routeFun) => {
          val survivingBehavior = Behaviors.supervise[Command] {
            behavior  
          }.onFailure[Exception](SupervisorStrategy.resume)

          val actor:ActorRef[Command] = context.spawn(survivingBehavior, s"Actor-${name}")
          context.watch(actor)
          routeFun(actor,context) 
        }
      }
      val appRoutes:Seq[Route] = appServices.map(r => r.routes)
      val appClasses:Seq[Class[_]] = appServices.map(r => r.getClass())

      val swaggerRoutes = Swagger.routes
      val swaggerUI = path("swagger") { getFromResource("swagger/index.html") } ~ getFromResourceDirectory("swagger")
      val infoRoutes = new InfoRoutes(infoRegistryActor)(context.system)
      val healthRoutes = new HealthRoutes(healthRegistryActor)(context.system)
      val configRoutes = new ConfigRoutes(configRegistryActor)(context.system)
      val telemetryRoutes = new TelemetryRoutes(telemetryRegistryActor)(context.system)
      val metricsRoutes = new MetricsRoutes(metricsRegistryActor)(context.system)

      implicit val ex = context.executionContext
      //val wsRoutes = new WsRoutes("ws",new WebSocketEcho()(ex,ActorMaterializer()(context.system.classicSystem)))(context.system)

      val routes: Route = 
        getRoutes(
          rejectionHandler,exceptionHandler,
          uri,
          Seq(telemetryRoutes.routes, infoRoutes.routes, healthRoutes.routes, configRoutes.routes, metricsRoutes.routes, swaggerRoutes, swaggerUI),
          appRoutes
        )
      
      
      Swagger.withClass(appClasses).withVersion(parseUri(uri)._2.toString).withHost(host,port)
    
      // should not be here...
      startHttpServer(host, port, routes)(context.system)

      Behaviors.empty
    }
    
    val rootBehavior = { Behaviors.supervise[Nothing] { httpBehavior }}.onFailure[Exception](SupervisorStrategy.resume)
    //.onFailure[Exception](SupervisorStrategy.restart.withLimit(maxNrOfRetries = 10, withinTimeRange = 10.seconds))

    implicit val (system) = {
      // ATTENTION: https://doc.akka.io/docs/akka/current/general/configuration.html#configuring-multiple-actorsystem
      val system = ActorSystem[Nothing](rootBehavior, "ActorSystem-HttpServer")
      //val mat = ActorMaterializer()(system.classicSystem)
      //(system,mat)
      (system)
    }
    
  }
}



