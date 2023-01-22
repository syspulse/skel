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

import io.syspulse.skel.service.swagger.{Swagger,SwaggerLike}
import io.syspulse.skel.service.telemetry.{TelemetryRegistry,TelemetryRoutes}
import io.syspulse.skel.service.metrics.{MetricsRegistry,MetricsRoutes}
import io.syspulse.skel.service.info.{InfoRegistry,InfoRoutes}
import io.syspulse.skel.service.health.{HealthRegistry,HealthRoutes}

import fr.davit.akka.http.metrics.core.{HttpMetricsRegistry, HttpMetricsSettings}
import fr.davit.akka.http.metrics.core.HttpMetrics._

import io.syspulse.skel.service.ws.{WebSocketEcho,WsRoutes}
import akka.stream.ActorMaterializer
import scala.concurrent.Future
import akka.http.scaladsl.server.directives.ContentTypeResolver
import akka.actor
import akka.http.scaladsl.server.directives.FileAndResourceDirectives
import akka.http.scaladsl.server.directives.RangeDirectives
import akka.http.scaladsl.server.directives.CodingDirectives
import akka.stream.scaladsl.StreamConverters
import akka.util.ByteString

trait Server {
  val log = Logger(s"${this}")
  
  val shutdownTimeout = 1.seconds
  
  private def startHttpServer(host:String,port:Int, routes: Route)(implicit system: ActorSystem[_]): Unit = {  
    import system.executionContext

    try {
      val http:Future[Http.ServerBinding] =
      Http()
        .newMeteredServerAt(host, port,TelemetryRegistry.prometheusRegistry)
        .bind(routes)
        .map(_.addToCoordinatedShutdown(hardTerminationDeadline = shutdownTimeout))
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
            log.error(s"Request '$uri' failed:",e)
            complete(HttpResponse(InternalServerError, entity = jsonEntity(s"""["error": "${e}"]""")))
          }
        // case e: Exception => complete(HttpResponse(InternalServerError))
        case e: Exception => {
          // nice forwarding errors to clients
          complete(HttpResponse(InternalServerError, entity = jsonEntity(s"""["error": "${e}"]""")))
        }
      }
    (rejectionHandler,exceptionHandler)
  }

  def parseUri(uri:String) = {
    val (apiUri,apiVersion,serviceUri) = uri.split("/").filter(!_.isEmpty()) match {
      case Array(p,v,s) => (p,v,s)
      case Array(p,s) => (p,"",s)
      case Array(s) => ("","",s)
      case Array() => ("","","")
      case Array(p,v,s,_*) => (p,v,s)
    }  
    (apiUri,apiVersion,serviceUri)  
  }

  def parseUriPath(uri:String) = {
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
    val (apiUri,apiVersion,serviceUri) = parseUriPath(uri)
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

  protected def postInit(context:ActorContext[_],routes:Route) = {}


  // =================================================================================================================================
  // Load HTTP resource with replacement (borrowed from akka)
  val withRangeSupportAndPrecompressedMediaTypeSupport = RangeDirectives.withRangeSupport & CodingDirectives.withPrecompressedMediaTypeSupport
  def getFromResource2(resourceName: String, serviceName:String = "service", classLoader: ClassLoader = classOf[actor.ActorSystem].getClassLoader)(implicit resolver: ContentTypeResolver): Route = {
    val contentType: ContentType = resolver(resourceName)
    log.info(s"documentation: resource=${resourceName}: serviceName=${serviceName}")
    if (!resourceName.endsWith("/"))
      get {
        Option(classLoader.getResource(resourceName)) flatMap FileAndResourceDirectives.ResourceFile.apply match {
          case Some(FileAndResourceDirectives.ResourceFile(url, length, lastModified)) =>
            if (length > 0) {
                withRangeSupportAndPrecompressedMediaTypeSupport {
                  // val stream = StreamConverters.fromInputStream(() => url.openStream(),chunkSize = 8192).map( s=> {
                  //   val body = ByteString(s.utf8String.replace("{service}",serviceName))
                  //   body
                  // })
                  // complete(HttpEntity.Default(contentType, length, stream))
                  val body0 = new String(url.openStream().readAllBytes())
                  val body1 = ByteString(body0.replace("{service}",serviceName))
                  complete(HttpEntity(contentType,body1))
                }
              } else complete(HttpEntity.Empty)
            
          case _ => reject // not found or directory
        }
      }
    else reject
  }
  // =================================================================================================================================

  def run(host:String, port:Int, uri:String, configuration:Configuration,
          app:Seq[(Behavior[Command],String,(ActorRef[Command],ActorContext[_])=>Routeable)],
          bootstrapActorSystem: Option[ActorSystem[Nothing]] = None): Behavior[Nothing] = {
    
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
      val appRoutes:Seq[Route] = appServices.map(r => r.buildRoutes())
      val appClasses:Seq[Class[_]] = appServices.map(r => r.getClass())
      
      val swagger = Swagger.withClass(appClasses).withVersion(parseUri(uri)._2).withHost(host,port).withUri(uri)

      val swaggerRoutes = swagger.routes
      val swaggerUI = path("doc") { getFromResource2("doc/index.html",parseUri(uri)._3) } ~ getFromResourceDirectory("doc")
      //val swaggerUI = path("swagger") { getFromResource2("swagger/index.html",parseUri(uri)._3) } ~ getFromResourceDirectory("swagger")
      val infoRoutes = new InfoRoutes(infoRegistryActor)(context)
      val healthRoutes = new HealthRoutes(healthRegistryActor)(context)
      val configRoutes = new ConfigRoutes(configRegistryActor)(context)
      val telemetryRoutes = new TelemetryRoutes(telemetryRegistryActor)(context)
      val metricsRoutes = new MetricsRoutes(metricsRegistryActor)(context)

      implicit val ex = context.executionContext
      //val wsRoutes = new WsRoutes("ws",new WebSocketEcho()(ex,ActorMaterializer()(context.system.classicSystem)))(context.system)

      val routes: Route = 
        getRoutes(
          rejectionHandler,exceptionHandler,
          uri,
          Seq(telemetryRoutes.routes, infoRoutes.routes, healthRoutes.routes, configRoutes.routes, metricsRoutes.routes, swaggerRoutes, swaggerUI),
          appRoutes
        )
            
      postInit(context,routes)
    
      // should not be here...
      startHttpServer(host, port, routes)(context.system)

      Behaviors.empty
    }
    
    val rootBehavior = { Behaviors.supervise[Nothing] { httpBehavior }}.onFailure[Exception](SupervisorStrategy.resume)
    //.onFailure[Exception](SupervisorStrategy.restart.withLimit(maxNrOfRetries = 10, withinTimeRange = 10.seconds))

    implicit val (system) = {
      // ATTENTION: https://doc.akka.io/docs/akka/current/general/configuration.html#configuring-multiple-actorsystem
      val system = 
        if(bootstrapActorSystem.isDefined) 
          bootstrapActorSystem.get.systemActorOf[Nothing](rootBehavior, "ActorSystem-HttpServer")
        else
          ActorSystem[Nothing](rootBehavior, "ActorSystem-HttpServer")

      //val mat = ActorMaterializer()(system.classicSystem)
      //(system,mat)
      (system)
    }
    
    rootBehavior
  }
}



