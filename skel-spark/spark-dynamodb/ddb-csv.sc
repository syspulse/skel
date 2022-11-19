//opt/amm/amm-2.12-2.2.0

// Requires scala-2.12: 
// ATTENTION: the sequence of deps loading is important !

// fixes java.lang.NoSuchMethodError: com.fasterxml.jackson.databind.JsonMappingException.<init>(Ljava/io/Closeable;Ljava/lang/String;)V
import $ivy.`com.fasterxml.jackson.core:jackson-databind:2.12.5`
import $ivy.`com.fasterxml.jackson.module::jackson-module-scala:2.12.5`

import com.fasterxml.jackson.databind._

// Must be loaded first to avoid error:
// java.lang.NoSuchFieldError: INSTANCE org.apache.http.conn.ssl.SSLConnectionSocketFactory.<clinit>(SSLConnectionSocketFactory.java:151)
import $ivy.`com.audienceproject::spark-dynamodb:1.1.2`

import $ivy.`org.apache.spark::spark-core:3.0.0` 
import $ivy.`org.apache.spark::spark-sql:3.0.0` 

import com.audienceproject.spark.dynamodb.implicits._
import com.audienceproject.spark.dynamodb.attribute

import org.apache.spark.sql.SparkSession
import org.apache.spark._
import org.apache.spark.sql.functions._

val region = Option(System.getenv("AWS_REGION")).getOrElse("eu-west-1")

case class Content (id:String, contentType:String, title:String, releaseDate: String, genres:List[String],directors:List[String],sourceId:String, sourceEntity:String, originalTitle:String, description: String)

@main
def main(table:String="Movies",limit:Int = 100,parallelism:Int = 4,output:String="./data/contents") {
  //val tableName = "UserList"
  val tableName = table

  val sparkSession = SparkSession.builder().appName("DDB-CSV-Transformer").config("spark.master", "local").getOrCreate()

  // region options is mandatory (AWS_REGION is not read for some reason)
  val reader = sparkSession.read.option("tableName", tableName).option("region",region).option("defaultParallelism",parallelism).format("dynamodb")

  //val dd = reader.load()
  //dd.show(10)
  
  val contents = reader.dynamodbAs[Content](tableName)

  val ds = if(limit == 0) contents else contents.limit(limit)
  ds.withColumn("genres",col("genres").cast("string")).withColumn("directors",col("directors").cast("string")).write.format("com.databricks.spark.csv").option("header", "true").save(output)
}