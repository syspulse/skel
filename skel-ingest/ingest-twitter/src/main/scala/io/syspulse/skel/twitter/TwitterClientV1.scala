// package io.syspulse.skel.twitter

// import scala.jdk.CollectionConverters._
// import com.typesafe.scalalogging.Logger
// import scala.concurrent.duration._
// import scala.concurrent.{Await, ExecutionContext, Future}
// import scala.concurrent.ExecutionContext.Implicits.global
// import java.util.concurrent.TimeUnit

// import akka.actor.ActorSystem
// import akka.stream.scaladsl.Keep
// import akka.{Done, NotUsed}
// import akka.stream.ActorMaterializer
// import akka.stream._
// import akka.stream.scaladsl._
// import akka.util.ByteString

// import spray.json._

// import com.danielasfregola.twitter4s.TwitterStreamingClient
// import com.danielasfregola.twitter4s.entities.Twit
// import com.danielasfregola.twitter4s.entities.ConsumerToken
// import com.danielasfregola.twitter4s.entities.AccessToken
// import com.danielasfregola.twitter4s.entities.Contributor

// import io.syspulse.skel.Ingestable
// import io.syspulse.skel.uri.TwitterURI
// import io.syspulse.skel.service.JsonCommon
// import com.danielasfregola.twitter4s.entities.Coordinate

// import spray.json._
// import com.danielasfregola.twitter4s.entities.Coordinates
// import java.time.Instant

// //case class Twit(f1:String,f2:Option[String])

// object TwitJsonV1 extends JsonCommon with DefaultJsonProtocol {
//   implicit val jf_twit_cont = jsonFormat3(Contributor)
//   implicit val jf_twit_coords = jsonFormat2(Coordinates)
//   implicit val jf_twit_coord = jsonFormat2(Coordinate)

//   import DefaultJsonProtocol._

//   implicit object TwitJsonFormat extends RootJsonFormat[Twit] {
//     override def write(obj: Twit): JsValue = {
//       JsObject( 
//         // "contributors" -> JsArray(obj.contributors.map(_.toJson).toVector),
//         // "coordinates" -> obj.coordinates.toJson,
//         "created_at" -> JsNumber(obj.created_at.toEpochMilli()),
        
//         // "favorite_count" -> JsNumber(obj.favorite_count),
//         // "favorited" -> JsBoolean(obj.favorited),

//         "id" -> JsNumber(obj.id),
//         "id_str" -> JsString(obj.id_str),
        
//         "source" -> JsString(obj.source),
//         "text" -> JsString(obj.text),
//       )
//     }

//     def read(json: JsValue): Twit = {
//       val fields = json.asJsObject.fields
//       Twit(
//         created_at = Instant.ofEpochMilli(fields("created_at").convertTo[Long]),
//         id = fields("id").convertTo[Long],
//         id_str = fields("id_str").convertTo[String],

//         source = fields("source").convertTo[String],
//         text = fields("text").convertTo[String],
//       )
//     }
//   }
// }

// trait TwitterClientV1[T <: Ingestable] {
//   val log = Logger(s"${this}")
//   implicit val as: akka.actor.ActorSystem = ActorSystem("ActorSystem-TwitterClient")

//   //def timeout() = Duration("3 seconds")  

//   import TwitJsonV1._
//   //implicit val fmt:JsonFormat[T]  

//   def source(consumerKey:String,consumerSecret:String,accessKey:String,accessSecret:String,followUsers:Set[String]) = {

//     val client = TwitterStreamingClient(
//       ConsumerToken(key = consumerKey, secret = consumerSecret), 
//       AccessToken(key = accessKey, secret = accessSecret)
//     )

//     val followId = followUsers.map(_.toLong)
//     val users = followId.map(id => (id -> id.toString)).toMap

//     val (s0,mat0) = Source
//       .queue[Twit](1000, OverflowStrategy.backpressure)
//       .map(t => ByteString(t.toJson.compactPrint))
//       .preMaterialize()

//     val futureTwitter = client.filterStatuses(follow = followId.toSeq) {
//       case tweet: Twit => {
//         tweet.retweeted_status match {
//           case None => {
//             if(followId.contains(tweet.user.get.id)) {
//               log.info(s"""${"-".repeat(180)}
//   id=${tweet.id}
//   user=${tweet.user.get.id},${users.get(tweet.user.get.id)}
//   user_retweet=${tweet.current_user_retweet}
//   retweet_count=${tweet.retweet_count}
//   retweeted=${tweet.retweeted}
//   text=${tweet.text}
//   ${"-".repeat(180)}
//   """)
                                      
//               s0.offer(tweet)
//               tweet
//             }
//           }
//           case Some(tweet) => {
//             log.info(s"Retweet: id(${tweet.id}${tweet.id}),users=(${tweet.user.get.id},${tweet.user.get.id}),count=(${tweet.retweet_count},${tweet.retweet_count})")
//             s0.offer(tweet)
//             tweet
//           }
//         }
//       }
//     }

//     // if(frameDelimiter.isEmpty())
//     //   s
//     // else
//     //   s.via(Framing.delimiter(ByteString(frameDelimiter), maximumFrameLength = frameSize, allowTruncation = true))
//     mat0

//   }

// }

// class FromTwitterV1[T <: Ingestable](uri:String) extends TwitterClientV1[T] {
//   val twitterUri = TwitterURI(uri)
    
//   def source():Source[ByteString,_] = 
//     source(twitterUri.consumerKey,twitterUri.consumerSecret,twitterUri.accessKey,twitterUri.accessSecret,twitterUri.follow.toSet)
// }
