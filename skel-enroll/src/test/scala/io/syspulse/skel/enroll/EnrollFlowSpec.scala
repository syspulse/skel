package io.syspulse.skel.enroll

import io.jvm.uuid._
import scala.util.Random

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.pattern.StatusReply
import org.scalatest.wordspec.AnyWordSpecLike

import io.syspulse.skel.util.Util
import io.syspulse.skel.crypto._

class EnrollFlowSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  "EnrollFlow" should {

    "run flow 'START,EMAIL' " in {      
      val eid = EnrollSystem.start("START,EMAIL,CONFIRM_EMAIL,WAIT,CREATE_USER")
      
      info(s"eid: ${eid}")
      
      Thread.sleep(1000)
      
      val efActor = EnrollSystem.findFlow(eid)
      info(s"actor = ${efActor}")
      //val a1 = testKit.spawn(ef1)

      EnrollSystem.sendEmailConfirmation(eid,"123")
      
      
      // val probe = testKit.createTestProbe[StatusReply[Enroll.Summary]]()
      // val r = probe.receiveMessage()
      // info(s"r = ${r}")
      // a1 ! Enroll.Start(probe.ref)

      

      // r should matchPattern { case StatusReply.Success(Enroll.Summary(id1,"CONFIRM_EMAIL",Some("user-1@domain.com"),None,None,_,_,false,_)) => }
      // r.getValue.confirmToken !== (None)
      
    }    
  }

}
