package io.syspulse.skel.elastic

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
import spray.json.JsonFormat

trait ElasticSink[T] extends ElasticClient[T] {
  
  def getIndex(d:T):(String,T)

  def parse(data:String):Seq[T]

  def run(file:String = "/dev/stdin"):Future[Done] = {
  
    val stdinSource: Source[ByteString, Future[IOResult]] = StreamConverters.fromInputStream(() => System.in)
    // this is non-streaming simple ingester for TmsParser. Reads full file, flattens it and parses into Stream of Tms objects
    val fileSource: Source[ByteString, Future[IOResult]] = FileIO.fromPath(Paths.get(file),chunkSize = Files.size(Paths.get(file)).toInt)
    
    val source = fileSource.mapConcat(txt => parse(txt.utf8String))

    val flow =
      source
      .map( data => { log.debug(s"data=${data}"); data})
      .viaMat(KillSwitches.single)(Keep.right)
      .map(data => {
        
        log.info(s"${Util.now}} ${data}")
        
        val (index,d) = getIndex(data)
        WriteMessage.createIndexMessage(index, d)
      })
      .runWith(
        ElasticsearchSink.create[T](
          ElasticsearchParams.V7(getIndexName()), settings = getSinkSettings()
        )
      )

    //val r = Await.result(result, timeout())
    log.info(s"flow: ${flow}")
    flow
  }

  override def connect(elasticUri:String,elasticIndex:String):ElasticSink[T] = super.connect(elasticUri,elasticIndex).asInstanceOf[ElasticSink[T]]
}