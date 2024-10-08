package io.syspulse.skel.spark

import scala.collection.immutable

import java.util.concurrent.TimeUnit
import scala.concurrent.Future
import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.Logger

import java.time.Duration
import java.time.Instant

import io.syspulse.skel.config._
import io.syspulse.skel.util.Util

import org.apache.spark.sql.{SparkSession,Dataset}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.DecimalType
import org.apache.spark.sql.types._

import org.apache.spark.sql.DataFrame

case class Config(
  host:String="",
  port:Int=0,
  uri:String = "",

  input:String ="./data/csv/",
  output:String = "./data/parquet/",
  codec:String = "parquet",
  batch:Int = 100, 
  parallelism:Int = 2,

  mapping:Seq[(String,String)] = Seq(),

  sparkExMem:String = "",
  sparkDrvMem:String = "",
  sparkCoresMax:Int = 0,

  kubeServiceAccount:String = "",
  kubeNamespace:String = "",
  
  cmd:String = "",
  params: Seq[String] = Seq(),
)


object AppSparkRead { 

  def main(args: Array[String]): Unit = {

    println(s"app: ${Util.info}")
    println(s"args: '${args.mkString(",")}'")
    println(s"res: ${Util.top()}")

    val c = Configuration.withPriority(Seq(
      new ConfigurationAkka,
      new ConfigurationProp,
      new ConfigurationEnv, 
      new ConfigurationArgs(args,"spark-read","",
        ArgString('i', "input","Input path (file/dir)"),
        ArgString('o', "output","Output path (file/dir)"),
        ArgString('c', "codec","Codec (parquet/avro)"),
        ArgInt('b', "batch","How many records to process in stream (def: 100)"),
        ArgInt('p', "par","Parallelism (def: 2)"),
        ArgString('m', "map","Fields types map: 'name:type;name:type,...' (ex: 'number:LongType;difficulty:DecimalType(38,0)')"),

        ArgString('_', "spark.executor.memory","(def :1g)"),
        ArgString('_', "spark.driver.memory","(def: 1g)"),
        ArgInt('_',"spark.cores.max","(def: max)"),

        ArgString('_', "kube.sa","(def: '')"),
        ArgString('_', "kube.namespace","(def: '')"),

        ArgCmd("convert","Command"),
        ArgCmd("read","Command"),
        ArgParam("<params>",""),

        ArgLogging()
      ).withExit(1)
    )).withLogging()


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
  
    var sb = SparkSession.builder()
      .appName("csv-to-parquet")
      .config("spark.master", "local")
      .config("default.parallelism",config.parallelism)
      .config("spark.executor.memory", config.sparkExMem)
      .config("spark.driver.memory", config.sparkDrvMem)
      .config("spark.cores.max", config.sparkCoresMax)
      //.config("spark.driver.cores", config.sparkDrvCores)
      //.config("spark.executor.cores", config.sparkExCores)
    
    val ss = sb.getOrCreate()
    println(s"SparkSession: ${ss}: conf=${ss.conf.getAll}")

    val df = ss.read.option("header", "true").format("com.databricks.spark.csv").csv(config.input)
    df.printSchema()

  }
}