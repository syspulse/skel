package io.syspulse.skeleton

import akka.actor.typed.ActorSystem
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.Behavior
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

  def run(host:String, port:Int,
          app:Seq[(Behavior[Command],String,(ActorRef[Command],ActorSystem[_])=>Routeable)]): Unit = {
    
    def jsonEntity(json:String) = HttpEntity(ContentTypes.`application/json`, json)


    val rootBehavior = Behaviors.setup[Nothing] { context =>
      val telemetryRegistryActor = context.spawn(TelemetryRegistry(), "Actor-TelemetryRegistry")
      val infoRegistryActor = context.spawn(InfoRegistry(), "Actor-InfoRegistry")
      context.watch(telemetryRegistryActor)
      context.watch(infoRegistryActor)


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
            context.system.log.error(s"Request '$uri' failed:",e)
            complete(HttpResponse(InternalServerError, entity = jsonEntity(s"""["error": "${e}"]""")))
          }
      }

      // val userRoutes = new UserRoutes(userRegistryActor)(context.system)
      // val otpRoutes = new OtpRoutes(otpRegistryActor)(context.system)
      val appServices:Seq[Routeable] = app.map{ case(behavior,name,routeFun) => {
          val actor:ActorRef[Command] = context.spawn(behavior, s"Actor-${name}")
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

      val routes: Route =
      handleRejections(rejectionHandler) {
        handleExceptions(exceptionHandler) {
          pathPrefix("api") {
            pathPrefix("v1") {
              concat(appRoutes:_*) ~
              concat(
                telemetryRoutes.routes,
                infoRoutes.routes,
                swaggerRoutes,
                swaggerUI
              )
            }
          }
        }
      }
          
      Swagger.withClass(appClasses).withVersion("v1").withHost(host,port)
      startHttpServer(host, port, routes)(context.system)
      Behaviors.empty
    }

    val system = ActorSystem[Nothing](rootBehavior, "ActorSystem-HttpServer")
  }
}

