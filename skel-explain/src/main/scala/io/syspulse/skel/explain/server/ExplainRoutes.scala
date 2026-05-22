package io.syspulse.skel.explain.server

import com.typesafe.scalalogging.Logger
import scala.util.{Try, Success, Failure}

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route

import scala.concurrent.Future
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.StatusCodes._

import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings

import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.{Content, Schema}
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.{Operation, Parameter}
import io.swagger.v3.oas.annotations.parameters.RequestBody
import jakarta.ws.rs.{Consumes, POST, PUT, GET, DELETE, Path, Produces}
import jakarta.ws.rs.core.MediaType

import io.syspulse.skel.service.Routeable
import io.syspulse.skel.service.CommonRoutes
import io.syspulse.skel.Command
import io.syspulse.skel.auth._
import io.syspulse.skel.auth.permissions.Permissions
import io.syspulse.skel.auth.RouteAuthorizers
import io.syspulse.skel.auth.ext.{ExtAuth, ExtRbacStrict, ExtRbacUser}

import io.syspulse.skel.explain._
import io.syspulse.skel.explain.store.ExplainRegistry
import io.syspulse.skel.explain.store.ExplainRegistry._

@Path("/")
class ExplainRoutes(registry: ActorRef[Command])(implicit context: ActorContext[_], config: Config)
    extends CommonRoutes with Routeable with RouteAuthorizers {

  implicit val system: ActorSystem[_] = context.system

  implicit val permissions: Permissions = config.permissions match {
    case "strict" => new ExtRbacStrict(config.adminRole, config.serviceRole, config.rolesAttr)
    case "user"   => new ExtRbacUser(config.adminRole, config.serviceRole, config.rolesAttr)
    case _        => Permissions(config.permissions)
  }

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import ExplainJson._

  private def oidOpt(oid: String): Option[String] =
    Option(oid).filter(_.nonEmpty)

  private def oidStr(oid: Option[String]): String =
    oid.getOrElse("")

  private def authOid(authn: Authenticated): String =
    ExtAuth.getOwner(authn).getOrElse("")

  private def canAccessOid(authn: Authenticated, oid: String): Boolean =
    Permissions.isAdmin(authn) ||
    Permissions.isService(authn) ||
    authOid(authn) == oid

  def getRule(oid: String, rid: String): Future[Try[Explain]] =
    registry.ask(GetRule(oidOpt(oid), rid, _))

  def getRules(oid: Option[String]): Future[Try[Explains]] =
    registry.ask(GetRules(oid, _))

  def createRule(oid: String, rid: String, req: ExplainCreateReq): Future[Try[ExplaineActionRes]] =
    registry.ask(CreateRule(oid, rid, req, _))

  def updateRule(oid: String, rid: String, req: ExplainUpdateReq): Future[Try[ExplaineActionRes]] =
    registry.ask(UpdateRule(oid, rid, req, _))

  def deleteRule(oid: String, rid: String): Future[Try[ExplaineActionRes]] =
    registry.ask(DeleteRule(oid, rid, _))

  def deleteRules(oid: String): Future[Try[Explains]] =
    registry.ask(DeleteRules(oid, _))

  def explain(req: ExplainReq, style: String): Future[Try[ExplainRes]] =
    registry.ask(RunExplain(req, style, _))

  // --- Rule routes ---

  @GET @Path("/{rid}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("explain"), summary = "Return Explain Rule",
    parameters = Array(
      new Parameter(name = "rid", in = ParameterIn.PATH, description = "Rule ID")
    ),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Explain Rule", content = Array(new Content(schema = new Schema(implementation = classOf[Explain])))))
  )
  def routeGetRule(oid: String, rid: String) = get {
    complete(getRule(oid, rid))
  }

  @POST @Path("/{rid}") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("explain"), summary = "Create Explain Rule",
    parameters = Array(
      new Parameter(name = "rid", in = ParameterIn.PATH, description = "Rule ID")
    ),
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[ExplainCreateReq])))),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Create Explain Rule", content = Array(new Content(schema = new Schema(implementation = classOf[ExplaineActionRes])))))
  )
  def routeCreateRule(oid: String, rid: String) = post {
    entity(as[ExplainCreateReq]) { req =>
      complete(createRule(oid, rid, req))
    }
  }

  @PUT @Path("/{rid}") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("explain"), summary = "Update Explain Rule",
    parameters = Array(
      new Parameter(name = "rid", in = ParameterIn.PATH, description = "Rule ID")
    ),
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[ExplainUpdateReq])))),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Update Explain Rule", content = Array(new Content(schema = new Schema(implementation = classOf[ExplaineActionRes])))))
  )
  def routeUpdateRule(oid: String, rid: String) = put {
    entity(as[ExplainUpdateReq]) { req =>
      complete(updateRule(oid, rid, req))
    }
  }

  @DELETE @Path("/{rid}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("explain"), summary = "Delete Explain Rule",
    parameters = Array(
      new Parameter(name = "rid", in = ParameterIn.PATH, description = "Rule ID")
    ),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Delete Explain Rule", content = Array(new Content(schema = new Schema(implementation = classOf[ExplaineActionRes])))))
  )
  def routeDeleteRule(oid: String, rid: String) = delete {
    complete(deleteRule(oid, rid))
  }

  @DELETE @Path("/") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("explain"), summary = "Delete all Explain Rules for an oid",
    parameters = Array(
      new Parameter(name = "oid", in = ParameterIn.QUERY, description = "Owner ID (omit for default oid='')")
    ),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Deleted rules", content = Array(new Content(schema = new Schema(implementation = classOf[Explains])))))
  )
  def routeDeleteRules(oid: String) = delete {
    complete(deleteRules(oid))
  }

  // --- Explain route ---

  @GET @Path("/{rid}/explain") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("explain"), summary = "Run Explain Rule",
    parameters = Array(
      new Parameter(name = "rid", in = ParameterIn.PATH, description = "Rule ID"),
      new Parameter(name = "style", in = ParameterIn.QUERY, description = "Explanation style: short, narrative, detailed (default: '')")
    ),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Explanation generated by rule scripts", content = Array(new Content(schema = new Schema(implementation = classOf[ExplainRes])))))
  )
  def routeExplain(rid: Option[String]) = get {
    parameters("style".?, "oid".?) { (styleOpt, oidQuery) =>
      val style = styleOpt.getOrElse("")
      (entity(as[ExplainReq]) | provide(ExplainReq())) { req =>
        complete(explain(req.copy(oid = oidQuery.orElse(req.oid), rid = rid.orElse(req.rid)), style))
      }
    }
  }

  val corsAllow = CorsSettings(system.classicSystem)
    .withAllowCredentials(true)
    .withAllowedMethods(Seq(HttpMethods.OPTIONS, HttpMethods.GET, HttpMethods.POST, HttpMethods.PUT, HttpMethods.DELETE, HttpMethods.HEAD))

  override def routes: Route = cors(corsAllow) {
    concat(
      // List rules: GET /  or GET /?oid=...
      // Delete all for oid: DELETE /?oid=...  (omit oid → default oid="")
      pathEndOrSingleSlash {
        get {
          entity(as[ExplainReq]) { req =>
            parameters("style".?, "oid".?) { (styleOpt, oidQuery) =>
              complete(explain(req.copy(oid = oidQuery.orElse(req.oid)), styleOpt.getOrElse("")))
            }
          } ~
          parameters("oid".?) { oidQuery =>
            authenticate()(authn => {
              authorize(Permissions.isAdmin(authn) || Permissions.isService(authn)) {
                complete(getRules(oidQuery))
              }
            })
          }
        } ~
        delete {
          parameters("oid".?) { oidQuery =>
            authenticate()(authn => {
              val oid = oidQuery.getOrElse("")
              authorize(canAccessOid(authn, oid)) {
                routeDeleteRules(oid)
              }
            })
          }
        } ~
        post {
          parameters("oid".?) { oidQuery =>
            authenticate()(authn => {
              entity(as[ExplainCreateReq]) { req =>
                req.rid match {
                  case Some(rid) =>
                    val oid = oidQuery.orElse(req.oid).getOrElse(authOid(authn))
                    authorize(canAccessOid(authn, oid)) {
                      complete(createRule(oid, rid, req))
                    }
                  case None =>
                    complete(StatusCodes.BadRequest -> "missing rid")
                }
              }
            })
          }
        } ~
        put {
          parameters("oid".?) { oidQuery =>
            authenticate()(authn => {
              entity(as[ExplainUpdateReq]) { req =>
                req.rid match {
                  case Some(rid) =>
                    val oid = oidQuery.orElse(req.oid).getOrElse(authOid(authn))
                    authorize(canAccessOid(authn, oid)) {
                      complete(updateRule(oid, rid, req))
                    }
                  case None =>
                    complete(StatusCodes.BadRequest -> "missing rid")
                }
              }
            })
          }
        }
      },
      // Per-rule routes: /{rid} and /{rid}/explain
      pathPrefix(Segment) { rid =>
        concat(
          // Explain: GET /{rid}/explain?style=...
          path("explain") {
            routeExplain(Some(rid))
          },
          // CRUD: GET/POST/PUT/DELETE /{rid}  (oid derived from JWT)
          pathEndOrSingleSlash {
            parameters("oid".?) { oidQuery =>
              authenticate()(authn => {
                get {
                  val oid = oidQuery.getOrElse(authOid(authn))
                  authorize(canAccessOid(authn, oid)) {
                    complete(getRule(oid, rid))
                  }
                } ~
                post {
                  entity(as[ExplainCreateReq]) { req =>
                    val oid = oidQuery.orElse(req.oid).getOrElse(authOid(authn))
                    authorize(canAccessOid(authn, oid)) {
                      complete(createRule(oid, rid, req))
                    }
                  }
                } ~
                put {
                  entity(as[ExplainUpdateReq]) { req =>
                    val oid = oidQuery.orElse(req.oid).getOrElse(authOid(authn))
                    authorize(canAccessOid(authn, oid)) {
                      complete(updateRule(oid, rid, req))
                    }
                  }
                } ~
                delete {
                  val oid = oidQuery.getOrElse(authOid(authn))
                  authorize(canAccessOid(authn, oid)) {
                    complete(deleteRule(oid, rid))
                  }
                }
              })
            }
          }
        )
      }
    )
  }
}
