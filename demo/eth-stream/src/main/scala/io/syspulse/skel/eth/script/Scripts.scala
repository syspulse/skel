package io.syspulse.skel.eth.script

import scala.util.Random

import com.typesafe.scalalogging.Logger

import io.syspulse.skel.dsl.JS
import java.time.LocalDateTime
import java.time.ZonedDateTime
import scala.util.Try
import scala.util.Success

import io.syspulse.skel.eth.alarm.UserAlarm
import io.syspulse.skel.eth.notify.{NotficationEmail,NotficationPush}

case class UserScript(id:String,script:String) {
  val log = Logger(s"${this.getClass().getSimpleName()}")

  val scriptText = 
    (if(script.trim.startsWith("@")) {
      Some(scala.io.Source.fromFile(script.trim.drop(1)).getLines().mkString("\n"))
    } else 
      Some(script)
    ).getOrElse("")

  log.info(s"script='${scriptText}'")

  val js = new JS(scriptText)

  def getJs() = js
}

object Scripts {

  var scripts = Map[UserScript,List[UserAlarm]](
    UserScript("S-0001","if(to_address == '0x41fb81197275db2105a839fce23858dabf86c73c') 'Transfer: '+to_address; else null;") -> List( UserAlarm("A-0001",NotficationEmail("user1@test.org")), UserAlarm("A-0002",NotficationPush("user1-PUSH-1")))
  )

  def +(script:String) = {
    scripts = scripts + ( UserScript(Random.nextInt().toString,script) -> List( UserAlarm("A-0002",NotficationPush("user1-PUSH-1",enabled = true)) ))
  }

}


