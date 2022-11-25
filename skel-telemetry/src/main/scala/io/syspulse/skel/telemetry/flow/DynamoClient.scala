package io.syspulse.skel.telemetry.store

import java.net.URI

import scala.jdk.CollectionConverters._
import com.typesafe.scalalogging.Logger

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{Duration,FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future}

import akka.actor.ActorSystem
import akka.stream.scaladsl.Keep
import akka.{Done, NotUsed}
import akka.stream.ActorMaterializer
import akka.stream._
import akka.stream.scaladsl._

import akka.http.scaladsl.settings.ConnectionPoolSettings
import com.github.matsluni.akkahttpspi.AkkaHttpClient

import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider,DefaultCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient

import io.syspulse.skel.ingest.uri.DynamoURI

abstract class DynamoClient(dynamoUri:DynamoURI) {
  val log = Logger(s"${this}")
  
  implicit val system = ActorSystem("ActorSystem-DynamoClient")

  private val credentialsProvider = DefaultCredentialsProvider.create //StaticCredentialsProvider.create(AwsBasicCredentials.create("x", "x"))

  private def getDynamoClient(uri:String): DynamoDbAsyncClient = {
    val client = DynamoDbAsyncClient
    .builder()
    .region(Region.AWS_GLOBAL)
    .credentialsProvider(credentialsProvider)
    .httpClient(
      AkkaHttpClient.builder()
        .withActorSystem(system)
        .build()
    )
    .endpointOverride(new URI(uri))
    // Possibility to configure the retry policy
    // see https://doc.akka.io/docs/alpakka/current/aws-shared-configuration.html
    // .overrideConfiguration(...)
    .build()

    system.registerOnTermination(client.close())
    client
  }

  def getTable():String = tableName

  // cannot use Option because of implicit
  implicit var dynamoClient: DynamoDbAsyncClient = getDynamoClient(dynamoUri.uri)
  val tableName:String = dynamoUri.table  
}