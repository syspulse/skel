package io.syspulse.skel.twit

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future,Await}
import scala.util.{Success, Failure, Random}

import com.danielasfregola.twitter4s.TwitterRestClient
import com.danielasfregola.twitter4s.entities.{ConsumerToken,AccessToken}

import com.typesafe.scalalogging.Logger
import scala.concurrent.duration.Duration

class TweetUsers(users:String, config:Config) {

  val log = Logger(s"${this}")

  val EMPTY = Map[Long,String]()

  val customToken = ConsumerToken(config.twitterConsumerKey,config.twitterConsumerSecret)
  val accessToken = AccessToken(config.twitterAccessKey,config.twitterAccessSecret)
  log.info(s"tokens: ${customToken}, ${accessToken}")

  val client = TwitterRestClient(customToken,accessToken)

  def resolve():Map[Long,String] = {
    val unresolved = users.split(",").map(u => {
      try {
        val id = u.toLong
        (Some(id),None)
      } catch {
        case e:NumberFormatException => {
          (None,Some(u))
        }
      }
    })

    val ids = unresolved.flatMap(_._1)
    val scr = unresolved.flatMap(_._2)

    log.info(s"users: ids=${ids}, scr=${scr}")

    val r1 = if(ids.size!=0) {
      val ids1 = for {
        idUsers <- client.usersByIds(ids.toSeq)
      } yield idUsers.data  

      try {
        Await.result(ids1.map(_.map(u => u.id -> u.screen_name).toMap),Duration("10 seconds"))
      } catch {
        case e:Exception => log.error(s"could not resolve users: '${ids}'",e); EMPTY
      }
    } else EMPTY
    log.info(s"users: ${r1}")

    val r2 = if(scr.size!=0) {
      val ids2 = for {
        scrUsers <- client.users(scr.toSeq)
      } yield scrUsers.data

      try {
        Await.result(ids2.map(_.map(u => u.id -> u.screen_name).toMap),Duration("10 seconds"))
        } catch {
          case e:Exception => log.error(s"could not resolve users: '${scr}'",e); EMPTY
        }
    } else EMPTY
    log.info(s"users: ${r2}")
    
    r1 ++ r2
  }
}
