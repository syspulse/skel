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

trait ElasticSink extends ElasticClient {

  import io.syspulse.skel.ingest.elastic.MovieElasticJson._
  
  def run(file:String = "/dev/stdin"):ElasticSink = {
  
    val stdinSource: Source[ByteString, Future[IOResult]] = StreamConverters.fromInputStream(() => System.in)
    // this is non-streaming simple ingester for TmsParser. Reads full file, flattens it and parses into Stream of Tms objects
    val fileSource: Source[ByteString, Future[IOResult]] = FileIO.fromPath(Paths.get(file),chunkSize = Files.size(Paths.get(file)).toInt)
    
    val source = fileSource.mapConcat(txt => TmsParser.fromString(txt.utf8String).toSeq)

    val result =
      source
      .viaMat(KillSwitches.single)(Keep.right)
      .map(tms => {
        
        val m = Movie(vid=tms.id, title=tms.title)
      
        println(s"${Util.now}} ${tms}: ${m}")
        WriteMessage.createIndexMessage(m.vid, m)
      })
      .runWith(
        ElasticsearchSink.create[Movie](
          ElasticsearchParams.V7(getIndexName()), settings = getSinkSettings()
        )
      )

    val r = Await.result(result, Duration.Inf)
    println(s"Result: ${r}")
    this
  }

  override def connect(elasticUri:String,elasticIndex:String):ElasticSink = super.connect(elasticUri,elasticIndex).asInstanceOf[ElasticSink]
}