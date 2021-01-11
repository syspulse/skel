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

import scala.concurrent.ExecutionContext.Implicits.global 

import io.confluent.kafka.serializers.{AbstractKafkaAvroSerDeConfig, KafkaAvroDeserializer, KafkaAvroSerializer}
import org.apache.avro.specific.SpecificRecord
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.common.serialization._
import io.confluent.kafka.serializers._

import scala.jdk.CollectionConverters._

import io.syspulse.skeleton
import io.syspulse.skeleton.{Configuration,ConfigurationAkka,ConfigurationEnv,Util}

trait KafkaAvroConsumer extends KafkaClient {
  
  def run(topics:Set[String], schemaRegUri:String, brokerUri:String, groupId:String, pollInterval:FiniteDuration,offset:String="earliest",autoCommit:Boolean=false) = {
    //implicit val system = ActorSystem("ActorSystem-KafkaConsumer")
    
    val kafkaAvroDeserializerConfig = collection.mutable.HashMap[String, Any] {
      AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG -> schemaRegUri
    }

    val kafkaAvroDeserializer = new KafkaAvroDeserializer
    kafkaAvroDeserializer.configure(kafkaAvroDeserializerConfig.asJava, false)

    val consumerSettings = ConsumerSettings(system, new StringDeserializer,
      kafkaAvroDeserializer.asInstanceOf[Deserializer[GenericRecord]])
      .withBootstrapServers(brokerUri)
      .withGroupId(groupId)
      .withPollInterval(pollInterval)
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,offset)
      .withProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, autoCommit.toString )

    val (control, result) =
    Consumer
      .plainSource(consumerSettings, Subscriptions.topics(topics.asJava))
      .map(record => {println(s"${Util.now}: <- ${record.offset}: ${record.key}, ${record.value}"); record})
      .toMat(Sink.ignore)(Keep.both)
      .run()

    //println(result.value.asInstanceOf[scala.util.Failure[_]].exception.getStackTrace.mkString("\n"))
    val r = Await.result(result, Duration.Inf)
    println(r)
    // Console.in.read()
  }

}