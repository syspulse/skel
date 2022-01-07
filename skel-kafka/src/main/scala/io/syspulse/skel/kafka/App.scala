package io.syspulse.skel.kafka

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.{Duration,FiniteDuration}

import scopt.OParser

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.config.{Configuration,ConfigurationAkka,ConfigurationEnv}


case class Config(
  client:String = "",
  topicsInput:String = "",
  topicsOutput:String = "",
  schemaRegUri:String = "",
  brokerUri:String = "",
  groupId:String = "",
  pollInterval: Long = 0L,
  offset:String = "",
  autoCommit:String = "",
)


object App {
  
  def main(args:Array[String]): Unit = {
    println(s"Args: '${args.mkString(",")}'")

    val builder = OParser.builder[Config]
    val argsParser = {
      import builder._
      OParser.sequence(
        programName(Util.info._1), head(Util.info._1, Util.info._2),
        opt[String]('t', "topics").action((x, c) => c.copy(topicsInput = x)).text("List of Input topics (topic-1,topic-2,...)"),
        opt[String]('o', "output").action((x, c) => c.copy(topicsOutput = x)).text("List of Output topics (topic-3,topic-4,...)"),
        opt[String]('r', "schema").action((x, c) => c.copy(schemaRegUri = x)).text("SchemaRegistry URI (http://address:port)"),
        opt[String]('b', "brocker").action((x, c) => c.copy(brokerUri = x)).text("Broker URI (host:port)"),
        opt[String]('g', "group").action((x, c) => c.copy(groupId = x)).text("Consumer Group ID"),
        opt[Long]('p', "poll").action((x, c) => c.copy(pollInterval = x)).text("Poll interval (millisec)"),
        opt[String]("offset").action((x, c) => c.copy(offset = x)).text("Start offset (earliest,latest)"),
        opt[String]("autocommit").action((x, c) => c.copy(autoCommit = x)).text("Autocommit (true/false)"),
        cmd("consumer")
          .action( (_, c) => c.copy(client = "consumer") ).text("Simple Consumer.").children(),
        cmd("consumer-avro")
          .action( (_, c) => c.copy(client = "consumer-avro") ).text("Avro Consumer.").children(),
        cmd("proxy")
          .action( (_, c) => c.copy(client = "proxy") ).text("Simple Consumer -> Producer").children(),
        cmd("producer")
          .action( (_, c) => c.copy(client = "producer") ).text("Simple Producer").children()
      )
    } 
  
    OParser.parse(argsParser, args, Config()) match {
      case Some(configArgs) => {
        val configuration = Configuration.default

        val config = Config(
          client = { if(! configArgs.client.isEmpty) configArgs.client else configuration.getString("kafka.client").getOrElse("simple") },
          topicsInput = { if(! configArgs.topicsInput.isEmpty) configArgs.topicsInput else configuration.getString("kafka.topics.input").getOrElse("topic-1") },
          topicsOutput = { if(! configArgs.topicsOutput.isEmpty) configArgs.topicsOutput else configuration.getString("kafka.topics.output").getOrElse("") },
          schemaRegUri = { if(! configArgs.schemaRegUri.isEmpty) configArgs.schemaRegUri else configuration.getString("kafka.schema-reg.uri").getOrElse("http://localhost:8081") },
          brokerUri = { if( ! configArgs.brokerUri.isEmpty) configArgs.brokerUri else configuration.getString("kafka.broker.uri").getOrElse("localhost:9092") },
          groupId = { if( ! configArgs.groupId.isEmpty) configArgs.groupId else configuration.getString("kafka.group-id").getOrElse("group-1") },
          pollInterval = { if( configArgs.pollInterval != 0) configArgs.pollInterval else configuration.getInt("kafka.poll-interval").getOrElse(100).toLong },

          offset = { if( ! configArgs.offset.isEmpty) configArgs.offset else configuration.getString("kafka.offset").getOrElse("earliest") },
          autoCommit = { if( ! configArgs.groupId.isEmpty) configArgs.autoCommit else configuration.getString("kafka.group-id").getOrElse("false") },
        )

        println(s"Config: ${config}")

        config.client match {
          case "consumer" => (new Object with KafkaSimpleConsumer).run( config.topicsInput.split(",").toSet, config.brokerUri, config.groupId, Duration(config.pollInterval,"ms"),config.offset,config.autoCommit.toBoolean)
          case "consumer-avro" =>  (new Object with KafkaAvroConsumer).run( config.topicsInput.split(",").toSet,config.schemaRegUri, config.brokerUri, config.groupId, Duration(config.pollInterval,"ms"),config.offset,config.autoCommit.toBoolean)
          case "proxy" => (new Object with KafkaSimpleConsumerProducer).run( config.topicsInput.split(",").toSet, config.topicsOutput, config.brokerUri, config.groupId, Duration(config.pollInterval,"ms"),config.offset,config.autoCommit.toBoolean)
          case "producer" => (new Object with KafkaSimpleProducer).run( config.topicsInput.split(",").toSet, config.brokerUri, config.autoCommit.toBoolean)
        
        }
      }
      case _ => 
    }
  }
}