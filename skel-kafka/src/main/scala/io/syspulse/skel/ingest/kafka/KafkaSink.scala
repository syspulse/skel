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
import java.util.concurrent.TimeUnit

// TODO: Investigate, wtf !?
//  [2024-02-24 02:51:33,475] [INFO] [o.a.k.c.p.KafkaProducer KafkaProducer.java:1183] [Producer clientId=producer-1] 
//  Closing the Kafka producer with timeoutMillis = 0 ms.                                                                              │
// │ [2024-02-24 02:51:33,476] [INFO] [o.a.k.c.p.KafkaProducer KafkaProducer.java:1209] [Producer clientId=producer-1] 
// Proceeding to force close the producer since pending requests could not be completed within timeout 0 ms.                          │
// │ [2024-02-24 02:51:33,484] [INFO] [o.a.k.c.p.KafkaProducer KafkaProducer.java:1183] [Producer clientId=producer-1] 
// Closing the Kafka producer with timeoutMillis = 60000 ms.


// [2024-10-28 13:42:01,359] [INFO] [o.a.k.c.p.KafkaProducer KafkaProducer.java:1183] [Producer clientId=producer-1] Closing the Kafka producer with timeoutMillis = 60000 ms.
// [2024-10-28 13:42:01,360] [DEBUG] [o.a.k.c.p.i.Sender Sender.java:250] [Producer clientId=producer-1] Beginning shutdown of Kafka producer I/O thread, sending remaining records.
// [2024-10-28 13:42:01,367] [DEBUG] [o.a.k.c.p.i.Sender Sender.java:292] [Producer clientId=producer-1] Shutdown of Kafka producer I/O thread has completed.
// [2024-10-28 13:42:01,368] [DEBUG] [o.a.k.c.p.KafkaProducer KafkaProducer.java:1235] [Producer clientId=producer-1] Kafka producer has been closed

// https://doc.akka.io/docs/alpakka-kafka/current/producer.html


// [2025-06-26 12:53:04,921] [INFO] [ActorSystem-IngestFlow-akka.actor.default-dispatcher-6] [i.s.h.i.e.f.r.PipelineTxETL@3dedb4a6 PipelineRpcTxETL.scala:89] Block[52124842,314,314,4627,283,31,297978]                                                                                                    
// [2025-06-26 12:53:04,950] [INFO] [ActorSystem-IngestFlow-akka.kafka.default-dispatcher-18] [o.a.k.c.p.KafkaProducer KafkaProducer.java:1183] [Producer clientId=producer-1] Closing the Kafka producer with timeoutMillis = 0 ms.                                                                        
// [2025-06-26 12:53:04,950] [INFO] [ActorSystem-IngestFlow-akka.kafka.default-dispatcher-18] [o.a.k.c.p.KafkaProducer KafkaProducer.java:1209] [Producer clientId=producer-1] Proceeding to force close the producer since pending requests could not be completed within timeout 0 ms.                    
// [2025-06-26 12:53:04,957] [INFO] [ActorSystem-IngestFlow-akka.kafka.default-dispatcher-18] [o.a.k.c.p.KafkaProducer KafkaProducer.java:1183] [Producer clientId=producer-1] Closing the Kafka producer with timeoutMillis = 7000 ms.                                                                     
// [2025-06-26 12:53:04,965] [WARN] [ActorSystem-IngestFlow-akka.actor.default-dispatcher-6] [a.s.Materializer Slf4jLogger.scala:92] [KafkaSink] Downstream finished, cause: RecordTooLargeException: The message is 1286639 bytes when serialized which is larger than the maximum request size you have co
// nfigured with the max.request.size configuration.                                                                                                                                                                                                                                                        
// [2025-06-26 12:53:05,363] [INFO] [ActorSystem-IngestFlow-akka.actor.default-dispatcher-6] [i.s.h.i.e.f.r.PipelineTxETL@3dedb4a6 PipelineRPC.scala:222] last=52124843, current=52124843, lag=0, reorg=0                                                                                                   
// [2025-06-26 12:53:05,363] [INFO] [ActorSystem-IngestFlow-akka.actor.default-dispatcher-6] [i.s.h.i.e.f.r.PipelineTxETL@3dedb4a6 PipelineRPC.scala:273] --> Vector(52124843)

trait KafkaSink[T] extends KafkaClient {
  
  def transform(t:T):ByteString

  def sink(brokerUri:String, topics:Set[String], ops:Map[String,String]=Map.empty) = {
    
    val producerSettings = ProducerSettings(system, new ByteArraySerializer, new ByteArraySerializer)
      .withBootstrapServers(brokerUri)
      .withProperty("reconnect.backoff.ms","3000")
      .withProperty("reconnect.backoff.max.ms","10000") 
      .withCloseProducerOnStop(ops.getOrElse("closeProducerOnStop", "true").toBoolean)
      .withCloseTimeout(FiniteDuration(ops.getOrElse("closeTimeout", "7000").toInt,TimeUnit.MILLISECONDS))
      // override with user properties
      .withProperties(ops.asJava)

    log.info(s"Producer: ${producerSettings}")    

    val s0 = Producer.plainSink(producerSettings)

    val s1 =
      Flow[T]
        .map( t => transform(t) )
        .map(d => new ProducerRecord[Array[Byte], Array[Byte]](topics.head, null, d.toArray))
        .log("KafkaSink")
        .addAttributes(
          Attributes.logLevels(
            onElement = Attributes.LogLevels.Off,
            onFinish = Attributes.LogLevels.Warning,
            onFailure = Attributes.LogLevels.Error)
        )
        //.viaMat(KillSwitches.single)(Keep.right)
        .toMat(s0)(Keep.both)
    s1    
  }
}