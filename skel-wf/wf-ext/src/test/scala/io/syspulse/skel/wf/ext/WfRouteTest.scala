package io.syspulse.skel.wf.ext

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest

import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}

import io.syspulse.skel.auth.jwt.AuthJwt

/**
 * Shared test setup for specs that build WorkflowRoutes:
 *   - provides the implicit `Config` required by WorkflowRoutes (permissions=user, like production);
 *   - issues an admin JWT and `~~>` so requests are authenticated without GOD mode
 *     (GOD is process-global / cached on Permissions.isGod and conflicts with oid auth tests).
 *
 * Prefer `req ~~> routes.routes` over bare `req ~> routes.routes`.
 */
trait WfRouteTest { self: ScalatestRouteTest =>
  // Do not set GOD here — WorkflowConfigAuthRoutesSpec needs real JWT oid filtering.
  implicit val config: Config = Config(
    ownerAttr = "oid",
    rolesAttr = "groups[].",
    serviceRole = "extractor-service",
    adminRole = "extractor-admin",
    permissions = "user"
  )

  private val jwtSecret = "secret1"
  private val jwtAlgo = JwtAlgorithm.HS256
  AuthJwt(s"${jwtAlgo}://${jwtSecret}")

  /** Admin JWT used by functional (non-auth) route specs. */
  val adminJwt: String = {
    val claims = JwtClaim(
      issuer = Some("test"),
      subject = Some("admin"),
      issuedAt = Some(System.currentTimeMillis() / 1000),
      expiration = Some((System.currentTimeMillis() / 1000) + 3600),
      content = s"""{"oid":"","groups":["${config.adminRole}"],"tenantId":""}"""
    )
    Jwt.encode(claims, jwtSecret, jwtAlgo)
  }

  implicit class AuthedHttpRequest(req: HttpRequest) {
    /** Like `~>` but attaches the admin Bearer token first. */
    def ~~>(route: Route) =
      req ~> addHeader(Authorization(OAuth2BearerToken(adminJwt))) ~> route
  }
}
