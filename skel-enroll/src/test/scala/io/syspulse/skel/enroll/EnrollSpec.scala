package io.syspulse.skel.enroll

import io.jvm.uuid._
import scala.util.Random

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.pattern.StatusReply
import org.scalatest.wordspec.AnyWordSpecLike

import io.syspulse.skel.util.Util
import io.syspulse.skel.crypto._

class EnrollSpec extends ScalaTestWithActorTestKit(s"""
      akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
      akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
      akka.persistence.snapshot-store.local.dir = "target/snapshot-${UUID.randomUUID().toString}"
    """) with AnyWordSpecLike {

  private var counter = 0
  def newenrollId(): String = {
    counter += 1
    s"enroll-$counter"
  }

  "Enroll Flow" should {

    "add email " in {
      val id1 = UUID.randomUUID()
      val e1 = Enroll(id1)
      val a1 = testKit.spawn(e1)
      val probe = testKit.createTestProbe[StatusReply[Enroll.Summary]]()
      
      a1 ! Enroll.AddEmail("user-1@domain.com", probe.ref)
      val r = probe.receiveMessage()

      r should matchPattern { case StatusReply.Success(Enroll.Summary(id1,"CONFIRM_EMAIL",Some("user-1@domain.com"),None,None,_,_,false,_)) => }
      r.getValue.confirmToken !== (None)
      //r.getValue.eid === id1 (StatusReply.Success(Enroll.Summary(id1,Some("user-1@email.com"),"CONFIRMING_EMAIL",false,r.getValue.confirmToken)))
    }

    "confirm email" in {
      val id1 = UUID.randomUUID()
      val e1 = Enroll(id1)
      val a1 = testKit.spawn(e1)
      val probe = testKit.createTestProbe[StatusReply[Enroll.Summary]]()
      
      a1 ! Enroll.AddEmail("user-1@domain.com", probe.ref)
      val r1 = probe.receiveMessage()

      val token = r1.getValue.confirmToken

      a1 ! Enroll.ConfirmEmail(token.get, probe.ref)
      val r2 = probe.receiveMessage()
      r2 should matchPattern { case StatusReply.Success(Enroll.Summary(id1,"EMAIL_CONFIRMED",Some("user-1@domain.com"),None,None,_,_,false,None)) => }
      //probe.expectMessage(StatusReply.Success(Enroll.Summary(id1,Some("user-1@email.com"),"EMAIL_CONFIRMED",false,None)))
    }

    "geneate data for sig with delay" in {
      val id1 = UUID.randomUUID()
      val d1 = Enroll.generateSigData(id1,"user-1@domain.com")
      Thread.sleep(500)
      val d2 = Enroll.generateSigData(id1,"user-1@domain.com")
      d1 === (d2)
    }

    "confirm PublicKey" in {
      val id1 = UUID.randomUUID()
      val e1 = Enroll(id1)
      val a1 = testKit.spawn(e1)
      val probe = testKit.createTestProbe[StatusReply[Enroll.Summary]]()

      a1 ! Enroll.AddEmail("user-1@domain.com", probe.ref)
      val r1 = probe.receiveMessage()
      info(s"r1=${r1}")

      val kk = Eth.generate("0x01").get
      val addr = Eth.address(kk.pk)
      val d1 = Enroll.generateSigData(id1,"user-1@domain.com")
      val sig = Eth.signMetamask(d1,kk)
      a1 ! Enroll.AddPublicKey(sig , probe.ref)
      
      val r2 = probe.receiveMessage()
      info(s"r2=${r2}")
      val sigData = Util.hex(sig.toArray())
      r2 should matchPattern { 
        case StatusReply.Success(Enroll.Summary(id1,"PK_CONFIRMED",Some("user-1@domain.com"),Some(addr),Some(sigData),_,_,false,_)) => }
    }

    
  }

}
