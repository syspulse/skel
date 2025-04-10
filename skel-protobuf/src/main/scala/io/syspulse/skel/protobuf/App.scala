package io.syspulse.skel.protobuf

import scala.util.{Try,Success,Failure}
import java.util.Base64

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._

case class Config(
  host:String="0.0.0.0",
  port:Int=8080,
  uri:String = "/api/v1/auth",

  datastore:String = "mem://",
  
  cmd:String = "proto",
  params: Seq[String] = Seq(),
)

object App extends skel.Server {
  
  def main(args:Array[String]):Unit = {
    Console.err.println(s"args: '${args.mkString(",")}'")

    val d = Config()
    val c = Configuration.withPriority(Seq(
      new ConfigurationAkka,
      new ConfigurationProp,
      new ConfigurationEnv, 
      new ConfigurationArgs(args,"skel-protobuf","",
        ArgString('h', "http.host",s"listen host (def: ${d.host})"),
        ArgInt('p', "http.port",s"listern port (def: ${d.port})"),
        ArgString('u', "http.uri",s"api uri (def: ${d.uri})"),
        ArgString('d', "datastore",s"datastore [mysql,postgres,mem,cache] (def: ${d.datastore})"),
        
        
        ArgCmd("server",s"Server"),
        ArgCmd("proto",s"Proto utils"),
                
        ArgParam("<params>",""),
        ArgLogging()
      ).withExit(1)
    )).withLogging()

    val config = Config(
      host = c.getString("http.host").getOrElse(d.host),
      port = c.getInt("http.port").getOrElse(d.port),
      uri = c.getString("http.uri").getOrElse(d.uri),
      datastore = c.getString("datastore").getOrElse(d.datastore),
      
      cmd = c.getCmd().getOrElse(d.cmd),
      params = c.getParams(),
    )

    Console.err.println(s"Config: ${config}")

    var r = config.cmd match {
      case "server" => 
              
      case "proto" => {
        import com.google.protobuf.ByteString
        //import com.thesamet.scalapb.GeneratedMessage
        //import TxRaw

        Try {
          val txBytes = Base64.getDecoder.decode(config.params(0))
          // 
          cosmos.tx.v1beta1.tx.TxRaw.parseFrom(txBytes)
        }
      }
    }

    println(s"${r}")        
  }
}



