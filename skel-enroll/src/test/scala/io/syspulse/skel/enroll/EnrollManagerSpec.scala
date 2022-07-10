package io.syspulse.skel.enroll

import io.jvm.uuid._
import scala.util.Random

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.pattern.StatusReply
import org.scalatest.wordspec.AnyWordSpecLike

import io.syspulse.skel.util.Util
import io.syspulse.skel.crypto._

class EnrollManagerSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  "EnrollManager" should {

    "run flow 'START,EMAIL' " in {      
      val eid = EnrollSystem.start("START,STARTED,EMAIL,CONFIRM_EMAIL,EMAIL_CONFIRMED,CREATE_USER,USER_CREATED,FINISH,FINISHED")
      
      info(s"eid: ${eid}")
      
      Thread.sleep(150)
      
      val efActor = EnrollSystem.findFlow(eid)
      info(s"actor = ${efActor}")
      //val a1 = testKit.spawn(ef1)

      EnrollSystem.sendEmailConfirmation(eid,"123")
      
      Thread.sleep(1000)
    }    
  }

}
