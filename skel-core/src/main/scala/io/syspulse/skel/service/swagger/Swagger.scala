package io.syspulse.skel.service.swagger

import com.typesafe.scalalogging.Logger

import com.github.swagger.akka.SwaggerHttpService
import com.github.swagger.akka.model.Info

import io.swagger.v3.oas.models.ExternalDocumentation

import io.syspulse.skel.service.telemetry.{TelemetryRoutes}
import io.syspulse.skel.service.info.{InfoRoutes}
import io.syspulse.skel.service.health.HealthRoutes
import io.syspulse.skel.service.config.ConfigRoutes
import io.syspulse.skel.service.metrics.MetricsRoutes


trait SwaggerLike extends SwaggerHttpService { 
  private val log = Logger(s"${this}")

  @volatile
  var hostPort:String = ""
  @volatile
  var information = Info(version = "1.0")
  @volatile
  var classes:Set[Class[_]] = Set(
    classOf[TelemetryRoutes],
    classOf[InfoRoutes],
    classOf[HealthRoutes],
    classOf[ConfigRoutes],
    classOf[MetricsRoutes]
  )
  @volatile
  var uriPath:String = ""

  override def apiClasses: Set[Class[_]] = {
    log.info(s"Documented Classes: ${classes}")
    classes
  }
  override def info = information
  override def host = hostPort

  override def basePath = uriPath

  def withClass(cc:Seq[Class[_]]):SwaggerLike = { classes = classes ++ cc; this }
  def withVersion(v:String):SwaggerLike = { information = Info(version = v); this }
  def withHost(h:String,p:Int):SwaggerLike = { hostPort = s"${h}:${p}"; this }
  def withUri(u:String):SwaggerLike = { uriPath = u; this }
}

object Swagger extends SwaggerLike {
  
  override val apiDocsPath: String = "doc"
  //override def host = ""
  
  override val externalDocs: Option[ExternalDocumentation] = Some(new ExternalDocumentation().description("Core Docs").url("http://syspulse.io/docs"))
  //override val securitySchemeDefinitions = Map("basicAuth" -> new BasicAuthDefinition())
}