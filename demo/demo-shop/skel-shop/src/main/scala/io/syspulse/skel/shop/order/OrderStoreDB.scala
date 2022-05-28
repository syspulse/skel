package io.syspulse.skel.shop.order

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

class OrderStoreDB extends StoreDB[Order]("db","`order`") with OrderStore {

  import ctx._

  val queryOrder = quote {
    querySchema[Order]("`order`")
  }

  def create:Try[Long] = {
    ctx.executeAction(
      s"""CREATE TABLE IF NOT EXISTS ${tableName} 
         (id VARCHAR(36) NOT NULL, 
          sid SERIAL NOT NULL, 
          ts TIMESTAMP(6) NOT NULL, 
          item VARCHAR(36), 
          order_status VARCHAR(10), 
          PRIMARY KEY(id, sid) 
        );"""
    )
    // why do we still use MySQL which does not even support INDEX IF NOT EXISTS ?...
    //val r = ctx.executeAction("CREATE INDEX IF NOT EXISTS order_name ON order (name);")
    try {
      ctx.executeAction(s"CREATE INDEX order_ts ON ${tableName} (ts);")
      ctx.executeAction(s"CREATE INDEX order_order_status ON ${tableName} (order_status);")
      Success(0)
    } catch {
      case e:Exception => { 
        // short name without full stack (change to check for duplicate index)
        log.warn(s"failed to create index: ${e.getMessage()}"); Success(0) 
      }
    }
  }
  
  def getAll:Seq[Order] = ctx.run(queryOrder)
  
  val deleteById = quote { (id:UUID) => 
    queryOrder.filter(o => o.id == id).delete
  } 

  def load:Seq[Order] = {
    val cc = OrderLoader.fromResource()
    log.info(s"load: ${cc.size}")
    try {
      cc.foreach( c => ctx.run(queryOrder.insert(lift(c))))
      cc
    } catch {
      case e:Exception => {
        log.warn(s"could not load: ${e.getMessage()}")
        Seq()
      }
    }
  }

  override def size:Long = {
    val q = quote { queryOrder.map(c => c.id).size }
    ctx.run(q)
  }

  override def clear:Try[OrderStore] = {
    log.info(s"clear: ")
    truncate()
    Success(this)
  }

  def +(order:Order):Try[OrderStoreDB] = { 
    log.info(s"insert: ${order}")
    try {
      ctx.run(queryOrder.insert(lift(order))); 
      Success(this)
    } catch {
      case e:Exception => Failure(new Exception(s"could not insert: ${e}"))
    }
  }

  def -(id:UUID):Try[OrderStoreDB] = { 
    log.info(s"delete: id=${id}")
    try {
      ctx.run(deleteById(lift(id)))
      Success(this)
    } catch {
      case e:Exception => Failure(new Exception(s"could not delete: ${e}"))
    } 
  }
  def -(order:Order):Try[OrderStoreDB] = { this.-(order.id) }

  def get(id:UUID):Option[Order] = {
    log.info(s"get: id=${id}")
    ctx.run(queryOrder.filter(o => o.id == lift(id))) match {
      case h :: _ => Some(h)
      case Nil => None
    }
  }

  def getByItemId(oid:UUID):Seq[Order] = {
    log.info(s"get: oid=${oid}")
    ctx.run(queryOrder
      .filter(o => 
        o.item == lift(oid)
      )) match {
      case h => h
      case Nil => Seq()
    }
  }
}