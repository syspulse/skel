//opt/amm/amm-2.12-2.2.0

// Requires scala-2.12: 
// ATTENTION: the sequence of deps loading is important !

// fixes java.lang.NoSuchMethodError: com.fasterxml.jackson.databind.JsonMappingException.<init>(Ljava/io/Closeable;Ljava/lang/String;)V
import $ivy.`com.fasterxml.jackson.core:jackson-databind:2.12.5`
import $ivy.`com.fasterxml.jackson.module::jackson-module-scala:2.12.5`

import com.fasterxml.jackson.databind._

// Must be loaded first to avoid error:
// java.lang.NoSuchFieldError: INSTANCE org.apache.http.conn.ssl.SSLConnectionSocketFactory.<clinit>(SSLConnectionSocketFactory.java:151)

import $ivy.`org.apache.spark::spark-core:3.0.0` 
import $ivy.`org.apache.spark::spark-sql:3.0.0` 

import org.apache.spark.sql.{SparkSession,Dataset}
import org.apache.spark
import org.apache.spark._
import org.apache.spark.sql.functions._

import java.time._

val region = Option(System.getenv("AWS_REGION")).getOrElse("eu-west-1")

case class Content (id:String, contentType:String, title:String, releaseDate: String, genres:List[String],directors:List[String],sourceId:String, sourceEntity:String, originalTitle:String, description: String)

@main
def main(input:String="./data/contents",limit:Int = 100, parallelism:Int = 4, search:Seq[String] = Range(0,500*5).map(_=>scala.util.Random.alphanumeric.take(10).mkString)) {
  val searching = search.map(_.toLowerCase.trim).toVector

  println(s"Search: ${searching}")

  val sparkSession = SparkSession.builder().appName("CSV-Count").config("spark.master", "local").config("default.parallelism",parallelism).getOrCreate()

  // region options is mandatory (AWS_REGION is not read for some reason)
  val reader = sparkSession.read.option("header", "true").option("defaultParallelism",parallelism).format("com.databricks.spark.csv").csv(input)
  println(s"reader=${reader}")

  import sparkSession.implicits._

  val contents:Dataset[Content] = reader.withColumn("genres", split(col("genres"), ",").cast("array<string>")).withColumn("directors", split(col("directors"), ",").cast("array<string>")).as[Content]
  println(s"contents=${contents}")

  val ds = if(limit == 0) contents else contents.limit(limit)

  val count = ds.count
  println(s"Count: ${count}")

  val ts0 = Instant.now

  val matches = ds.filter( c => 
    (c.title!=null && searching.contains(c.title.toLowerCase)) ||
    (c.releaseDate!=null && searching.contains(c.releaseDate.toString.toLowerCase)) ||
    (c.originalTitle!=null && searching.contains(c.originalTitle.toLowerCase)) ||
    (c.description!=null && searching.contains(c.description.toLowerCase)) ||
    (c.genres!=null && c.genres.size>0 && ( ! searching.intersect(c.genres.map(_.trim.toLowerCase)).isEmpty))
  )

  val matched = matches.collect.toVector

  val ts1 = Instant.now
  val elapsed = Duration.between(ts0, ts1)

  val dump = matched.zipWithIndex.map{ case(m,i) => s"${i}: ${m}"}.mkString("\n")
  println(s"matches: \n${dump}\n-----\nmatched: ${matched.size}\ntotal=${count}\nElapsed time: ${elapsed.toMinutes} min (${elapsed.toSeconds} sec)")
}