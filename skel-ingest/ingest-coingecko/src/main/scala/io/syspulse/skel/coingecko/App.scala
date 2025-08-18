package io.syspulse.skel.coingecko

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.{Duration,FiniteDuration}
import com.typesafe.scalalogging.Logger
import java.util.concurrent.TimeUnit

import io.syspulse.skel.config._
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._

import io.syspulse.skel.coingecko.flow._

case class Config(  
  entity:String = "coins",
  
  filter:Seq[String] = Seq(),  
  limit:Long = Long.MaxValue,
  size:Long = Long.MaxValue,

  feed:String = "stdin://",
  output:String = "stdout://",
  
  delimiter:String = "\n",
  buffer:Int = 1024 * 1000 * 10, // 10MB
  throttle:Long = 0L,
  throttleSource:Long = 100L,
  format:String = "",

  retryDelay:Long = 5000L,
  retryCount:Int = 3,

  parser:String = "ujson",

  cmd:String = "coins",

  params: Seq[String] = Seq(),
)

object App {
  def main(args:Array[String]): Unit = {
    Console.err.println(s"args: ${args.size}: ${args.toSeq}")

    val d = Config()

    val c = Configuration.withPriority(Seq(
      new ConfigurationAkka,
      new ConfigurationProp,
      new ConfigurationEnv, 
      new ConfigurationArgs(args,"ingest-coingecko","",        
        ArgString('e', "entity",s"Entity (coin, raw) (def=${d.entity})"),

        ArgString('f', "feed",s"Input Feed (stdin://, http://, file://, kafka://) (def=${d.feed})"),
        ArgString('o', "output",s"Output (stdout://, csv://, json://, log://, file://, hive://, elastic://, kafka:// (def=${d.output})"),

        ArgString('_', "delimiter",s"""Delimiter characteds (def: '${Util.hex(d.delimiter.getBytes())}'). Usage example: --delimiter=`echo -e "\\r\\n"` """),
        ArgInt('_', "buffer",s"Frame buffer (Akka Framing) (def: ${d.buffer})"),
        ArgLong('_', "throttle",s"Throttle messages in msec (def: ${d.throttle})"),
        ArgLong('_', "throttle.source",s"Throttle source (e.g. http, def=${d.throttleSource})"),
        ArgString('_', "format",s"Format output (json,csv,log) (def=${d.format})"),

        ArgString('_', "filter",s"Filter coins (def: ${d.filter.mkString(",")})"),
        ArgLong('n', s"limit",s"File Limit (def: ${d.limit})"),
        ArgLong('s', s"size",s"File Size Limit (def: ${d.size})"),

        ArgLong('_', "retry.delay",s"Retry delay (msec) (def: ${d.retryDelay})"),
        ArgInt('_', "retry.count",s"Retry count (def: ${d.retryCount})"),

        ArgString('e', "entity",s"Entity (coins, coin, raw) (def=${d.entity})"),
        ArgString('p', "parser",s"Parser (ujson, json) (def=${d.parser})"),

        ArgCmd("pipeline","Create pipeline"),
        ArgCmd("coins","Ask All Coin ID"),
        ArgCmd("coin","Ask Coin by ID"),
        
        ArgParam("<params>",""),

        ArgLogging(),
        ArgConfig(),
      ).withExit(1)
    )).withLogging()

    implicit val config = Config(
      feed = c.getString("feed").getOrElse(d.feed),
      output = c.getString("output").getOrElse(d.output),      

      limit = c.getLong("limit").getOrElse(d.limit),
      size = c.getLong("size").getOrElse(d.size),
      
      delimiter = c.getString("delimiter").getOrElse(d.delimiter),
      buffer = c.getInt("buffer").getOrElse(d.buffer),
      throttle = c.getLong("throttle").getOrElse(d.throttle),
      throttleSource = c.getLong("throttle.source").getOrElse(d.throttleSource),
      format = c.getString("format").getOrElse(d.format),

      filter = c.getListString("filter",d.filter),

      retryDelay = c.getLong("retry.delay").getOrElse(d.retryDelay),
      retryCount = c.getInt("retry.count").getOrElse(d.retryCount),

      entity = c.getString("entity").getOrElse(d.entity),
      parser = c.getString("parser").getOrElse(d.parser),
      
      cmd = c.getCmd().getOrElse(d.cmd),
      params = c.getParams(),
    )

    Console.err.println(s"Config: ${config}")
        
    implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global    

    val r = config.cmd match {
      case "coins" =>   
        val uri = config.params.headOption.getOrElse("cg://")
        val ids = config.params.drop(1).toSet
        val cg = Coingecko(uri)    
        cg.get.askCoins(ids)

      case "coin" =>   
        val uri = config.params.headOption.getOrElse("cg://")
        val ids = config.params.drop(1)
        val cg = Coingecko(uri)
        cg.get.askCoins(ids.toSet)
      
      case "source" =>         
        val uri = config.params.headOption.getOrElse("cg://")
        val ids = config.params.drop(1).toSet
        
        val cg = Coingecko(uri)    
        val src = cg.get.source(ids)

      case "pipeline" if config.entity == "coins" =>
        val ids = config.params.drop(1).toSet        
        val p = new PipelineCoins(config.feed,config.output) 
        p.run()

      case "pipeline" if config.entity == "coin" =>         
        val p = new PipelineCoin(config.feed,config.output) 
        p.run()

      case "pipeline" if config.entity == "raw.coins" =>
        val p = new PipelineRawCoins(config.feed,config.output) 
        p.run()

      case "pipeline" if config.entity == "raw.coin" =>
        val p = new PipelineRawCoin(config.feed,config.output) 
        p.run()
        
    }

    Console.err.println(s"r = ${r}")
    //sys.exit(0)
  }
}
