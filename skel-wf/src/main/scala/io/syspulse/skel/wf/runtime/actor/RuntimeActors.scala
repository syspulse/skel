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
import akka.NotUsed
import akka.actor.typed.Terminated
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.ActorRef

trait RuntimeCmd
final case class StopRuntime() extends RuntimeCmd
final case class SpawnLinking(ra:RunningActor) extends RuntimeCmd

class RunningActor(link:Linking) extends Running {
  val log = Logger(s"${this}")

  def getLinking = link
  @volatile
  var actor:Option[ActorRef[ExecEvent]] = None
  val failure = Failure(new Exception(s"actor is not bound"))

  def setActor(actor:ActorRef[ExecEvent]) = {
    this.actor = Some(actor)
  }

  override def !(e: ExecEvent):Try[RunningActor] = {
    actor match {
      case Some(a) =>
        a ! e 
        Success(this)
      case None =>
        log.error(s"${failure}")
        failure
    }
  }

  def start():Try[RunningActor] = {
    actor match {
      case Some(a) =>
        Success(this)
      case None =>
        log.error(s"${failure}")
        failure
    }
  }

  def stop():Try[RunningActor] = {
    actor match {
      case Some(a) =>
        // send to self
        a ! ExecCmdStop()
        Success(this)
      case None =>
        log.error(s"${failure}")
        failure
    }
  }
}

object RunningActor {
  
  def apply(master:ActorRef[RuntimeCmd],ra:RunningActor): Behavior[ExecEvent] =
    Behaviors.setup(context => {
      val actor = context.self
      ra.setActor(actor)
      new RunningBehavior(ra,context)
    })

  class RunningBehavior(ra:RunningActor,context: ActorContext[ExecEvent]) extends AbstractBehavior[ExecEvent](context) {
    val log = Logger(s"${this}-${ra}")
    
    override def onMessage(message: ExecEvent): Behavior[ExecEvent] = {
      message match {
        case ExecCmdStop() =>
          log.info(s"stopping: actor=${context.self}")
          Behaviors.stopped  
        case e:ExecEvent =>
          ra.getLinking.output(e)
          this
      }
    }
  }
}

// ---------------------------------------------------------- Runtime ------------------
object RuntimeActors {
  
  def apply(): Behavior[RuntimeCmd] = Behaviors.setup { context =>    
    Behaviors.receiveMessage {
      case SpawnLinking(ra) =>
        val a = context.spawn(RunningActor(context.self,ra), s"Actor-${ra}")
        // don't watch because RunningActor stops itself
        //context.watch(a)
        
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

  val as = ActorSystem(RuntimeActors(), "RuntimeActors")

  def spawn(link: Linking):Try[Running] = {
    val ra = new RunningActor(link)
    
    as ! SpawnLinking(ra)

    Success(ra)
  }
}

