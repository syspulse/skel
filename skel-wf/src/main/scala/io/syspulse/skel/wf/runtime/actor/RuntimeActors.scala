package io.syspulse.skel.wf.runtime.actor

import java.util.concurrent.{ArrayBlockingQueue, BlockingQueue}

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.wf.runtime._
import io.syspulse.skel.wf._
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.AskPattern._
import akka.NotUsed
import akka.actor.typed.Terminated
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.ActorRef
import akka.util.Timeout
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import scala.concurrent.Await

trait RuntimeCmd
final case class StopRuntime() extends RuntimeCmd
final case class SpawnLinking(link:Linking,replyTo:ActorRef[SpawnLinkingRes]) extends RuntimeCmd
final case class SpawnLinkingRes(r:Running) extends RuntimeCmd

class RunningActor(link:Linking,context: ActorContext[ExecEvent]) extends AbstractBehavior[ExecEvent](context) with Running {
  val log = Logger(s"${this}")

  def getLinking = link
  var actor:ActorRef[ExecEvent] = context.self
  val failure = Failure(new Exception(s"actor is not bound"))
  
  override def !(e: ExecEvent):Try[RunningActor] = {
    actor ! e 
    Success(this)
  }

  def start():Try[RunningActor] = {
    Success(this)
  }

  def stop():Try[RunningActor] = {
    actor ! ExecCmdStop(actor.toString)
    Success(this)
  }

  override def onMessage(msg: ExecEvent): Behavior[ExecEvent] = {
    msg match {
      case ExecCmdRunningReq(replyTo) => 
        replyTo ! ExecCmdRunningRes(this)
        this
      case ExecCmdStop(who) =>
        log.info(s"stopping: actor=${context.self}")
        Behaviors.stopped  
      case e:ExecEvent =>
        link.output(e)
        this
    }
  }
}

object RunningActor {
  
  def apply(master:ActorRef[RuntimeCmd],link:Linking): Behavior[ExecEvent] =
    Behaviors.setup(context => {
      val actor = context.self
      val ra = new RunningActor(link,context)
      ra      
    })
}

// ---------------------------------------------------------- Runtime ------------------
object RuntimeActors {
  val as = ActorSystem(RuntimeActors(), "RuntimeActors")
  implicit val sched = as.scheduler

  def apply(): Behavior[RuntimeCmd] = Behaviors.setup { context =>    
    Behaviors.receiveMessage {
      case SpawnLinking(link,relpyTo) => 
        implicit val timeout = Timeout(FiniteDuration(100,TimeUnit.MILLISECONDS))
        val a = context.spawn(RunningActor(context.self,link), s"Actor-${link.from.exec.getName}-${link.to.exec.getName}")
        val rsp = Await.result( a.ask { ref => ExecCmdRunningReq(ref) }, timeout.duration )
        // don't watch because RunningActor stops itself
        //context.watch(a)
        relpyTo ! SpawnLinkingRes(rsp.r)
        Behaviors.same
      case StopRuntime() =>
        Behaviors.stopped
    }

    // Behaviors.receiveSignal {
    //   case (_, Terminated(_)) =>
    //     Behaviors.stopped
    // }
  }
}

class RuntimeActors extends Runtime {
  val log = Logger(s"${this}")
  import RuntimeActors._

  def spawn(link: Linking):Try[Running] = {
    implicit val timeout = Timeout(FiniteDuration(100,TimeUnit.MILLISECONDS))
    
    val rsp = Await.result( as.ask { ref => SpawnLinking(link,ref) }, timeout.duration)
    Success(rsp.r)
  }
}

