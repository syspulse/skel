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

object App {
  
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem(Behaviors.empty, "App-Cli")
    implicit val ec = system.executionContext

    println(s"args: ${args.toList}")

    val cli = new AppCli("")
    cli.run(args.mkString(" "))

    Util.stdin( (line) => { cli.run(line); true})

    system.terminate(); System.exit(0) 
  }
}