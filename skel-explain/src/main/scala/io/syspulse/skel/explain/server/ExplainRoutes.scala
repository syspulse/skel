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

  def getRule(oid: String, rid: String): Future[Try[ExplainRule]] =
    registry.ask(GetRule(oid, rid, _))

  def getRules(oid: Option[String]): Future[Try[ExplainRules]] =
    registry.ask(GetRules(oid, _))

  def createRule(oid: String, rid: String, req: ExplainRuleCreateReq): Future[Try[ExplainRuleRes]] =
    registry.ask(CreateRule(oid, rid, req, _))

  def updateRule(oid: String, rid: String, req: ExplainRuleUpdateReq): Future[Try[ExplainRuleRes]] =
    registry.ask(UpdateRule(oid, rid, req, _))

  def deleteRule(oid: String, rid: String): Future[Try[ExplainRuleRes]] =
    registry.ask(DeleteRule(oid, rid, _))

  def explain(rid: String, req: ExplainReq): Future[Try[ExplainRes]] =
    registry.ask(Explain(rid, req, _))

  // --- Rule routes ---

  def routeGetRule(oid: String, rid: String) = get {
    complete(getRule(oid, rid))
  }

  def routeCreateRule(oid: String, rid: String) = post {
    entity(as[ExplainRuleCreateReq]) { req =>
      complete(createRule(oid, rid, req))
    }
  }

  def routeUpdateRule(oid: String, rid: String) = put {
    entity(as[ExplainRuleUpdateReq]) { req =>
      complete(updateRule(oid, rid, req))
    }
  }

  def routeDeleteRule(oid: String, rid: String) = delete {
    complete(deleteRule(oid, rid))
  }

  // --- Explain route ---

  def routeExplain(rid: String) = post {
    entity(as[ExplainReq]) { req =>
      complete(explain(rid, req))
    }
  }

  val corsAllow = CorsSettings(system.classicSystem)
    .withAllowCredentials(true)
    .withAllowedMethods(Seq(HttpMethods.OPTIONS, HttpMethods.GET, HttpMethods.POST, HttpMethods.PUT, HttpMethods.DELETE, HttpMethods.HEAD))

  override def routes: Route = cors(corsAllow) {
    concat(
      // Default rules: /rule/{rid}
      pathPrefix("rule") {
        path(Segment) { rid =>
          pathEndOrSingleSlash {
            authenticate()(authn => {
              authorize(Permissions.isAdmin(authn) || Permissions.isService(authn)) {
                routeGetRule(ExplainRule.DEF_OID, rid) ~
                routeCreateRule(ExplainRule.DEF_OID, rid) ~
                routeUpdateRule(ExplainRule.DEF_OID, rid) ~
                routeDeleteRule(ExplainRule.DEF_OID, rid)
              }
            })
          }
        }
      },
      // OID rules: /{oid}/{rid}
      path(Segment / Segment) { (oid, rid) =>
        pathEndOrSingleSlash {
          authenticate()(authn => {
            authorize(
              Permissions.isAdmin(authn) ||
              Permissions.isService(authn) ||
              ExtAuth.getOwner(authn) == Some(oid)
            ) {
              routeGetRule(oid, rid) ~
              routeCreateRule(oid, rid) ~
              routeUpdateRule(oid, rid) ~
              routeDeleteRule(oid, rid)
            }
          })
        }
      },
      // Explain: POST /{rid}
      path(Segment) { rid =>
        pathEndOrSingleSlash {
          routeExplain(rid)
        }
      }
    )
  }
}
