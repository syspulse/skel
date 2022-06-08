// ATTENTION: the sequence of deps loading is important !

// AWS emr-6.6.0   3.2.0
// AWS emr-5.35.0  2.4.8

// ATTENTION: Versions are VERY important !
import $ivy.`org.apache.spark::spark-core:3.2.0` 
import $ivy.`org.apache.spark::spark-sql:3.2.0` 
import $ivy.`org.apache.hadoop:hadoop-aws:3.2.2`

import org.apache.spark.sql.{SparkSession,Dataset}
import org.apache.spark
import org.apache.spark._
import org.apache.spark.sql.functions._

import java.time._

val region = Option(System.getenv("AWS_REGION")).getOrElse("eu-west-1")

@main
def main(input:String="./data/", output:String = "./data/parquet/", codec:String = "parquet", limit:Int = 100, parallelism:Int = 4) {
  
  println(s"input=${input}, output=${output}, codec=${codec}, limit=${limit}, par=${parallelism}")

  //val ss = SparkSession.builder().appName("csv-to-parquet").config("spark.master", "local").config("default.parallelism",parallelism).getOrCreate()
  val ss = SparkSession.builder()
    .appName("csv-to-parquet")
    .config("spark.master", "local")
    .config("default.parallelism",1)
    .config("fs.s3a.aws.credentials.provider", "com.amazonaws.auth.DefaultAWSCredentialsProviderChain")
    .getOrCreate()

  val df = ss.read.option("header", "true").format("com.databricks.spark.csv").csv(input)
  df.printSchema()

  import ss.implicits._

  val ts0 = Instant.now

  df.write.option("maxRecordsPerFile", limit).option("compression", "gzip").mode("overwrite").format(codec).save(output);

  val ts1 = Instant.now
  val elapsed = Duration.between(ts0, ts1)

  println(s"Elapsed time: ${elapsed.toMinutes} min (${elapsed.toSeconds} sec)")
}