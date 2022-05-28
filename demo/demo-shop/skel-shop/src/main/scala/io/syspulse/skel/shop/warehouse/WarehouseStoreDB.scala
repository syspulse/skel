package io.syspulse.skel.shop.warehouse

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

class WarehouseStoreDB extends StoreDB[Warehouse]("db","warehouse") with WarehouseStore {

  import ctx._

  def create:Try[Long] = {
    ctx.executeAction(
      s"CREATE TABLE IF NOT EXISTS ${tableName} (id VARCHAR(36) PRIMARY KEY, sid SERIAL NOT NULL, ts TIMESTAMP(6) NOT NULL, name VARCHAR(128), country_id VARCHAR(36), location VARCHAR(128) );"
    )
    // why do we still use MySQL which does not even support INDEX IF NOT EXISTS ?...
    //val r = ctx.executeAction("CREATE INDEX IF NOT EXISTS warehouse_name ON warehouse (name);")
    try {
      ctx.executeAction(s"CREATE INDEX warehouse_ts ON ${tableName} (ts);")
      ctx.executeAction(s"CREATE INDEX warehouse_name ON ${tableName} (name);")
      ctx.executeAction(s"CREATE INDEX warehouse_country_id ON ${tableName} (country_id);")
      Success(0)
    } catch {
      case e:Exception => { 
        // short name without full stack (change to check for duplicate index)
        log.warn(s"failed to create index: ${e.getMessage()}"); Success(0) 
      }
    }
  }
  
  def getAll:Seq[Warehouse] = ctx.run(query[Warehouse])
  
  val deleteById = quote { (id:UUID) => 
    query[Warehouse].filter(o => o.id == id).delete
  } 

  def load:Seq[Warehouse] = {
    val cc = WarehouseLoader.fromResource()
    log.info(s"load: ${cc.size}")
    try {
      cc.foreach( c => ctx.run(query[Warehouse].insert(lift(c))))
      cc
    } catch {
      case e:Exception => {
        log.warn(s"could not load: ${e.getMessage()}")
        Seq()
      }
    }
  }

  override def size:Long = {
    val q = quote { query[Warehouse].map(c => c.id).size }
    ctx.run(q)
  }

  override def clear:Try[WarehouseStore] = {
    log.info(s"clear: ")
    truncate()
    Success(this)
  }

  def +(warehouse:Warehouse):Try[WarehouseStoreDB] = { 
    log.info(s"insert: ${warehouse}")
    try {
      ctx.run(query[Warehouse].insert(lift(warehouse))); 
      Success(this)
    } catch {
      case e:Exception => Failure(new Exception(s"could not insert: ${e}"))
    }
  }

  def -(id:UUID):Try[WarehouseStoreDB] = { 
    log.info(s"delete: id=${id}")
    try {
      ctx.run(deleteById(lift(id)))
      Success(this)
    } catch {
      case e:Exception => Failure(new Exception(s"could not delete: ${e}"))
    } 
  }
  def -(warehouse:Warehouse):Try[WarehouseStoreDB] = { this.-(warehouse.id) }

  def get(id:UUID):Option[Warehouse] = {
    log.info(s"get: id=${id}")
    ctx.run(query[Warehouse].filter(o => o.id == lift(id))) match {
      case h :: _ => Some(h)
      case Nil => None
    }
  }

  def getByName(name:String):Option[Warehouse] = {
    log.info(s"get: name=${name}")
    ctx.run(query[Warehouse]
      .filter(o => 
        o.name.toLowerCase == lift(name.toLowerCase())
      )) match {
      case h :: _ => Some(h)
      case Nil => None
    }
  }
}