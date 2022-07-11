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

    "run flow 'START,STARTED,EMAIL,CONFIRM_EMAIL,EMAIL_CONFIRMED,CREATE_USER,USER_CREATED,FINISH,FINISHED' " in {      
      val eid = EnrollSystem.start("START,STARTED,EMAIL,CONFIRM_EMAIL,EMAIL_CONFIRMED,CREATE_USER,USER_CREATED,FINISH,FINISHED",Some("0x001"))
      
      info(s"eid: ${eid}")
      
      Thread.sleep(50)
      val s1 = EnrollSystem.summary(eid)
      info(s"summary: ${s1}")    
      s1 should matchPattern { case Some(Enroll.Summary(eid,"STARTED",_,_,None,None,_,_,false,None)) => }
      s1.get.confirmToken should === (None)
      
      Thread.sleep(250)
      val s2 = EnrollSystem.summary(eid)
      info(s"summary: ${s2}")    
      s2 should matchPattern { case Some(Enroll.Summary(eid,"STARTED",_,_,None,None,_,_,false,None)) => }
      s2.get.confirmToken should === (None)

      val confirmToken = s1.get.confirmToken.get
      
      EnrollSystem.sendEmailConfirmation(eid,confirmToken)
      
      Thread.sleep(1000)

      val s3 = EnrollSystem.summary(eid)
      info(s"summary: ${s3}")

      s3 should matchPattern { case Some(_) => }
      s3 should matchPattern { case Some(Enroll.Summary(eid,"FINISHED",Some("0x001"),Some("email@"),None,None,_,_,true,None)) => }
    }    
  }

}
