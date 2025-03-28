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


object AuthRegistry {
  val log = Logger(s"${this}")

  final case class GetAuths(replyTo: ActorRef[Auths]) extends Command
  final case class CreateAuth(auth: Auth, replyTo: ActorRef[AuthCreateRes]) extends Command
  final case class GetAuth(auid: String, replyTo: ActorRef[Try[Auth]]) extends Command
  final case class DeleteAuth(auid: String, replyTo: ActorRef[AuthActionRes]) extends Command
  final case class RefreshTokenAuth(auid: String, refreshToken:String, uid:Option[UUID], replyTo: ActorRef[Try[Auth]]) extends Command
  final case class Logoff(uid:Option[UUID], replyTo: ActorRef[Auths]) extends Command

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
        val aa = store.all
        replyTo ! Auths(aa,Some(aa.size))
        Behaviors.same

      case CreateAuth(auth, replyTo) =>
        val store1 = store.+(auth)
        replyTo ! AuthCreateRes(auth)
        Behaviors.same

      case GetAuth(auid, replyTo) =>
        replyTo ! store.?(auid)
        Behaviors.same

      case DeleteAuth(auid, replyTo) =>
        val store1 = store.del(auid)
        replyTo ! AuthActionRes(s"Success",Some(auid))
        Behaviors.same
      
      case RefreshTokenAuth(auid, refreshToken, uid, replyTo) =>
        val auth = store.?(auid)
        val r:Try[Auth] = auth.flatMap(a => 
          a.refreshToken match {
            case Some(rt) => 
              // fail if refreshToken is expired
              if(a.tsExpire <= System.currentTimeMillis()) {

                log.error(s"refresh token expired: ${rt}: ${a.tsExpire}")
                Failure(new Exception(s"refresh token expired: ${rt}"))
                
              } else {

                val uid0:Option[UUID] = AuthJwt.getClaim(auid,"uid").map(UUID(_))
                val uid1 = uid
                val uid2 = a.uid

                if(refreshToken != rt) {

                  log.error(s"refresh token invalid: ${rt}")
                  Failure(new Exception(s"refresh token invalid: ${rt}"))
                
                } 
                // NOTE: By default uid1 is None since expired token could not be validated
                else 
                // if( (uid1 != None) && (uid0 != uid1) ) {
                  
                //   log.error(s"invalid 'uid' claim: ${uid0}: ${uid1}")
                //   Failure(new Exception(s"refresh token invalid: ${rt}"))
                
                // } else {
                //   val accessToken = AuthJwt.generateAccessToken(Map( "uid" -> uid0.get)) 
                  
                //   // update Auth
                //   store.!(auid, accessToken,refreshToken)
                // }
                {
                  (uid0, uid1, uid2) match {
                    case (_,Some(uid1),_) => 
                      // override with specified UID
                      val accessToken = AuthJwt().generateAccessToken(Map( "uid" -> uid1.toString)) 
                      store.!(auid, accessToken,refreshToken, Some(uid1))

                    case (Some(uid0),_,Some(uid2)) => 
                      // claim and Existing token must be identical
                      if( (uid0  != uid2 ) ) {                
                        log.error(s"unmatched identity: ${uid0}: ${uid2}")
                        Failure(new Exception(s"refresh token invalid: ${rt}"))
                      } else {
                        val accessToken = AuthJwt().generateAccessToken(Map( "uid" -> uid2.toString)) 
                        store.!(auid, accessToken,refreshToken,None)
                      }
                    case (_,_,Some(uid2)) => 
                      val accessToken = AuthJwt().generateAccessToken(Map( "uid" -> uid2.toString)) 
                      store.!(auid, accessToken,refreshToken,None)
                    
                    case _ =>
                      log.error(s"missing identity: ${uid2}")
                      Failure(new Exception(s"refresh token invalid: ${rt}"))
                  }
                }
              }
            case None => 
              log.warn(s"refresh token invalid: ${a.refreshToken}")
              Failure(new Exception(s"refresh token invalid"))
          }
        )
        replyTo ! r
        Behaviors.same

      case Logoff(uid, replyTo) =>
        val auths:Seq[Auth] = if(uid.isDefined) store.findUser(uid.get) else store.all
        val aa = auths.flatMap(a => 
          store.del(a.accessToken) match {
            case Success(s) => Some(a)
            case Failure(e) =>
              log.error(s"could not logoff: ${uid}: ${a.accessToken}")
              None
          }
        )
        replyTo ! Auths(aa,Some(aa.size))
        Behaviors.same
    }
  }
}

