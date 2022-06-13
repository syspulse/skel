package io.syspulse.skel.enroll

import io.jvm.uuid._
import scala.util.Random

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.pattern.StatusReply
import org.scalatest.wordspec.AnyWordSpecLike

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

  "Enroll" should {

    "add email " in {
      val id1 = UUID.randomUUID()
      val e1 = Enroll(id1)
      val a1 = testKit.spawn(e1)
      val probe = testKit.createTestProbe[StatusReply[Enroll.Summary]]()
      
      a1 ! Enroll.AddEmail("user-1@email.com", probe.ref)
      val r = probe.receiveMessage()

      r should matchPattern { case StatusReply.Success(Enroll.Summary(id1,Some("user-1@email.com"),"CONFIRM_EMAIL",_,_,false,_)) => }
      r.getValue.confirmToken !== (None)
      //r.getValue.eid === id1 (StatusReply.Success(Enroll.Summary(id1,Some("user-1@email.com"),"CONFIRMING_EMAIL",false,r.getValue.confirmToken)))
    }

    "confirm email" in {
      val id1 = UUID.randomUUID()
      val e1 = Enroll(id1)
      val a1 = testKit.spawn(e1)
      val probe = testKit.createTestProbe[StatusReply[Enroll.Summary]]()
      
      a1 ! Enroll.AddEmail("user-1@email.com", probe.ref)
      val r1 = probe.receiveMessage()

      val token = r1.getValue.confirmToken

      a1 ! Enroll.ConfirmEmail(token.get, probe.ref)
      val r2 = probe.receiveMessage()
      r2 should matchPattern { case StatusReply.Success(Enroll.Summary(id1,Some("user-1@email.com"),"EMAIL_CONFIRMED",_,_,false,None)) => }
      //probe.expectMessage(StatusReply.Success(Enroll.Summary(id1,Some("user-1@email.com"),"EMAIL_CONFIRMED",false,None)))
    }

    // "reject already added item" in {
    //   val enroll = testKit.spawn(Enroll(newenrollId()))
    //   val probe = testKit.createTestProbe[StatusReply[Enroll.Summary]]
    //   enroll ! Enroll.AddEmail("foo", 42, probe.ref)
    //   probe.receiveMessage().isSuccess should === (true)
    //   enroll ! Enroll.AddEmail("foo", 13, probe.ref)
    //   probe.receiveMessage().isError should === (true)
    // }

    // "remove item" in {
    //   val enroll = testKit.spawn(Enroll(newenrollId()))
    //   val probe = testKit.createTestProbe[StatusReply[Enroll.Summary]]
    //   enroll ! Enroll.AddEmail("foo", 42, probe.ref)
    //   probe.receiveMessage().isSuccess should === (true)
    //   enroll ! Enroll.RemoveItem("foo", probe.ref)
    //   probe.expectMessage(StatusReply.Success(Enroll.Summary(Map.empty, checkedOut = false)))
    // }

    // "adjust quantity" in {
    //   val enroll = testKit.spawn(Enroll(newenrollId()))
    //   val probe = testKit.createTestProbe[StatusReply[Enroll.Summary]]
    //   enroll ! Enroll.AddEmail("foo", 42, probe.ref)
    //   probe.receiveMessage().isSuccess should === (true)
    //   enroll ! Enroll.AdjustItemQuantity("foo", 43, probe.ref)
    //   probe.expectMessage(StatusReply.Success(Enroll.Summary(Map("foo" -> 43), checkedOut = false)))
    // }

    // "checkout" in {
    //   val enroll = testKit.spawn(Enroll(newenrollId()))
    //   val probe = testKit.createTestProbe[StatusReply[Enroll.Summary]]
    //   enroll ! Enroll.AddEmail("foo", 42, probe.ref)
    //   probe.receiveMessage().isSuccess should === (true)
    //   enroll ! Enroll.Checkout(probe.ref)
    //   probe.expectMessage(StatusReply.Success(Enroll.Summary(Map("foo" -> 42), checkedOut = true)))

    //   enroll ! Enroll.AddEmail("bar", 13, probe.ref)
    //   probe.receiveMessage().isError should === (true)
    // }

    // "keep its state" in {
    //   val enrollId = newenrollId()
    //   val enroll = testKit.spawn(Enroll(enrollId))
    //   val probe = testKit.createTestProbe[StatusReply[Enroll.Summary]]
    //   enroll ! Enroll.AddEmail("foo", 42, probe.ref)
    //   probe.expectMessage(StatusReply.Success(Enroll.Summary(Map("foo" -> 42), checkedOut = false)))

    //   testKit.stop(enroll)

    //   // start again with same enrollId
    //   val restartedenroll = testKit.spawn(Enroll(enrollId))
    //   val stateProbe = testKit.createTestProbe[Enroll.Summary]
    //   restartedenroll ! Enroll.Get(stateProbe.ref)
    //   stateProbe.expectMessage(Enroll.Summary(Map("foo" -> 42), checkedOut = false))
    // }

    // "start multiple times" in {
    //   val enrollId = newenrollId()
    //   info(s"enrollId=${enrollId}")

    //   val enroll = testKit.spawn(Enroll(enrollId))
    //   info(s"enroll=${enroll}")
    //   val probe = testKit.createTestProbe[StatusReply[Enroll.Summary]]
    //   enroll ! Enroll.AddEmail("foo", 42, probe.ref)
    //   probe.expectMessage(StatusReply.Success(Enroll.Summary(Map("foo" -> 42), checkedOut = false)))

    //   //testKit.stop(enroll)

    //   // start again with same enrollId
    //   val restartedenroll = testKit.spawn(Enroll(enrollId))
    //   info(s"restartedenroll=${restartedenroll}")
    //   val stateProbe = testKit.createTestProbe[Enroll.Summary]
    //   restartedenroll ! Enroll.Get(stateProbe.ref)
    //   stateProbe.expectMessage(Enroll.Summary(Map("foo" -> 42), checkedOut = false))


    //   val stateProbe2 = testKit.createTestProbe[Enroll.Summary]
    //   restartedenroll ! Enroll.Get(stateProbe2.ref)
    //   stateProbe2.expectMessage(Enroll.Summary(Map("foo" -> 42), checkedOut = false))
      

    //   val stateProbe3 = testKit.createTestProbe[Enroll.Summary]
    //   enroll ! Enroll.Get(stateProbe3.ref)
    //   stateProbe3.expectMessage(Enroll.Summary(Map("foo" -> 42), checkedOut = false))
    // }

  }

}
