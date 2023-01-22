package io.syspulse.skel.enroll

import io.jvm.uuid._
import scala.util.Random

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.pattern.StatusReply
import org.scalatest.wordspec.AnyWordSpecLike

import io.syspulse.skel.util.Util
import io.syspulse.skel.crypto._

import io.syspulse.skel.enroll.flow.event._
import io.syspulse.skel.enroll.flow.EnrollFlow
import io.syspulse.skel.enroll.flow.Enrollment._
import io.syspulse.skel.enroll.flow.Enrollment


class EnrollEventSpec extends ScalaTestWithActorTestKit(s"""
      akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
      akka.actor.allow-java-serialization = true
      akka.persistence.journal.inmem.test-serialization = on
      akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
      akka.persistence.snapshot-store.local.dir = "target/snapshot-${UUID.randomUUID().toString}"
    """) with AnyWordSpecLike {

  private var counter = 0
  def newenrollId(): String = {
    counter += 1
    s"enroll-$counter"
  }

  "Enroll" should {

    "add email " in {
      val id1 = UUID.randomUUID()
      val e1 = EnrollEvent(id1)
      val a1 = testKit.spawn(e1)
      val probe = testKit.createTestProbe[StatusReply[Enrollment.Summary]]()
      
      a1 ! Enrollment.AddEmail("user-1@domain.com", probe.ref)
      val r = probe.receiveMessage()

      r should matchPattern { case StatusReply.Success(Enrollment.Summary(id1,"EMAIL_ACK",None,Some("user-1@domain.com"),None,None,None,None,_,_,false,_,_)) => }
      r.getValue.confirmToken !== (None)
      //r.getValue.eid === id1 (StatusReply.Success(Enrollment.Summary(id1,Some("user-1@email.com"),"CONFIRMING_EMAIL",false,r.getValue.confirmToken)))
    }

    "confirm email" in {
      val id1 = UUID.randomUUID()
      val e1 = EnrollEvent(id1)
      val a1 = testKit.spawn(e1)
      val probe = testKit.createTestProbe[StatusReply[Enrollment.Summary]]()
      
      a1 ! Enrollment.AddEmail("user-1@domain.com", probe.ref)
      val r1 = probe.receiveMessage()

      r1 should matchPattern { case StatusReply.Success(Enrollment.Summary(id1,"EMAIL_ACK",None,Some("user-1@domain.com"),None,None,None,None,_,_,false,Some(token),_)) => }
      val token = r1.getValue.confirmToken
      
      a1 ! Enrollment.ConfirmEmail(token.get, probe.ref)
      val r2 = probe.receiveMessage()
      r2 should matchPattern { case StatusReply.Success(Enrollment.Summary(id1,"CONFIRM_EMAIL_ACK",None,Some("user-1@domain.com"),None,None,None,None,_,_,false,None,_)) => }
      //probe.expectMessage(StatusReply.Success(Enroll.Summary(id1,Some("user-1@email.com"),"EMAIL_CONFIRMED",false,None)))
    }

    "geneate data for sig with delay" in {
      val id1 = UUID.randomUUID()
      val d1 = EnrollEvent.generateSigData(id1,"user-1@domain.com")
      Thread.sleep(500)
      val d2 = EnrollEvent.generateSigData(id1,"user-1@domain.com")
      d1 === (d2)
    }

    "confirm PublicKey" in {
      val id1 = UUID.randomUUID()
      val e1 = EnrollEvent(id1)
      val a1 = testKit.spawn(e1)
      val probe = testKit.createTestProbe[StatusReply[Enrollment.Summary]]()

      a1 ! Enrollment.AddEmail("user-1@domain.com", probe.ref)
      val r1 = probe.receiveMessage()
      //info(s"r1=${r1}")

      val kk = Eth.generate("0x01").get
      val addr = Eth.address(kk.pk)
      val d1 = EnrollEvent.generateSigData(id1,"user-1@domain.com")
      val sig = Eth.signMetamask(d1,kk)
      a1 ! Enrollment.AddPublicKey(sig , probe.ref)
      
      val r2 = probe.receiveMessage()
      //info(s"r2=${r2}")
      val sigData = Util.hex(sig.toArray())
      r2 should matchPattern { 
        case StatusReply.Success(Enrollment.Summary(id1,"PK_ACK",None,Some("user-1@domain.com"),None,None,Some(addr),Some(sigData),_,_,false,_,_)) => 
      }
    }

    
  }

}
