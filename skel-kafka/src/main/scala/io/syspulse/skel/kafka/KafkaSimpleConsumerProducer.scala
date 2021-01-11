package io.syspulse.skel.kafka


import akka.actor.ActorSystem
import akka.kafka._
import akka.kafka.scaladsl.{Committer, Consumer, Producer}
import akka.stream.scaladsl.Keep
import akka.{Done, NotUsed}
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

import io.syspulse.skeleton
import io.syspulse.skeleton.{Configuration,ConfigurationAkka,ConfigurationEnv,Util}

trait KafkaSimpleConsumerProducer extends KafkaClient {
  
  def process(r: ConsumerRecord[Array[Byte], Array[Byte]]): (Array[Byte], Array[Byte]) = (r.key(),r.value())

  def run(topics1:Set[String], topic2:String, brokerUri:String, groupId:String, pollInterval:FiniteDuration,offset:String="earliest",autoCommit:Boolean=false) = {
    
    val consumerSettings = ConsumerSettings(system, 
      new ByteArrayDeserializer, new ByteArrayDeserializer)
      .withBootstrapServers(brokerUri)
      .withGroupId(groupId)
      .withPollInterval(pollInterval)
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, offset)
      .withProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,autoCommit.toString)  

    val producerSettings = ProducerSettings(system, 
      new ByteArraySerializer, new ByteArraySerializer).
      withBootstrapServers(brokerUri)

    val sink = Producer.plainSink(producerSettings)

    println(s"Consumer: ${consumerSettings}\nProducer: ${producerSettings}")

    val (control, result) =
    Consumer
      .plainSource(consumerSettings, Subscriptions.topics(topics1.asJava))
      .map(record => {println(s"${Util.now}: <-: ${record.offset}: ${if(record.key!=null) new String(record.key) else "null"} ${new String(record.value)}"); record})
      .map(record => process(record))
      .map(kv => new ProducerRecord[Array[Byte], Array[Byte]](topic2, kv._1,kv._2))
      .viaMat(KillSwitches.single)(Keep.right)
      .map(record => {println(s"${Util.now}: ->: ${if(record.key!=null) new String(record.key) else "null"} ${new String(record.value)}"); record})
      .toMat(sink)(Keep.both)
      .run()

    val r = Await.result(result, Duration.Inf)
    println(r)
  }

}