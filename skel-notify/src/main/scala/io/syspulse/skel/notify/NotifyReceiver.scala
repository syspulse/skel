package io.syspulse.skel.notify

import scala.util.Random

import scala.collection.immutable
import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.util.Util

import io.jvm.uuid._

abstract class NotifyReceiver[R] {
  def send(title:String,msg:String,severity:Option[Int],scope:Option[String]):Try[R]
}

class NotifyStdout() extends NotifyReceiver[Option[_]] {
  def send(title:String,msg:String,severity:Option[Int],scope:Option[String]):Try[Option[_]] = {
    println(s"severity=${severity}:scope=${scope}: title=${title},msg=${msg}")
    Success(None)
  }
}

