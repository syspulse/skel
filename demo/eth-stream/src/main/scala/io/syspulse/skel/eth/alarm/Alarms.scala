package io.syspulse.skel.eth.alarm

import com.typesafe.scalalogging.Logger

import java.time.ZonedDateTime
import scala.util.Try
import scala.util.Success

import io.syspulse.skel.eth.notify.NotficationDest


case class UserAlarm(id:String,to:NotficationDest)

