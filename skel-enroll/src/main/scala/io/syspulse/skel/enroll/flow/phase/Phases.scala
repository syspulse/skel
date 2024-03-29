package io.syspulse.skel.enroll.flow.phase

import scala.util.Try

import scala.util.Random

import com.typesafe.scalalogging.Logger

import scala.concurrent.duration.Duration
import scala.concurrent.Future
import scala.concurrent.Await
import akka.actor.typed.scaladsl.Behaviors

import io.jvm.uuid._

import io.syspulse.skel
import io.syspulse.skel.util.Util

import io.syspulse.skel.enroll.Config
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

trait Phase {
  val log = Logger(s"${this}") 
  val timeout = FiniteDuration(3,TimeUnit.SECONDS)

  def run(data:Map[String,Any]):Try[String]
}

class Phases(config:Config) {
  
  var phases:Map[String,Phase] = Map(
    //"EMAIL_ACK" -> new PhaseSNSSend() // only for testing
    "EMAIL_ACK" -> new PhaseEmailSend(config),
    "CREATE_USER" -> new PhaseUserCreate(config),

    "FINISH_ACK" -> new PhaseFinish(config)
  )
  
  def get(name:String) = phases.get(name)
}
