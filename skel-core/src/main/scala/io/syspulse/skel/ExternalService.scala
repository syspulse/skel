package io.syspulse.skel

import io.jvm.uuid._

import akka.util.Timeout
import java.util.concurrent.TimeUnit

import com.typesafe.scalalogging.Logger

import scala.concurrent.duration.Duration

trait ExternalService[T <: ExternalService[T]] {
  def withAccessToken(token:String):T
  def withTimeout(timeout:Duration = Duration(1000, TimeUnit.MILLISECONDS)):T
}
