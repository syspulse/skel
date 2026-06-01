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
import scala.concurrent.Future
import scala.concurrent.ExecutionContext


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

    implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

    Behaviors.receiveMessage {
      case GetAuths(replyTo) =>
        store.all.foreach(aa => replyTo ! Auths(aa, Some(aa.size)))
        Behaviors.same

      case CreateAuth(auth, replyTo) =>
        store.+(auth)
        replyTo ! AuthCreateRes(auth)
        Behaviors.same

      case GetAuth(auid, replyTo) =>
        store.?(auid).onComplete(replyTo ! _)
        Behaviors.same

      case DeleteAuth(auid, replyTo) =>
        store.del(auid)
        replyTo ! AuthActionRes(s"Success",Some(auid))
        Behaviors.same

      case RefreshTokenAuth(auid, refreshToken, uid, replyTo) =>
        store.?(auid).onComplete {
          case Failure(e) =>
            replyTo ! Failure(e)
          case Success(a) =>
            a.refreshToken match {
              case Some(rt) =>
                // fail if refreshToken is expired
                if(a.tsExpire <= System.currentTimeMillis()) {

                  log.error(s"refresh token expired: ${rt}: ${a.tsExpire}")
                  replyTo ! Failure(new Exception(s"refresh token expired: ${rt}"))

                } else {

                  val uid0:Option[UUID] = AuthJwt.getClaim(auid,"uid").map(UUID(_))
                  val uid1 = uid
                  val uid2 = a.uid

                  if(refreshToken != rt) {

                    log.error(s"refresh token invalid: ${rt}")
                    replyTo ! Failure(new Exception(s"refresh token invalid: ${rt}"))

                  } else {
                    val futureAuth: Future[Auth] = (uid0, uid1, uid2) match {
                      case (_,Some(uid1),_) =>
                        // override with specified UID
                        val accessToken = AuthJwt().generateAccessToken(Map( "uid" -> uid1.toString))
                        store.!(auid, accessToken,refreshToken, Some(uid1))

                      case (Some(uid0),_,Some(uid2)) =>
                        // claim and Existing token must be identical
                        if( (uid0  != uid2 ) ) {
                          log.error(s"unmatched identity: ${uid0}: ${uid2}")
                          Future.failed(new Exception(s"refresh token invalid: ${rt}"))
                        } else {
                          val accessToken = AuthJwt().generateAccessToken(Map( "uid" -> uid2.toString))
                          store.!(auid, accessToken,refreshToken,None)
                        }
                      case (_,_,Some(uid2)) =>
                        val accessToken = AuthJwt().generateAccessToken(Map( "uid" -> uid2.toString))
                        store.!(auid, accessToken,refreshToken,None)

                      case _ =>
                        log.error(s"missing identity: ${uid2}")
                        Future.failed(new Exception(s"refresh token invalid: ${rt}"))
                    }
                    futureAuth.onComplete(replyTo ! _)
                  }
                }
              case None =>
                log.warn(s"refresh token invalid: ${a.refreshToken}")
                replyTo ! Failure(new Exception(s"refresh token invalid"))
            }
        }
        Behaviors.same

      case Logoff(uid, replyTo) =>
        val futureAuths: Future[Seq[Auth]] = if(uid.isDefined) store.findUser(uid.get) else store.all
        futureAuths.foreach { auths =>
          Future.traverse(auths) { a =>
            store.del(a.accessToken).map(_ => Some(a)).recover { case e =>
              log.error(s"could not logoff: ${uid}: ${a.accessToken}")
              None
            }
          }.foreach { results =>
            val aa = results.flatten
            replyTo ! Auths(aa, Some(aa.size))
          }
        }
        Behaviors.same
    }
  }
}
