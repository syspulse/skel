package io.syspulse.skel.service.metrics

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import scala.collection.immutable

import scala.jdk.CollectionConverters._
import nl.grons.metrics4.scala.DefaultInstrumented

final case class Metrics(metric:String, value:String)
final case class Telemetries(telemetries: immutable.Seq[Metrics])

object MetricsRegistry extends DefaultInstrumented {
  
  sealed trait Command extends io.syspulse.skel.Command

  final case class GetTelemetries(replyTo: ActorRef[Telemetries]) extends Command
  final case class GetMetrics(key:String,replyTo: ActorRef[GetMetricsResponse]) extends Command

  final case class GetMetricsResponse(metrics: Option[Metrics])
  
  def apply(): Behavior[Command] = registry(Set.empty)

  private def registry(telemetries: Set[Metrics]): Behavior[Command] =
    Behaviors.receiveMessage {
      case GetTelemetries(replyTo) =>
        replyTo ! Telemetries( 
            metricRegistry.getGauges.asScala.toSeq.map( kv => Metrics(kv._1,kv._2.getValue.toString)) ++
            metricRegistry.getCounters.asScala.toSeq.map( kv => Metrics(kv._1,kv._2.getCount.toString))
          )
        Behaviors.same
      case GetMetrics(key, replyTo) =>
        val metricGuage = metricRegistry.getGauges.asScala.get(key).flatMap(v => Some(Metrics(key,v.getValue.toString)))
        val metricCount = metricRegistry.getCounters.asScala.get(key).flatMap(v => Some(Metrics(key,v.getCount.toString)))
        replyTo ! GetMetricsResponse(metricGuage.orElse(metricCount))
        Behaviors.same
      
    }
}
