package io.syspulse.skeleton

import com.github.swagger.akka.SwaggerHttpService
import com.github.swagger.akka.model.Info

import io.swagger.v3.oas.models.ExternalDocumentation

trait SwaggerLike extends SwaggerHttpService { 
  
  var uri:String = ""
  var information = Info(version = "1.0")
  var classes:Set[Class[_]] = Set(
    classOf[TelemetryRoutes],
    classOf[InfoRoutes]
  )

  override def apiClasses: Set[Class[_]] = classes
  override def info = information
  override def host = uri

  def withClass(cc:Seq[Class[_]]):SwaggerLike = { classes = classes ++ cc; this }
  def withVersion(v:String):SwaggerLike = { information = Info(version = v); this }
  def withHost(h:String,p:Int):SwaggerLike = { uri = s"${h}:${p}"; this }
}

object Swagger extends SwaggerLike {
  
  override val apiDocsPath: String = "docs"
  //override def host = ""
  
  override val externalDocs: Option[ExternalDocumentation] = Some(new ExternalDocumentation().description("Core Docs").url("http://acme.com/docs"))
  //override val securitySchemeDefinitions = Map("basicAuth" -> new BasicAuthDefinition())
}