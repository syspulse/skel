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


// ATTENTION: EnrollSystem ignores ScalaTestWithActorTestKit() and reads from project_root/conf/application.conf !
class EnrollFlowSpec extends DurableStoreTestKit() with AnyWordSpecLike {

  val configEvents = """
akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
akka.persistence.snapshot-store.local.dir = "target/snapshot-${UUID.randomUUID().toString}"
akka.actor.allow-java-serialization = on
  """

  val configStates = getConfigDurable()
  implicit val config = Config()

  "EnrollFlowSpec" should {

    "run full flow (Events) " in {      
      
      val es = new EnrollActorSystem("ES-Event-1","event",Some(configEvents))

      val eid = es.start(
        "START,START_ACK,EMAIL,EMAIL_ACK,CONFIRM_EMAIL,CONFIRM_EMAIL_ACK,CREATE_USER,CREATE_USER_ACK,FINISH,FINISH_ACK",
        Some("0x001")
      )
      
      info(s"eid: ${eid}")
      
      Thread.sleep(50)
      val s1 = es.summary(eid)
      info(s"summary: ${s1}")    
      s1 should matchPattern { case Some(Enrollment.Summary(eid,"START_ACK",_,_,None,None,None,None,_,_,false,None,_)) => }
      s1.get.confirmToken should === (None)
      
      Thread.sleep(250)
      val s2 = es.summary(eid)
      info(s"summary: ${s2}")    
      s2 should matchPattern { case Some(Enrollment.Summary(eid,"EMAIL",Some("0x001"),None,None,_,None,None,_,_,false,None,_)) => }
      s2.get.confirmToken should === (None)

      es.addEmail(eid,"1@email.org")
      Thread.sleep(150)
      
      val s3 = es.summary(eid)
      info(s"summary: ${s3}")    
      s3 should matchPattern { case Some(Enrollment.Summary(eid,"CONFIRM_EMAIL",Some("0x001"),Some("1@email.org"),None,None,None,None,_,_,false,Some(_),_)) => }
      s3.get.confirmToken should !== (None)

      val confirmToken = s3.get.confirmToken.get
      es.confirmEmail(eid,confirmToken)
      
      Thread.sleep(250)

      val s10 = es.summary(eid)
      info(s"summary: ${s10}")

      s10 should matchPattern { case Some(_) => }
      s10 should matchPattern { case Some(Enrollment.Summary(eid,"FINISH_ACK",Some("0x001"),Some("1@email.org"),None,None,None,None,_,_,true,None,_)) => }
    }

    "run full flow (State) " in {            
      val es = new EnrollActorSystem("ES-State-1","state",Some(configStates))

      val eid = es.start(
        "START,START_ACK,EMAIL,EMAIL_ACK,CONFIRM_EMAIL,CONFIRM_EMAIL_ACK,CREATE_USER,CREATE_USER_ACK,FINISH,FINISH_ACK",        
        Some("0x001")
      )
      
      info(s"eid: ${eid}")
      
      Thread.sleep(50)
      val s1 = es.summary(eid)
      info(s"summary: ${s1}")    
      s1 should matchPattern { case Some(Enrollment.Summary(eid,"START_ACK",_,_,None,None,None,None,_,_,false,None,_)) => }
      s1.get.confirmToken should === (None)
      
      Thread.sleep(250)
      val s2 = es.summary(eid)
      info(s"summary: ${s2}")    
      s2 should matchPattern { case Some(Enrollment.Summary(eid,"EMAIL",Some("0x001"),_,None,None,None,None,_,_,false,None,_)) => }
      s2.get.confirmToken should === (None)

      es.addEmail(eid,"1@email.org")
      Thread.sleep(150)
      
      val s3 = es.summary(eid)
      info(s"summary: ${s3}")    
      s3 should matchPattern { case Some(Enrollment.Summary(eid,"CONFIRM_EMAIL",Some("0x001"),Some("1@email.org"),None,None,None,None,_,_,false,Some(_),_)) => }
      s3.get.confirmToken should !== (None)

      val confirmToken = s3.get.confirmToken.get
      es.confirmEmail(eid,confirmToken)
      
      Thread.sleep(250)

      val s10 = es.summary(eid)
      info(s"summary: ${s10}")

      s10 should matchPattern { case Some(_) => }
      s10 should matchPattern { case Some(Enrollment.Summary(eid,"FINISH_ACK",Some("0x001"),Some("1@email.org"),None,None,None,None,_,_,true,None,_)) => }
   }
  }
}
