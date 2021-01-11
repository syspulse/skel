package io.syspulse.skel.kafka


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
import io.syspulse.skel.{Configuration,ConfigurationAkka,ConfigurationEnv,Util}

trait KafkaSimpleProducer extends KafkaClient {
  
  def run(topics:Set[String], brokerUri:String, autoCommit:Boolean=false) = {
    
    val producerSettings = ProducerSettings(system, 
      new ByteArraySerializer, new ByteArraySerializer).
      withBootstrapServers(brokerUri)

    println(s"Producer: ${producerSettings}")

    val stdinSource: Source[ByteString, Future[IOResult]] = StreamConverters.fromInputStream(() => System.in)
    val sink = Producer.plainSink(producerSettings)

    val (control, result) =
      stdinSource
      .map(line => new ProducerRecord[Array[Byte], Array[Byte]](topics.head, null, line.utf8String.getBytes()))
      .viaMat(KillSwitches.single)(Keep.right)
      .map(record => {println(s"${Util.now}: ->: ${if(record.key!=null) new String(record.key) else "null"} ${new String(record.value)}"); record})
      .toMat(sink)(Keep.both)
      .run()

    val r = Await.result(result, Duration.Inf)
    println(s"Result: ${r}")
  }

}