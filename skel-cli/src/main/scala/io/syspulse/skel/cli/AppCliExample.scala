package io.syspulse.skel.cli

import scala.collection.immutable

import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.util.{Try,Success,Failure}
import akka.actor.typed.ActorSystem
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.util.Util

import io.syspulse.skel.util.Util
import scala.concurrent.ExecutionContext
import scala.concurrent.Await
import akka.actor.typed.scaladsl.Behaviors

object AppCliExample {
  
  def main(args: Array[String]): Unit = {
    val cli = new CliLoginable("http://localhost:8080")
    cli.shell()
  }
}