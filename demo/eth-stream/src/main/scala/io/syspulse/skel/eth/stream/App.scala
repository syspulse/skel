package io.syspulse.skel.eth.stream

import io.syspulse.skel.util.Util
import io.syspulse.skel.config._

import com.typesafe.scalalogging.Logger
import io.syspulse.skel.dsl.JS
import akka.stream.scaladsl.Flow
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import akka.stream.scaladsl.Tcp
import akka.stream.scaladsl.Source
import akka.util.ByteString
import akka.actor.ActorSystem
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Keep
import scala.concurrent.Future
import akka.stream.scaladsl.StreamConverters
import akka.stream.IOResult
import akka.stream.scaladsl.RestartFlow
import akka.stream.RestartSettings
import scala.concurrent.duration.FiniteDuration
import java.net.InetSocketAddress
import scala.concurrent.duration.Duration

case class Config(
  script:String="",
  source:String="",
  cmd:Seq[String] = Seq()
)

object App extends {
  val log = Logger(s"${this.getClass().getSimpleName()}")
  
  def main(args:Array[String]) = {
    
    val c = Configuration.withPriority(Seq(
      new ConfigurationAkka,
      new ConfigurationProp,
      new ConfigurationEnv, 
      new ConfigurationArgs(args,"eth-stream","",
        ArgString('s', "script","Javascript script (use: @filename.js)"),
        ArgString('i', "source","Source (def: stdin)"),
        ArgParam("<cmd>","")
      )
    ))
    
    val config = Config(
      script = c.getString("script").getOrElse(""),
      source = c.getString("source").getOrElse("stdin"),
      cmd = c.getParams()
    )

    new StreamEth(config).run()
    System.exit(0)
  }
}

