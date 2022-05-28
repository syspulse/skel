package io.syspulse.skel.shop

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.config.{Configuration,ConfigurationAkka,ConfigurationEnv}

import io.syspulse.skel.shop.item.{ItemRegistry,ItemRoutes,ItemStoreDB}
import io.syspulse.skel.shop.warehouse.{WarehouseRegistry,WarehouseRoutes,WarehouseStoreDB}
import io.syspulse.skel.shop.shipment.{ShipmentRegistry,ShipmentRoutes,ShipmentStoreDB}
import io.syspulse.skel.shop.order.{OrderRegistry,OrderRoutes,OrderStoreDB}

import scopt.OParser

case class Config(
  host:String="",
  port:Int=0,
  uri:String = ""
)

object App extends skel.Server {
  
  def main(args:Array[String]) = {
    val (appName,appVersion) = Util.info

    println(s"${appName}:${appVersion}")
    println(s"args: '${args.mkString(",")}'")

    val builder = OParser.builder[Config]
    val argsParser = {
      import builder._
      OParser.sequence(
        programName(appName), head(appName, appVersion),
        opt[String]('h', "host").action((x, c) => c.copy(host = x)).text("hostname"),
        opt[Int]('p', "port").action((x, c) => c.copy(port = x)).text("port"),
        opt[String]('u', "uri").action((x, c) => c.copy(uri = x)).text("uri"),
      )
    } 
  
    OParser.parse(argsParser, args, Config()) match {
      case Some(configArgs) => {
        val confuration = Configuration.withPriority(Seq(new ConfigurationEnv,new ConfigurationAkka))

        val config = Config(
          host = { if(! configArgs.host.isEmpty) configArgs.host else confuration.getString("http.host").getOrElse("0.0.0.0") },
          port = { if(configArgs.port!=0) configArgs.port else confuration.getInt("http.port").getOrElse(8080) },
          uri = { if(! configArgs.uri.isEmpty) configArgs.uri else confuration.getString("uri").getOrElse("/api/v1/shop") },
        )

        println(s"Config: ${config}")

        run( config.host, config.port, config.uri, confuration,
          Seq(
            (ItemRegistry(new ItemStoreDB),"ItemRegistry",(actor,actorSystem ) => new ItemRoutes(actor)(actorSystem) ),
            (WarehouseRegistry(new WarehouseStoreDB),"WarehouseRgistry",(actor,actorSystem ) => new WarehouseRoutes(actor)(actorSystem) ),
            (ShipmentRegistry(new ShipmentStoreDB),"ShipmentRgistry",(actor,actorSystem ) => new ShipmentRoutes(actor)(actorSystem) ),
            (OrderRegistry(new OrderStoreDB),"OrderRgistry",(actor,actorSystem ) => new OrderRoutes(actor)(actorSystem) )
          )
        )
      }
      case _ => 
    }
  }
}

