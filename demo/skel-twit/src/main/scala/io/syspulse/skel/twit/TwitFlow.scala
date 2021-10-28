package io.syspulse.skel.twit

// inspired by: https://github.com/Anant/example-cassandra-alpakka-twitter

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Success, Failure, Random}

import akka.actor.typed.ActorRef
import akka.actor.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.ActorMaterializer
import akka.stream.OverflowStrategy

import akka.stream.alpakka.cassandra.CassandraSessionSettings
import akka.stream.alpakka.cassandra.scaladsl.CassandraSession
import akka.stream.alpakka.cassandra.scaladsl.CassandraSessionRegistry
import akka.stream.alpakka.cassandra.CassandraWriteSettings
import akka.stream.alpakka.cassandra.scaladsl.CassandraFlow
import akka.stream.alpakka.cassandra.scaladsl.CassandraSource
import com.datastax.oss.driver.api.core.cql.{BoundStatement, PreparedStatement}

import com.danielasfregola.twitter4s.TwitterStreamingClient
import com.danielasfregola.twitter4s.entities.Tweet

import com.typesafe.scalalogging.Logger
import akka.NotUsed
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit

class TwitFlow(users:Map[Long,String],cassandraHosts:String, keySpace:String="twit_space",table:String="twit",queueLimit:Long = 100, scrapUsers:Seq[String] = Seq(),scrapDir:String="/tmp") {

  val log = Logger(s"${this}")

  // set cassandra hosts
  if(! cassandraHosts.isEmpty()) {
    cassandraHosts.split(",").zipWithIndex.foreach{ case (h,i) => {
      log.info(s"Cassandra Host: ${i}: ${h}")
      System.setProperty(s"datastax-java-driver.basic.contact-points.${i}",h)
    }}
  }

  implicit val system: ActorSystem = ActorSystem()
  implicit lazy val materializer: ActorMaterializer = ActorMaterializer()
  val sessionSettings = CassandraSessionSettings()
  implicit val cassandraSession: CassandraSession = CassandraSessionRegistry.get(system).sessionFor(sessionSettings)

  val tweeterClient = TwitterStreamingClient()
  
  val statementBinder: (TwitData, PreparedStatement) => BoundStatement =
    (d, preparedStatement) => preparedStatement.bind(d.id, d.ts, d.user, d.txt)

  val cassandraStream = Source
    .queue[TwitData](queueLimit.toInt, OverflowStrategy.backpressure)
    .via(
      CassandraFlow.create(
        CassandraWriteSettings.defaults,
        s"INSERT INTO $keySpace.$table(id, ts, user, txt) VALUES (?, ?, ?, ?)",
        statementBinder
      )
    )
    .to(Sink.foreach(t=>log.info(s"saved: ${t.id}")))
    .run()

  val scrapStream = if(scrapUsers.size == 0) None else Some(new scrap.ScrapFlow(scrapUsers,scrapDir).stream)

  // val test = Source
  //   .repeat(0)
  //   //.delay(FiniteDuration(1,TimeUnit.SECONDS))
  //   .map(v => TwitData(scala.util.Random.nextLong, 100000000000L, "user2","text"))
  //   .take(10)
  //   .map(t => { cassandraStream.offer(t); t})
  //   .to(Sink.foreach(println))
  //   .run()

  tweeterClient.filterStatuses(follow = users.keySet.toSeq) {
    case tweet: Tweet => {
      tweet.retweeted_status match {
        case None => {
          if(users.contains(tweet.user.get.id)) {
            log.debug(s"""${"-".repeat(180)}
id=${tweet.id}
user=${tweet.user.get.id},${users.get(tweet.user.get.id)}
user_retweet=${tweet.current_user_retweet}
retweet_count=${tweet.retweet_count}
retweeted=${tweet.retweeted}
text=${tweet.text}
${"-".repeat(180)}
""")
            println(s"${tweet.id},${tweet.created_at},${tweet.user.get.id},${users.get(tweet.user.get.id).getOrElse("")},${tweet.text}")
            val t = TwitData(tweet.id, tweet.created_at.toEpochMilli, tweet.user.map(_.id).getOrElse(0L).toString,tweet.text)

            App.metricTwitCount.inc()
            
            cassandraStream.offer(t)
            if(scrapStream.isDefined) scrapStream.get.offer(t)
          }
        }
        case Some(tweet2) => {
          log.debug(s"Retweet: id(${tweet.id}${tweet2.id}),users=(${tweet.user.get.id},${tweet2.user.get.id}),count=(${tweet.retweet_count},${tweet2.retweet_count})")
        }
      }
    }
  }

  val version: Future[String] = cassandraSession
      .select("SELECT release_version FROM system.local;")
      .map(_.getString("release_version"))
      .runWith(Sink.head)

  version.onComplete({
    case Success(value) => println(s"Cassandara versions: ${value}")
    case Failure(exception) => exception.printStackTrace
  })

}
