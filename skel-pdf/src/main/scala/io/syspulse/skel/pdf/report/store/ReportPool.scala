package io.syspulse.skel.pdf.report.store

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import scala.collection.immutable
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.Command
import io.syspulse.skel.pdf.report._
import io.syspulse.skel.pdf.ReportGenerator

object ReportPool {
  val log = Logger(s"${this}")
  
  final case class Start(report:Report,replyTo: ActorRef[Command]) extends Command
  
  def apply(templateDir:String = "templates/"): Behavior[Command] = {
    Behaviors.receive { (ctx,msg) => { 
      implicit val ec = ctx.executionContext
      
      msg match {
        case Start(report,replyTo) =>

          val outputFile = report.output match {
            case "" => s"output-${report.id.toString}"
            case s => report.output.replaceAll("\\{ID\\}",report.id.toString)
          }
          val rg = new ReportGenerator(templateDir + report.template, report.input, outputFile)
          val r = rg.generate()

          replyTo ! ReportRegistry.ReportFinished(report.id,r)
          Behaviors.same
      }
    }}
  }
}
