package io.syspulse.skel.ingest.kafka


import akka.actor.ActorSystem
import akka.kafka._
import akka.kafka.scaladsl.{Committer, Consumer, Producer}
import akka.stream.scaladsl.Keep
import akka.{Done, NotUsed}
import akka.util.ByteString
import org.apache.kafka.clients.consumer.ConsumerConfig

import org.apache.kafka.common.serialization._

import scala.concurrent.duration.{Duration,FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future}

import akka.stream.ActorMaterializer
import akka.stream._
import akka.stream.scaladsl._

import scala.concurrent.ExecutionContext.Implicits.global 

import io.confluent.kafka.serializers.{AbstractKafkaAvroSerDeConfig, KafkaAvroDeserializer, KafkaAvroSerializer}
import org.apache.avro.specific.SpecificRecord
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization._
import io.confluent.kafka.serializers._

import scala.jdk.CollectionConverters._

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.kafka.KafkaClient

trait KafkaSink[T] extends KafkaClient {
  
  def transform(t:T):ByteString

  def sink(brokerUri:String, topics:Set[String], autoCommit:Boolean=false) = {
    
    val producerSettings = ProducerSettings(system, new ByteArraySerializer, new ByteArraySerializer)
      .withBootstrapServers(brokerUri)
      .withProperty("reconnect.backoff.ms","3000")
      .withProperty("reconnect.backoff.max.ms","10000")
      

    log.info(s"Producer: ${producerSettings}")

    val s0 = Producer.plainSink(producerSettings)

    val s1 =
      Flow[T]
        .map( t => transform(t) )
        .map(d => new ProducerRecord[Array[Byte], Array[Byte]](topics.head, null, d.utf8String.getBytes()))
        .viaMat(KillSwitches.single)(Keep.right)
        .toMat(s0)(Keep.both)
    s1    
  }

}