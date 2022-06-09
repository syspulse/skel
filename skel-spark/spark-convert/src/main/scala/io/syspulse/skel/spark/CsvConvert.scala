package io.syspulse.skel.spark

import scala.collection.immutable

import java.util.concurrent.TimeUnit
import scala.concurrent.Future
import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.Logger

import java.time.Duration
import java.time.Instant

import org.apache.spark.sql.{SparkSession,Dataset}
import org.apache.spark.sql.functions._

import io.syspulse.skel.config._
import io.syspulse.skel.util.Util

case class Config(
  host:String="",
  port:Int=0,
  uri:String = "",

  input:String ="./data/csv/",
  output:String = "./data/parquet/",
  codec:String = "parquet", 
  batch:Int = 100, 
  parallelism:Int = 2,

  sparkExMem:String = "",
  sparkDrvMem:String = "",
  sparkCoresMax:Int = 0,
  
  cmd:String = "",
  params: Seq[String] = Seq(),
)


object CsvConvert { 
  def main(args: Array[String]): Unit = {

    println(s"args: '${args.mkString(",")}'")
    println(s"res: ${Util.top()}")

    val c = Configuration.withPriority(Seq(
      new ConfigurationAkka,
      new ConfigurationProp,
      new ConfigurationEnv, 
      new ConfigurationArgs(args,"spark-convert","",
        ArgString('i', "input","Input path (file/dir)"),
        ArgString('o', "output","Output path (file/dir)"),
        ArgString('c', "codec","Codec (parquet/avro)"),
        ArgInt('b', "batch","How many records to process in stream (def: 100)"),
        ArgInt('p', "par","Parallelism (def: 2)"),

        ArgString('_', "spark.executor.memory","(def :1g)"),
        ArgString('_', "spark.driver.memory","(def: 1g)"),
        ArgInt('_',"spark.cores.max","(def: max)"),

        ArgCmd("convert","Command"),
        ArgCmd("read","Command"),
        ArgParam("<params>","")
      ).withExit(1)
    ))

    val config = Config(
      input = c.getString("input").getOrElse("./data/csv/"),
      output = c.getString("output").getOrElse("./data/parquet/"),
      codec = c.getString("codec").getOrElse("parquet"),
      batch = c.getInt("batch").getOrElse(100),
      parallelism = c.getInt("par").getOrElse(2),

      sparkExMem = c.getString("spark.executor.memory").getOrElse("1g"),
      sparkDrvMem = c.getString("spark.driver.memory").getOrElse("1g"),
      sparkCoresMax = c.getInt("spark.cores.max").getOrElse(Int.MaxValue),

      cmd = c.getCmd().getOrElse("convert"),
      params = c.getParams(),
    )

    println(s"Config: ${config}")
  
    val ss = SparkSession.builder()
      .appName("csv-to-parquet")
      .config("spark.master", "local")
      .config("default.parallelism",config.parallelism)
      .config("fs.s3a.aws.credentials.provider", "com.amazonaws.auth.DefaultAWSCredentialsProviderChain")
      .config("spark.executor.memory", config.sparkExMem)
      .config("spark.driver.memory", config.sparkDrvMem)
      .config("spark.cores.max", config.sparkCoresMax)

      //.config("spark.driver.cores", config.sparkDrvCores)
      //.config("spark.executor.cores", config.sparkExCores)
    .getOrCreate()

    val df = ss.read.option("header", "true").format("com.databricks.spark.csv").csv(config.input)
    df.printSchema()

    import ss.implicits._

    val ts0 = Instant.now

    df.write.option("maxRecordsPerFile", config.batch).option("compression", "gzip").mode("overwrite").format(config.codec).save(config.output);

    val ts1 = Instant.now
    val elapsed = Duration.between(ts0, ts1)

    println(s"Elapsed time: ${elapsed.toMinutes} min (${elapsed.toSeconds} sec)")
  }

}