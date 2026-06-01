package io.syspulse.skel.pdf.report.store

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import scala.collection.immutable
import com.typesafe.scalalogging.Logger
import scala.concurrent.{Future, ExecutionContext}

import io.jvm.uuid._

import io.syspulse.skel.Command
import io.syspulse.skel.pdf.report._
import scala.util.{Try, Success, Failure}
import akka.actor.typed.scaladsl.ActorContext

object ReportRegistry {
  val log = Logger(s"${this}")

  final case class GetReports(replyTo: ActorRef[Reports]) extends Command
  final case class GetReport(id:UUID,replyTo: ActorRef[Try[Report]]) extends Command
  final case class GetReportByXid(eid:String,replyTo: ActorRef[Option[Report]]) extends Command

  final case class CreateReport(enrollCreate: ReportCreateReq, replyTo: ActorRef[ReportActionRes]) extends Command
  final case class DeleteReport(id: UUID, replyTo: ActorRef[ReportActionRes]) extends Command

  final case class ReportFinished(id: UUID, status:Try[String]) extends Command

  def apply(store: ReportStore = new ReportStoreMem): Behavior[Command] = Behaviors.setup { ctx => {
    val poolActor = ctx.spawn(ReportPool(),"ReportPool")
    registry(store)(poolActor,ctx)
  }}

  private def registry(store: ReportStore)(implicit poolActor:ActorRef[Command],ctx:ActorContext[Command]): Behavior[Command] = {
    implicit val ec: ExecutionContext = ctx.executionContext

    Behaviors.receive { (ctx,msg) =>
      msg match {
        case GetReports(replyTo) =>
          store.all.foreach(reports => replyTo ! Reports(reports))
          Behaviors.same

        case GetReport(id, replyTo) =>
          store.?(id).onComplete(replyTo ! _)
          Behaviors.same

        case GetReportByXid(xid, replyTo) =>
          replyTo ! store.findByXid(xid)
          Behaviors.same


        case CreateReport(reportCreate, replyTo) =>
          val id = UUID.randomUUID()

          val report = Report(id, reportCreate.input, reportCreate.output, reportCreate.name, reportCreate.xid)
          store.+(report)

          // launch
          poolActor ! ReportPool.Start(report,ctx.self)

          // notify user
          replyTo ! ReportActionRes("started",Some(id))
          Behaviors.same

        case DeleteReport(id, replyTo) =>
          Behaviors.same

        case ReportFinished(id, status) =>
          log.info(s"completed: ${id}: status = ${status}")
          store.?(id).foreach { report =>
            val report1 = status match {
              case Success(s) => report.copy(phase = "GENERATED",output = s)
              case Failure(e) => report.copy(phase = s"FAILED: ${e}")
            }
            store.+(report1)
          }
          Behaviors.same
      }
    }
  }
}
