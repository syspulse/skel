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
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.admin.AdminClient

//trait KafkaSource[T <: skel.Ingestable ] extends KafkaClient {
trait KafkaSource[T] extends KafkaClient {
  
  def source(brokerUri:String, topics:Set[String], groupId:String, 
             pollInterval:FiniteDuration = FiniteDuration(100L,TimeUnit.MILLISECONDS), offset:String="earliest", autoCommit:Boolean=true) = {    
    
    val (offsetKafka,autoKafka,reset) = offset match {
      case "oldest" => ("earliest",autoCommit,true)
      case "youngest" => ("latest",autoCommit,true)

      case "earliest_noauto" | "start" => ("earliest",false,false)
      case "latest_noauto"   | "end" => ("latest",false,false)

      case "START" => ("earliest",false,true)
      case "END"   => ("latest",false,true)
      
      case _ => (offset,autoCommit,false)
    }

    val consumerSettings = ConsumerSettings(system, new ByteArrayDeserializer, new ByteArrayDeserializer)
      .withBootstrapServers(brokerUri)
      .withGroupId(groupId)
      .withPollInterval(pollInterval)
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, offsetKafka)
      .withProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, autoKafka.toString)
      .withProperty("reconnect.backoff.ms","3000")
      .withProperty("reconnect.backoff.max.ms","10000")

    log.info(s"Consumer: ${consumerSettings}")
    
    val s0 = if(reset) {
      // resetting Offsets
      // get all partitions         
      val props = new java.util.Properties()
      props.put("bootstrap.servers", brokerUri);
      val client:AdminClient = AdminClient.create(props);        
      val result = client.describeTopics(topics.toList.asJavaCollection)
      val topicDescriptions = result.values()
      val partitions = topicDescriptions.asScala.map{ case(name,desc) =>
        
        val topicDescription = topicDescriptions.get(name)
        val partitions = topicDescription.get().partitions().size()

        (name,partitions)
      }
      log.info(s"Partitions: ${partitions}")
      // close admin client
      client.close()

      Consumer
      .plainSource(consumerSettings, 
        Subscriptions.assignmentWithOffset(
          partitions.flatMap{ case(name,size) => {
            Range(0,size).map(r => new TopicPartition(name, r) -> 0L)
          }}.toMap
        )        
      )
      .map(record => {
        //if(record.key!=null) new String(record.key) 
        ByteString(record.value)
      })        
    } else
      Consumer
      .plainSource(consumerSettings, Subscriptions.topics(topics.asJava))
      .map(record => {
        //if(record.key!=null) new String(record.key) 
        ByteString(record.value)
      })
    
    s0
  }

}