package io.syspulse.skel.enroll

import java.time.Instant
import scala.util.Random
import com.typesafe.scalalogging.Logger
import scala.concurrent.duration._

import akka.NotUsed
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.SupervisorStrategy
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.AskPattern._

import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.RetentionCriteria
import akka.persistence.typed.scaladsl.Effect
import akka.persistence.typed.scaladsl.EventSourcedBehavior

import io.jvm.uuid._

import io.syspulse.skel.crypto.key.{PK,Signature}
import io.syspulse.skel.crypto.Eth
import io.syspulse.skel.util.Util
import io.syspulse.skel.crypto.SignatureEth
import akka.util.Timeout
import scala.concurrent.Await

import io.syspulse.skel.enroll.flow._

import com.typesafe.config.ConfigFactory
import scala.concurrent.Future
import akka.persistence.jdbc.testkit.scaladsl.SchemaUtils
import akka.Done
import scala.concurrent.ExecutionContext

class EnrollActorSystem(name:String = "EnrollSystem",enrollType:String = "state", actorConfig:Option[String] = None)(implicit config:Config) {
  val log = Logger(s"${this}")
  
  val system: ActorSystem[Command] = if(actorConfig.isDefined) 
    ActorSystem(EnrollFlow(config), name, ConfigFactory.parseString(actorConfig.get)) 
  else 
    ActorSystem(EnrollFlow(config), name)

  implicit val sched = system.scheduler
  implicit val timeout = Timeout(3.seconds)
  implicit val ec:ExecutionContext = system.executionContext

  // var config:Option[Config] = None
  // def withConfig(config:Config):EnrollActorSystem = {
  //   this.config = Some(config)
  //   this
  // }
  
  def withAutoTables():EnrollActorSystem = {
    val done: Future[Done] = SchemaUtils.createIfNotExists("jdbc-durable-state-store")(system)
    val r = Await.result(done,timeout.duration)
    log.info(s"Auto-Tables: ${r}")
    this
  }

  def start(flow:String,xid:Option[String] = None):UUID = {
    val eid = UUID.random
    system ! EnrollFlow.StartFlow(eid,enrollType,flow,xid,system.ignoreRef)
    eid
  }

  def continue(eid:UUID):UUID = {
    system ! EnrollFlow.ContinueFlow(eid)
    eid
  }

  def findEnroll(eid:UUID):Option[ActorRef[Command]] = {
    
    val enrollActor = Await.result(
      system.ask {
        ref => EnrollFlow.FindFlow(eid, ref)
      }, timeout.duration)

    log.info(s"enrollActor = ${enrollActor}")
    enrollActor
  }

  def summary(eid:UUID):Option[Enrollment.Summary] = { 
    val summary = Await.result(summaryFuture(eid), timeout.duration)
    log.info(s"summary = ${summary}")    
    summary
  }

  def summaryFuture(eid:UUID):Future[Option[Enrollment.Summary]] = {    
    for {
      summary <- {
        system.ask {
          ref => EnrollFlow.GetSummary(eid, enrollType, ref)
        }
      }
      // special case for non-found
      es <- {
        if(summary.isDefined && summary.get.phase == "START") 
          Future(None)
        else
          Future(summary)
      }
    } yield es
  }
  
  def confirmEmail(eid:UUID,confirmCode:String):Unit = {    
    system ! EnrollFlow.ConfirmEmail(eid,confirmCode)
  }

  def addEmail(eid:UUID,email:String):Unit = {    
    system ! EnrollFlow.AddEmail(eid,email)
  }  
}

//object EnrollSystem extends EnrollActorSystem()
class EnrollSystem()(implicit config:Config)  extends EnrollActorSystem
