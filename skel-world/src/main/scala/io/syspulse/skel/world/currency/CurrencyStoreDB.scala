package io.syspulse.skel.world.currency

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

class CurrencyStoreDB extends StoreDB[Currency]("db","currency") with CurrencyStore {

  import ctx._

  def create:Try[Long] = {
    ctx.executeAction(s"CREATE TABLE IF NOT EXISTS ${tableName} (id VARCHAR(36) PRIMARY KEY, name VARCHAR(128), code VARCHAR(5), num_code INTEGER, country VARCHAR(128) );")
    // why do we still use MySQL which does not even support INDEX IF NOT EXISTS ?...
    //val r = ctx.executeAction("CREATE INDEX IF NOT EXISTS currency_name ON currency (name);")
    try {
      val r = ctx.executeAction(s"CREATE INDEX currency_name ON ${tableName} (name);")
      Success(r)
    } catch {
      case e:Exception => { 
        log.warn(s"failed to create index: ${e.getMessage()}"); Success(0) 
      }
    }

    try {
      val r = ctx.executeAction(s"CREATE INDEX currency_code ON ${tableName} (code);")
      Success(r)
    } catch {
      case e:Exception => { 
        log.warn(s"failed to create index: ${e.getMessage()}"); Success(0) 
      }
    }
  }
  
  def getAll:Seq[Currency] = ctx.run(query[Currency])
  
  val deleteById = quote { (id:UUID) => 
    query[Currency].filter(o => o.id == id).delete
  } 

  def load:Seq[Currency] = {
    val cc = CurrencyLoader.fromResource()
    log.info(s"load: ${cc.size}")
    try {
      cc.foreach( c => ctx.run(query[Currency].insert(lift(c))))
      cc
    } catch {
      case e:Exception => {
        log.warn(s"could not load: ${e.getMessage()}")
        Seq()
        //Failure(new Exception(s"could not reload: ${e}"))
      }
    }
  }

  override def size:Long = {
    val q = quote { query[Currency].map(c => c.id).size }
    ctx.run(q)
  }

  override def clear:Try[CurrencyStore] = {
    log.info(s"clear: ")
    truncate()
    Success(this)
  }

  def +(currency:Currency):Try[CurrencyStoreDB] = { 
    log.info(s"insert: ${currency}")
    try {
      ctx.run(query[Currency].insert(lift(currency))); 
      Success(this)
    } catch {
      case e:Exception => Failure(new Exception(s"could not insert: ${e}"))
    }
  }

  def -(id:UUID):Try[CurrencyStoreDB] = { 
    log.info(s"delete: id=${id}")
    try {
      ctx.run(deleteById(lift(id)))
      Success(this)
    } catch {
      case e:Exception => Failure(new Exception(s"could not delete: ${e}"))
    } 
  }
  def -(currency:Currency):Try[CurrencyStoreDB] = { this.-(currency.id) }

  def get(id:UUID):Option[Currency] = {
    log.info(s"get: id=${id}")
    ctx.run(query[Currency].filter(o => o.id == lift(id))) match {
      case h :: _ => Some(h)
      case Nil => None
    }
  }

  def getByName(name:String):Option[Currency] = {
    log.info(s"get: name=${name}")
    if(name.size==3) {
      ctx.run(query[Currency]
        .filter(o => 
          o.code.toLowerCase == lift(name.toLowerCase())
        )) match {
        case h :: _ => Some(h)
        case Nil => None
      }
    }else {
      ctx.run(query[Currency]
        .filter(o => 
          o.name.toLowerCase == lift(name.toLowerCase())
        )) match {
        case h :: _ => Some(h)
        case Nil => None
      }
    }
  }

}