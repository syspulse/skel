package io.syspulse.skel.elastic


import akka.actor.ActorSystem
import akka.stream.scaladsl.Keep
import akka.{Done, NotUsed}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

import akka.stream.ActorMaterializer
import akka.stream._
import akka.stream.scaladsl._

import com.typesafe.scalalogging.Logger

import scala.concurrent.ExecutionContext.Implicits.global

import scala.jdk.CollectionConverters._

import io.syspulse.skel
import akka.http.scaladsl.settings.ConnectionPoolSettings
import java.net.URI
import akka.stream.alpakka.elasticsearch.ElasticsearchConnectionSettings
import akka.stream.alpakka.elasticsearch.ElasticsearchWriteSettings
import akka.stream.alpakka.elasticsearch.RetryAtFixedRate
import akka.stream.alpakka.elasticsearch.ApiVersion
import java.util.concurrent.TimeUnit
import akka.stream.alpakka.elasticsearch.ElasticsearchSourceSettings
import spray.json.JsObject
import spray.json.JsonFormat


trait ElasticClient[T] {
  val log = Logger(s"${this}")
  implicit val as = ActorSystem("ActorSystem-ElasticClient")

  def timeout() = Duration("3 seconds")

  var connectionSettings:Option[ElasticsearchConnectionSettings] = None
  var indexName:Option[String] = None

  private def getElasticClient(elasticUri:String,elasticIndex:String): ElasticsearchConnectionSettings = {
    val connectionSettings = ElasticsearchConnectionSettings(elasticUri)
      //.withCredentials("user", "pass")

    //system.registerOnTermination(client.close())
    connectionSettings
  }

  def getWriteSettings = ElasticsearchWriteSettings(connectionSettings.get).withApiVersion(ApiVersion.V7)

  def getIndexName():String = indexName.get

  def getSourceSettings() = {
    ElasticsearchSourceSettings(connectionSettings.get)
      .withApiVersion(ApiVersion.V7)
  }

  def getSinkSettings() = {
    ElasticsearchWriteSettings(connectionSettings.get)
      .withBufferSize(10)
      .withVersionType("internal")
      .withRetryLogic(RetryAtFixedRate(maxRetries = 5, retryInterval = 1.seconds))
      .withApiVersion(ApiVersion.V7)
  }


  def connect[C <: ElasticClient[T]](elasticUri:String = "http://localhost:9200",elasticIndex:String = "index"):C = {
    connectionSettings = Some(getElasticClient(elasticUri,elasticIndex))
    indexName = Some(elasticIndex)
    log.info(s"-> Elastic(${elasticUri}): index=${elasticIndex}")
    this.asInstanceOf[C]
  }

  implicit val fmt:JsonFormat[T]
  //def convertTo(r:JsObject):T 
}