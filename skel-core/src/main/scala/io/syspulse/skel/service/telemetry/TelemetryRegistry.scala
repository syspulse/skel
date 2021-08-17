package io.syspulse.skel.service.telemetry

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import scala.collection.immutable

import scala.jdk.CollectionConverters._

// prometheus
import fr.davit.akka.http.metrics.prometheus.marshalling.PrometheusMarshallers._
import fr.davit.akka.http.metrics.prometheus.{Buckets, PrometheusRegistry, PrometheusSettings, Quantiles}
import io.prometheus.client.CollectorRegistry

final case class Telemetry(metric:String, value:String)
final case class Telemetries(telemetries: immutable.Seq[Telemetry])

object TelemetryRegistry {
  
  sealed trait Command extends io.syspulse.skel.Command
  final case class GetTelemetryResponse(telemetry: Option[Telemetry])
  
  def apply(): Behavior[Command] = registry(Set.empty)
  private def registry(telemetries: Set[Telemetry]): Behavior[Command] = 
    Behaviors.receiveMessage {
      case _ => Behaviors.same
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
