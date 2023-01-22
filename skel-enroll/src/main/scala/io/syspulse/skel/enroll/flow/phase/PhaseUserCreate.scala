package io.syspulse.skel.enroll.flow.phase

import scala.util.Random

import com.typesafe.scalalogging.Logger

import scala.util.Try
import scala.util.Failure
import scala.util.Success

import scala.concurrent.duration.Duration
import scala.concurrent.Future
import scala.concurrent.Await
import akka.actor.typed.scaladsl.Behaviors

import io.jvm.uuid._

import io.syspulse.skel
import io.syspulse.skel.util.Util

import io.syspulse.skel.user.UserService
import io.syspulse.skel.user.User

import io.syspulse.skel.enroll.Config

class PhaseUserCreate(config:Config) extends Phase {
  import io.syspulse.skel.FutureAwaitable._

  def create(email:String,name:String,xid:String,avatar:String):Try[UUID] = {
    // log.info(s"Sending email(${toUri},${subj},${msg}) -> ${NotifyService.service}")
    val user = UserService.create(email,name,xid,avatar)
    log.info(s"user=${user}")
    user.map(_.id)    
  }

  def run(data:Map[String,Any]):Try[String] = {
    val email = data.get("email").getOrElse("").toString
    val name = data.get("name").getOrElse("").toString
    val xid = data.get("xid").getOrElse("").toString
    val avatar = data.get("avatar").getOrElse("").toString

    create(email,name,xid,avatar).map(_.toString)
  }
}
