package io.syspulse.skel.service.telemetry

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import scala.collection.immutable

import scala.jdk.CollectionConverters._

// metrics4
import nl.grons.metrics4.scala.DefaultInstrumented
// prometheus
import fr.davit.akka.http.metrics.prometheus.marshalling.PrometheusMarshallers._
import fr.davit.akka.http.metrics.prometheus.{Buckets, PrometheusRegistry, PrometheusSettings, Quantiles}
import io.prometheus.client.CollectorRegistry

final case class Telemetry(metric:String, value:String)
final case class Telemetries(telemetries: immutable.Seq[Telemetry])

object TelemetryRegistry extends DefaultInstrumented {
  
  sealed trait Command extends io.syspulse.skel.Command

  final case class GetTelemetries(replyTo: ActorRef[Telemetries]) extends Command
  final case class GetTelemetry(key:String,replyTo: ActorRef[GetTelemetryResponse]) extends Command

  final case class GetTelemetryResponse(telemetry: Option[Telemetry])
  
  def apply(): Behavior[Command] = registry(Set.empty)

  private def registry(telemetries: Set[Telemetry]): Behavior[Command] =
    Behaviors.receiveMessage {
      case GetTelemetries(replyTo) =>
        replyTo ! Telemetries( 
            metricRegistry.getGauges.asScala.toSeq.map( kv => Telemetry(kv._1,kv._2.getValue.toString)) ++
            metricRegistry.getCounters.asScala.toSeq.map( kv => Telemetry(kv._1,kv._2.getCount.toString))
          )
        Behaviors.same
      case GetTelemetry(key, replyTo) =>
        val metricGuage = metricRegistry.getGauges.asScala.get(key).flatMap(v => Some(Telemetry(key,v.getValue.toString)))
        val metricCount = metricRegistry.getCounters.asScala.get(key).flatMap(v => Some(Telemetry(key,v.getCount.toString)))
        replyTo ! GetTelemetryResponse(metricGuage.orElse(metricCount))
        Behaviors.same
      
    }

  val prometheusSettings: PrometheusSettings = PrometheusSettings
    .default
    .withIncludePathDimension(true)
    .withIncludeMethodDimension(true)
    .withIncludeStatusDimension(true)
    .withDurationConfig(Buckets(1, 2, 3, 5, 8, 13, 21, 34))
    .withReceivedBytesConfig(Quantiles(0.5, 0.75, 0.9, 0.95, 0.99))
    .withSentBytesConfig(PrometheusSettings.DefaultQuantiles)
    .withDefineError(_.status.isFailure)

  val prometheusCollector: CollectorRegistry = CollectorRegistry.defaultRegistry
  val prometheusRegistry: PrometheusRegistry = PrometheusRegistry(prometheusCollector, prometheusSettings)
}
