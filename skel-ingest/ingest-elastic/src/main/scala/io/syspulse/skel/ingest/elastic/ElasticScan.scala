package io.syspulse.skel.ingest.elastic

import scala.jdk.CollectionConverters._

import akka.actor.ActorSystem
import akka.stream.scaladsl.Keep
import akka.{Done, NotUsed}
import akka.util.ByteString

import scala.concurrent.duration.{Duration,FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future}

import akka.stream.ActorMaterializer
import akka.stream._
import akka.stream.scaladsl._

import scala.concurrent.ExecutionContext.Implicits.global 
import scala.util.Random
import java.nio.file.{Paths,Files}
import scala.jdk.CollectionConverters._

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.video._
import akka.stream.alpakka.elasticsearch.scaladsl.ElasticsearchSink
import akka.stream.alpakka.elasticsearch.WriteMessage
import akka.stream.alpakka.elasticsearch.ElasticsearchParams
import akka.stream.alpakka.elasticsearch.scaladsl.ElasticsearchSource

trait ElasticScan extends ElasticClient {

  import io.syspulse.skel.ingest.elastic.MovieElasticJson._
  
  def scan(txt:String):ElasticScan = {
    val result = ElasticsearchSource.typed[Movie](
        ElasticsearchParams.V7(getIndexName()),
        settings = getSourceSettings(),
        searchParams = Map(
          "query" -> s""" {"match_all": {}} """,
          "_source" -> """ ["vid", "ts", "title", "category"] """
        )
      )
      .map { m =>
        m.source
      }
      .runWith(Sink.seq)

    val r = Await.result(result, Duration.Inf)
    println(s"Result: ${r.mkString("\n")}")
    this
  }

  override def connect(elasticUri:String,elasticIndex:String):ElasticScan = super.connect(elasticUri,elasticIndex).asInstanceOf[ElasticScan]
}