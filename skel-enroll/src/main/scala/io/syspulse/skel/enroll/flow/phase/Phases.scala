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

trait Phase {
  val log = Logger(s"${this}") 
  val timeout = Duration("3 seconds")

  def run(data:Map[String,Any]):Try[String]
}

object Phases {
  var phases:Map[String,Phase] = Map(
    //"EMAIL_ACK" -> new PhaseSNSSend() // only for testing
    "EMAIL_ACK" -> new PhaseEmailSend()
  )
  
  def get(name:String) = phases.get(name)
}