package io.syspulse.skel.enroll

import io.jvm.uuid._
import scala.util.Random

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.pattern.StatusReply
import org.scalatest.wordspec.AnyWordSpecLike

import io.syspulse.skel.util.Util
import io.syspulse.skel.crypto._

class EnrollSystemRecoverySpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  "EnrollSystemRecovery" should {

    // "recover Enroll between systems" in {      
      
    //   val em1 = new EnrollSystem("EM-1")
    //   val eid1 = em1.start("START,START_ACK,EMAIL,EMAIL_ACK,CONFIRM_EMAIL,CONFIRM_EMAIL_ACK,CREATE_USER,CREATE_USER_ACK,FINISH,FINISH_ACK",Some("XID-001"))
    //   info(s"eid: ${eid1}")
      
    //   Thread.sleep(50)
    //   val s1 = em1.summary(eid1)
    //   info(s"summary: ${s1}")

    //   em1.system.terminate()
    //   Thread.sleep(150)

    //   val em2 = new EnrollSystem("EM-1")
    //   val s2 = em2.summary(eid1)
    //   info(s"summary: ${s2}")
      
    //   Thread.sleep(150)
    //   em2.system.terminate()

    // }    
  }

}
