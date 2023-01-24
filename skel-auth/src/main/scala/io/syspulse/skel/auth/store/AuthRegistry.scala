package io.syspulse.skel.auth.store

import scala.collection.immutable
import io.jvm.uuid._
import com.typesafe.scalalogging.Logger

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

import io.syspulse.skel.Command

import io.syspulse.skel.auth._

import io.syspulse.skel.auth.server._
import io.syspulse.skel.auth.store.AuthStore
import io.syspulse.skel.auth.store.AuthStoreMem

import io.syspulse.skel.auth.server.{Auths, AuthRes, AuthCreateRes, AuthActionRes}
import scala.util.Try
import scala.util.Failure
import scala.util.Success
import io.syspulse.skel.auth.jwt.AuthJwt
import io.syspulse.skel.auth.permissions.rr.Permisson
import io.syspulse.skel.auth.permissions.rbac.Permissions

object AuthRegistry {
  val log = Logger(s"${this}")

  final case class GetAuths(replyTo: ActorRef[Auths]) extends Command
  final case class CreateAuth(auth: Auth, replyTo: ActorRef[AuthCreateRes]) extends Command
  final case class GetAuth(auid: String, replyTo: ActorRef[Try[Auth]]) extends Command
  final case class DeleteAuth(auid: String, replyTo: ActorRef[AuthActionRes]) extends Command
  final case class RefreshTokenAuth(auid: String, refreshToken:String, uid:Option[UUID], replyTo: ActorRef[Try[Auth]]) extends Command

    // this var reference is unfortunately needed for Metrics access
  var store: AuthStore = null //new AuthStoreDB //new AuthStoreCache

  def apply(store: AuthStore = new AuthStoreMem): Behavior[Command] = {
    this.store = store
    registry(store)
  }

  private def registry(store: AuthStore): Behavior[Command] = {
    this.store = store

    Behaviors.receiveMessage {
      case GetAuths(replyTo) =>
        replyTo ! Auths(store.all)
        Behaviors.same

      case CreateAuth(auth, replyTo) =>
        val store1 = store.+(auth)
        replyTo ! AuthCreateRes(auth)
        registry(store1.getOrElse(store))

      case GetAuth(auid, replyTo) =>
        replyTo ! store.?(auid)
        Behaviors.same

      case DeleteAuth(auid, replyTo) =>
        val store1 = store.del(auid)
        replyTo ! AuthActionRes(s"Success",Some(auid))
        registry(store1.getOrElse(store))
      
      case RefreshTokenAuth(auid, refreshToken, uid, replyTo) =>
        val auth = store.?(auid)
        val r:Try[Auth] = auth.flatMap(a => 
          a.refreshToken match {
            case Some(rt) => 
              val uid0 = AuthJwt.getClaim(auid,"uid")
              val uid1 = uid.map(_.toString)

              if(refreshToken != rt) {

                log.error(s"refresh token invalid: ${rt}")
                Failure(new Exception(s"refresh token invalid: ${rt}"))

              } else if( (uid != Some(Permissions.USER_ADMIN)) && (uid0 != uid1) ) {
                
                log.error(s"invalid 'uid' claim: ${uid0}: ${uid1}")
                Failure(new Exception(s"refresh token invalid: ${rt}"))
              
              } else {
                val accessToken = AuthJwt.generateAccessToken(Map( "uid" -> uid0.get)) 
                // update Auth
                store.!(auid, accessToken,refreshToken)                
              }
            case None => 
              log.warn(s"refresh token invalid: ${a.refreshToken}")
              Failure(new Exception(s"refresh token invalid"))
          }
        )
        replyTo ! r
        Behaviors.same
    }
  }
}

