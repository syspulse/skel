package io.syspulse.skel.shop.shipment

import scala.util.Try
import scala.util.{Success,Failure}

import io.jvm.uuid._

import io.getquill._
import io.getquill.MysqlJdbcContext
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import io.getquill.{Literal, MySQLDialect}

import scala.jdk.CollectionConverters._
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.store.{Store,StoreDB}

class ShipmentStoreDB extends StoreDB[Shipment]("db","shipment") with ShipmentStore {

  import ctx._

  def create:Try[Long] = {
    ctx.executeAction(
      s"CREATE TABLE IF NOT EXISTS ${tableName} (id VARCHAR(36) PRIMARY KEY, sid SERIAL NOT NULL, ts TIMESTAMP(6) NOT NULL, order_id VARCHAR(36), warehouse_id VARCHAR(36), address VARCHAR(128), shipment_type VARCHAR(5) );"
    )
    // why do we still use MySQL which does not even support INDEX IF NOT EXISTS ?...
    //val r = ctx.executeAction("CREATE INDEX IF NOT EXISTS shipment_name ON shipment (name);")
    try {
      ctx.executeAction(s"CREATE INDEX shipment_ts ON ${tableName} (ts);")
      ctx.executeAction(s"CREATE INDEX shipment_order_id ON ${tableName} (order_id);")
      ctx.executeAction(s"CREATE INDEX shipment_warehouse_id ON ${tableName} (warehouse_id);")
      ctx.executeAction(s"CREATE INDEX shipment_shipment_type ON ${tableName} (shipment_type);")
      Success(0)
    } catch {
      case e:Exception => { 
        // short name without full stack (change to check for duplicate index)
        log.warn(s"failed to create index: ${e.getMessage()}"); Success(0) 
      }
    }
  }
  
  def getAll:Seq[Shipment] = ctx.run(query[Shipment])
  
  val deleteById = quote { (id:UUID) => 
    query[Shipment].filter(o => o.id == id).delete
  } 

  def load:Seq[Shipment] = {
    val cc = ShipmentLoader.fromResource()
    log.info(s"load: ${cc.size}")
    try {
      cc.foreach( c => ctx.run(query[Shipment].insert(lift(c))))
      cc
    } catch {
      case e:Exception => {
        log.warn(s"could not load: ${e.getMessage()}")
        Seq()
      }
    }
  }

  override def size:Long = {
    val q = quote { query[Shipment].map(c => c.id).size }
    ctx.run(q)
  }

  override def clear:Try[ShipmentStore] = {
    log.info(s"clear: ")
    truncate()
    Success(this)
  }

  def +(shipment:Shipment):Try[ShipmentStoreDB] = { 
    log.info(s"insert: ${shipment}")
    try {
      ctx.run(query[Shipment].insert(lift(shipment))); 
      Success(this)
    } catch {
      case e:Exception => Failure(new Exception(s"could not insert: ${e}"))
    }
  }

  def -(id:UUID):Try[ShipmentStoreDB] = { 
    log.info(s"delete: id=${id}")
    try {
      ctx.run(deleteById(lift(id)))
      Success(this)
    } catch {
      case e:Exception => Failure(new Exception(s"could not delete: ${e}"))
    } 
  }
  def -(shipment:Shipment):Try[ShipmentStoreDB] = { this.-(shipment.id) }

  def get(id:UUID):Option[Shipment] = {
    log.info(s"get: id=${id}")
    ctx.run(query[Shipment].filter(o => o.id == lift(id))) match {
      case h :: _ => Some(h)
      case Nil => None
    }
  }

  def getByOrderId(oid:UUID):Option[Shipment] = {
    log.info(s"get: oid=${oid}")
    ctx.run(query[Shipment]
      .filter(o => 
        o.orderId == lift(oid)
      )) match {
      case h :: _ => Some(h)
      case Nil => None
    }
  }
}