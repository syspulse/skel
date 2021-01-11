package io.syspulse.skel.kafka


import akka.actor.ActorSystem
import akka.kafka._
import akka.kafka.scaladsl.{Committer, Consumer}
import akka.stream.scaladsl.Keep
import akka.{Done, NotUsed}
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization._

import scala.concurrent.duration.{Duration,FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future}

import akka.stream.ActorMaterializer
import akka.stream._
import akka.stream.scaladsl._

import com.typesafe.scalalogging.Logger

import scala.concurrent.ExecutionContext.Implicits.global

import io.confluent.kafka.serializers.{AbstractKafkaAvroSerDeConfig, KafkaAvroDeserializer, KafkaAvroSerializer}
import org.apache.avro.specific.SpecificRecord
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.common.serialization._
import io.confluent.kafka.serializers._

import scala.jdk.CollectionConverters._

import io.syspulse.skel
import io.syspulse.skel.{Configuration,ConfigurationAkka,ConfigurationEnv}

trait KafkaClient {
  val log = Logger(s"${this}")

  implicit val system = ActorSystem("ActorSystem-KafkaClient")

}