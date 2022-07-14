package io.syspulse.skel.enroll

import io.jvm.uuid._
import scala.util.Random

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.pattern.StatusReply

import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.DoNotDiscover
import org.scalatest.Sequential

import io.syspulse.skel.util.Util

import io.syspulse.skel.enroll.state._

// class EnrollStateSystemRecoverySpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

//   "EnrollStateSystemRecovery" should {

//     // "recover Enroll between systems" in {      
      
//     //   val em1 = new EnrollSystem("EM-1")
//     //   val eid1 = em1.start("START,START_ACK,EMAIL,EMAIL_ACK,CONFIRM_EMAIL,CONFIRM_EMAIL_ACK,CREATE_USER,CREATE_USER_ACK,FINISH,FINISH_ACK",Some("XID-001"))
//     //   info(s"eid: ${eid1}")
      
//     //   Thread.sleep(50)
//     //   val s1 = em1.summary(eid1)
//     //   info(s"summary: ${s1}")

//     //   em1.system.terminate()
//     //   Thread.sleep(150)

//     //   val em2 = new EnrollSystem("EM-1")
//     //   val s2 = em2.summary(eid1)
//     //   info(s"summary: ${s2}")
      
//     //   Thread.sleep(150)
//     //   em2.system.terminate()

//     // }    
//   }

// }

object TestObjects {
  val id1 = UUID.randomUUID()
}

class EnrollStateSystemRecoverySpec extends Sequential(
   new EnrollStateSpec1,
   new EnrollStateSpec2
)

class EnrollStateActorSystem extends DurableStoreTestKit

@DoNotDiscover
class EnrollStateSpec2 extends AnyWordSpecLike {

  "Enroll-2" should {

    "recover state email from Different ActorSystem" in {
      val as = new EnrollStateActorSystem

      val id1 = TestObjects.id1

      val e2 = Enroll(id1)      
      val a2 = as.testKit.spawn(e2)
      val probe2 = as.testKit.createTestProbe[Enroll.Summary]
      a2 ! Enroll.Get(probe2.ref)
      val r2 = probe2.receiveMessage()
      info(s"r2 = ${r2}")

      as.testKit.internalSystem.terminate()
    }

  }
}

@DoNotDiscover
class EnrollStateSpec1 extends AnyWordSpecLike {


  "Enroll-1" should {


    "recover state email from same ActorSystem" in {
      val as = new EnrollStateActorSystem

      val id1 = TestObjects.id1

      val e1 = Enroll(id1)
      val a1 = as.testKit.spawn(e1)
      val probe = as.testKit.createTestProbe[StatusReply[Enroll.Summary]]
      a1 ! Enroll.AddEmail("user-1@email.com", probe.ref)
      val r = probe.receiveMessage()
      
      val probe1 = as.testKit.createTestProbe[Enroll.Summary]
      a1 ! Enroll.Get(probe1.ref)
      val r1 = probe1.receiveMessage()
      info(s"r1 = ${r1}")

      as.testKit.stop(a1)

      val e2 = Enroll(id1)      
      val a2 = as.testKit.spawn(e2)
      val probe2 = as.testKit.createTestProbe[Enroll.Summary]
      a2 ! Enroll.Get(probe2.ref)
      val r2 = probe2.receiveMessage()
      info(s"r2 = ${r2}")

      as.testKit.internalSystem.terminate()
    }
  }
}
