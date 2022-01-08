package io.syspulse.skel.auth.jwt

import com.typesafe.scalalogging.Logger

import pdi.jwt.{Jwt, JwtAlgorithm, JwtHeader, JwtClaim, JwtOptions}
import com.nimbusds.jose.jwk._

import io.jvm.uuid._
import ujson._

import io.syspulse.skel.auth.Auth

object AuthJwt {
  val log = Logger(s"${this}")

  def decode(a:Auth) = {
    Jwt.decode(a.idToken,JwtOptions(signature = false))
  }

  def decodeClaim(a:Auth):Map[String,String] = {
    val jwt = decode(a)
    ujson.read(jwt.get.toJson).obj.map(v=> v._1 -> v._2.toString.stripSuffix("\"").stripPrefix("\"")).toMap
  }

}

