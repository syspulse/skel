package io.syspulse.skel.lake.job.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.lake.job._

class JobStoreNone(implicit config:Config) extends JobStore {
  val log = Logger(s"${this}")
  def +(job:Job):Try[JobStore] = {         
    Success(this)
  }
}
