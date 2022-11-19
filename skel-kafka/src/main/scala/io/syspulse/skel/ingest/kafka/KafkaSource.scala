package io.syspulse.skel.ingest.kafka

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.util.ByteString
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

import scala.concurrent.ExecutionContext.Implicits.global 

import io.confluent.kafka.serializers.{AbstractKafkaAvroSerDeConfig, KafkaAvroDeserializer, KafkaAvroSerializer}
import org.apache.avro.specific.SpecificRecord
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.common.serialization._
import io.confluent.kafka.serializers._

import scala.jdk.CollectionConverters._

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.kafka.KafkaClient

trait KafkaSource[T <: skel.Ingestable ] extends KafkaClient {
  
  def source(brokerUri:String, topics:Set[String], groupId:String, 
             pollInterval:FiniteDuration = FiniteDuration(100L,TimeUnit.MILLISECONDS), offset:String="earliest", autoCommit:Boolean=false) = {    
    
    val consumerSettings = ConsumerSettings(system, new ByteArrayDeserializer, new ByteArrayDeserializer)
      .withBootstrapServers(brokerUri)
      .withGroupId(groupId)
      .withPollInterval(pollInterval)
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, offset)
      .withProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, autoCommit.toString)

    log.info(s"Consumer: ${consumerSettings}")

    val s0 = Consumer
      .plainSource(consumerSettings, Subscriptions.topics(topics.asJava))
      .map(record => {
        //if(record.key!=null) new String(record.key) 
        ByteString(record.value)
      })
    
    s0
  }

}