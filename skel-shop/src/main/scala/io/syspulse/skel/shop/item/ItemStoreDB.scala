package io.syspulse.skel.shop.item

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

class ItemStoreDB extends StoreDB[Item]("db","item") with ItemStore {

  import ctx._

  def create:Try[Long] = {
    ctx.executeAction(
      s"CREATE TABLE IF NOT EXISTS ${tableName} (id VARCHAR(36) PRIMARY KEY, sid SERIAL NOT NULL, ts TIMESTAMP(6) NOT NULL, name VARCHAR(128), count NUMERIC, price NUMERIC );"
    )
    // why do we still use MySQL which does not even support INDEX IF NOT EXISTS ?...
    //val r = ctx.executeAction("CREATE INDEX IF NOT EXISTS item_name ON item (name);")
    try {
      ctx.executeAction(s"CREATE INDEX item_ts ON ${tableName} (ts);")
      ctx.executeAction(s"CREATE INDEX item_name ON ${tableName} (name);")
      Success(0)
    } catch {
      case e:Exception => { 
        // short name without full stack (change to check for duplicate index)
        log.warn(s"failed to create index: ${e.getMessage()}"); Success(0) 
      }
    }
  }
  
  def getAll:Seq[Item] = ctx.run(query[Item])
  
  val deleteById = quote { (id:UUID) => 
    query[Item].filter(o => o.id == id).delete
  } 

  def load:Seq[Item] = {
    val cc = ItemLoader.fromResource()
    log.info(s"load: ${cc.size}")
    try {
      cc.foreach( c => ctx.run(query[Item].insert(lift(c))))
      cc
    } catch {
      case e:Exception => {
        log.warn(s"could not load: ${e.getMessage()}")
        Seq()
      }
    }
  }

  override def size:Long = {
    val q = quote { query[Item].map(c => c.id).size }
    ctx.run(q)
  }

  override def clear:Try[ItemStore] = {
    log.info(s"clear: ")
    truncate()
    Success(this)
  }

  def +(item:Item):Try[ItemStoreDB] = { 
    log.info(s"insert: ${item}")
    try {
      ctx.run(query[Item].insert(lift(item))); 
      Success(this)
    } catch {
      case e:Exception => Failure(new Exception(s"could not insert: ${e}"))
    }
  }

  def -(id:UUID):Try[ItemStoreDB] = { 
    log.info(s"delete: id=${id}")
    try {
      ctx.run(deleteById(lift(id)))
      Success(this)
    } catch {
      case e:Exception => Failure(new Exception(s"could not delete: ${e}"))
    } 
  }
  def -(item:Item):Try[ItemStoreDB] = { this.-(item.id) }

  def get(id:UUID):Option[Item] = {
    log.info(s"get: id=${id}")
    ctx.run(query[Item].filter(o => o.id == lift(id))) match {
      case h :: _ => Some(h)
      case Nil => None
    }
  }

  def getByName(name:String):Option[Item] = {
    log.info(s"get: name=${name}")
    ctx.run(query[Item]
      .filter(o => 
        o.name.toLowerCase == lift(name.toLowerCase())
      )) match {
      case h :: _ => Some(h)
      case Nil => None
    }
  }
}