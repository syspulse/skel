package io.syspulse.skel.ingest.dynamo

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

import com.github.matsluni.akkahttpspi.AkkaHttpClient
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient

import scala.jdk.CollectionConverters._

import io.syspulse.skel
import io.syspulse.skel.util.Util
import akka.stream.alpakka.dynamodb.scaladsl.DynamoDb
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import scala.util.Random
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest
import software.amazon.awssdk.services.dynamodb.model.QueryRequest
import software.amazon.awssdk.services.dynamodb.model.ScanRequest

trait DynamoGet extends DynamoClient {
  
  def get(key:String):DynamoGet = {
    log.info(s"key=${key}")
    val req = GetItemRequest
          .builder()
          .key(
            Map(
              "VID"-> AttributeValue.builder.s(key).build(),
              "TS" -> AttributeValue.builder.n("0".toString).build(),
            ).asJava
          )
          .tableName(getTable())
          .build()
          
    val result = DynamoDb.single(req)
  
    val r = Await.result(result, Duration.Inf)
    println(s"Result: ${r}")
    
    val m = MovieDynamo.fromDynamo(r.item().asScala.toMap)
    println(s"${m}")
    this
  }

  def query(q:String):DynamoGet = {
    log.info(s"q=${q}")
    val req = QueryRequest
          .builder()
          .tableName(getTable())
          .keyConditionExpression("VID = :vid")
          .expressionAttributeValues(Map(":vid" -> AttributeValue.builder().s(q).build()).asJava)
          //.attributesToGet("VID","TS","TITLE")
          .build()
          
    val result = DynamoDb.single(req)
  
    val r = Await.result(result, Duration.Inf)
    println(s"Result: ${r}")
    
    val mm = r.items().asScala.map( r => MovieDynamo.fromDynamo(r.asScala.toMap))
    mm.foreach{m => println(s"${m}")}
    this
  }

  def scan(limit:Int = -1):DynamoGet = {
    log.info(s"limit=${limit}")
    val req = ScanRequest
          .builder()
          .tableName(getTable())
          .limit(if(limit == -1) Int.MaxValue else limit)
          .build()
          
    val result = DynamoDb.single(req)
  
    val r = Await.result(result, Duration.Inf)
    println(s"Result: ${r}")

    val mm = r.items().asScala.map( r => MovieDynamo.fromDynamo(r.asScala.toMap))
    mm.foreach{m => println(s"${m}")}
    this
  }

  override def connect(dynamoUri:String,dynamoTable:String):DynamoGet = super.connect(dynamoUri,dynamoTable).asInstanceOf[DynamoGet]

}