package io.syspulse.skel.auth.jwt

import org.scalatest.{Ignore}
import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import java.time._
import io.jvm.uuid._
import scala.util.Success

import io.syspulse.skel.util.Util
import pdi.jwt.JwtAlgorithm
import io.syspulse.skel.auth.permissions.rbac.Permissions

class AuthJwtSpec extends AnyWordSpec with Matchers {
  val testDir = this.getClass.getClassLoader.getResource(".").getPath
  
  "AuthJWT" should {
    "default secret is secure" in {      
      info(s"${AuthJwt.defaultSecret}")
      AuthJwt.defaultSecret !== ""
    }

    "validate JWT with defaults" in {
      val j1 = AuthJwt.generateToken()
      AuthJwt.isValid(j1) === true
    }

    "fail JWT validation with a wrong secret" in {
      val j1 = AuthJwt.generateToken(secret = "secret")
      AuthJwt.isValid(j1,secret = "secret2") === false
    }

    "fail JWT validation with a wrong HS algo" in {
      val j1 = AuthJwt.generateToken(secret = "secret2", algo = JwtAlgorithm.HS512)
      AuthJwt.isValid(j1,secret = "secret2", algo = JwtAlgorithm.HS256) === false
    }

    "validate generated Admin token" in {
      val j1 = AuthJwt.generateAccessToken(Map("uid" -> Permissions.USER_ADMIN.toString))
      AuthJwt.isValid(j1) === true
    }

    "validate JWT token as Admin" in {
      val j1 = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzUxMiJ9.eyJleHAiOjE2NzQzNDMxODAsImlhdCI6MTY3NDMzOTU4MCwidWlkIjoiZmZmZmZmZmYtZmZmZi1mZmZmLWZmZmYtZmZmZmZmZmZmZmZmIn0.VaYS9CVgFKH9sw81TTWTuztG-zo4y7LAab7Sb_diQZMViMH9HZpHDMq0NGbt7mmvyhB1tsLnsv-AZMnUAtZMSw"
      AuthJwt.isValid(j1) === true
    }

  }
}
