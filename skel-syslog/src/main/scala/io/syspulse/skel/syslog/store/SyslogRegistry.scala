package io.syspulse.skel.syslog.store

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import scala.collection.immutable
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.Command

import io.syspulse.skel.syslog._
import io.syspulse.skel.syslog.Syslog.ID
import scala.util.{Try, Success, Failure}
import scala.concurrent.{Future, ExecutionContext}

object SyslogRegistry {
  val log = Logger(s"${this}")

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  val bus = new SyslogBus(busId = "syslog") {
    override def recv(msg:SyslogEvent):SyslogEvent = {
      log.info(s"event=${msg}")
      msg
    }
  }

  final case class GetSyslogs(replyTo: ActorRef[Syslogs]) extends Command
  final case class GetSyslog(id:ID,replyTo: ActorRef[Try[Syslog]]) extends Command
  final case class SearchSyslog(txt:String,replyTo: ActorRef[Syslogs]) extends Command

  final case class CreateSyslog(req: SyslogCreateReq, replyTo: ActorRef[Syslog]) extends Command
  final case class RandomSyslog(replyTo: ActorRef[Syslog]) extends Command

  final case class DeleteSyslog(id: ID, replyTo: ActorRef[SyslogActionRes]) extends Command

  // this var reference is unfortunately needed for Metrics access
  var store: SyslogStore = null //new SyslogStoreDB //new SyslogStoreCache

  def apply(store: SyslogStore = new SyslogStoreMem): Behavior[io.syspulse.skel.Command] = {
    this.store = store
    registry(store)
  }

  private def registry(store: SyslogStore): Behavior[io.syspulse.skel.Command] = {
    this.store = store

    Behaviors.receiveMessage {
      case GetSyslogs(replyTo) =>
        store.all.flatMap { syslogs =>
          store.size.map { sz =>
            replyTo ! Syslogs(syslogs, sz)
          }
        }
        Behaviors.same

      case GetSyslog(id, replyTo) =>
        store.?(id).onComplete(replyTo ! _)
        Behaviors.same

      case SearchSyslog(txt, replyTo) =>
        val ss = store.??(txt)
        replyTo ! Syslogs(ss, ss.size.toLong)
        Behaviors.same

      case CreateSyslog(req, replyTo) =>
        val syslog = Syslog(
          msg = req.msg,
          severity = req.severity,
          scope = req.scope,
          from = req.from,
          id = Some(UUID.random),
          cid = req.cid,
          ts = System.currentTimeMillis,
          subj =  req.subj,
        )

        store.+(syslog)

        replyTo ! syslog
        Behaviors.same

      case RandomSyslog(replyTo) =>

        //replyTo ! SyslogRandomRes(secret,qrImage)
        Behaviors.same

      case DeleteSyslog(id, replyTo) =>
        store.del(id)
        replyTo ! SyslogActionRes(s"Success",Some(id))
        Behaviors.same
    }
  }
}
