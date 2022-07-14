package io.syspulse.skel.enroll

import io.jvm.uuid._
import scala.util.Random

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.pattern.StatusReply
import org.scalatest.wordspec.AnyWordSpecLike

import io.syspulse.skel.util.Util
import io.syspulse.skel.crypto._
import io.syspulse.skel.enroll.event.Enroll

// ATTENTION: EnrollSystem ignores ScalaTestWithActorTestKit() and reads from project_root/conf/application.conf !
class EnrollFlowEventSpec extends ScalaTestWithActorTestKit() with AnyWordSpecLike {

  val config = """
akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
akka.persistence.snapshot-store.local.dir = "target/snapshot-${UUID.randomUUID().toString}"
akka.actor.allow-java-serialization = on
  """

  "EnrollFlowEventSpec" should {

    "run full flow '1' " in {      
      
      val es = new EnrollActorSystem("ES-1",Some(config))

      val eid = es.start(
        "START,START_ACK,EMAIL,EMAIL_ACK,CONFIRM_EMAIL,CONFIRM_EMAIL_ACK,CREATE_USER,CREATE_USER_ACK,FINISH,FINISH_ACK",
        Some("0x001")
      )
      
      info(s"eid: ${eid}")
      
      Thread.sleep(50)
      val s1 = es.summary(eid)
      info(s"summary: ${s1}")    
      s1 should matchPattern { case Some(Enroll.Summary(eid,"START_ACK",_,_,None,None,_,_,false,None)) => }
      s1.get.confirmToken should === (None)
      
      Thread.sleep(250)
      val s2 = es.summary(eid)
      info(s"summary: ${s2}")    
      s2 should matchPattern { case Some(Enroll.Summary(eid,"EMAIL",Some("0x001"),_,None,None,_,_,false,None)) => }
      s2.get.confirmToken should === (None)

      es.addEmail(eid,"1@email.org")
      Thread.sleep(150)
      
      val s3 = es.summary(eid)
      info(s"summary: ${s3}")    
      s3 should matchPattern { case Some(Enroll.Summary(eid,"CONFIRM_EMAIL",Some("0x001"),Some("1@email.org"),None,None,_,_,false,Some(_))) => }
      s3.get.confirmToken should !== (None)

      val confirmToken = s3.get.confirmToken.get
      es.sendEmailConfirmation(eid,confirmToken)
      
      Thread.sleep(250)

      val s10 = es.summary(eid)
      info(s"summary: ${s10}")

      s10 should matchPattern { case Some(_) => }
      s10 should matchPattern { case Some(Enroll.Summary(eid,"FINISH_ACK",Some("0x001"),Some("1@email.org"),None,None,_,_,true,None)) => }
    }    
  }

}
