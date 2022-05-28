package io.syspulse.skel.world.country

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

class CountryStoreDB extends StoreDB[Country]("db","country") with CountryStore {

  import ctx._

  def create:Try[Long] = {
    ctx.executeAction(s"CREATE TABLE IF NOT EXISTS ${tableName} (id VARCHAR(36) PRIMARY KEY, name VARCHAR(128), iso VARCHAR(5), native VARCHAR(128) );")
    // why do we still use MySQL which does not even support INDEX IF NOT EXISTS ?...
    //val r = ctx.executeAction("CREATE INDEX IF NOT EXISTS country_name ON country (name);")
    try {
      val r = ctx.executeAction(s"CREATE INDEX country_name ON ${tableName} (name);")
      Success(r)
    } catch {
      case e:Exception => { 
        // short name without full stack (change to check for duplicate index)
        log.warn(s"failed to create index: ${e.getMessage()}"); Success(0) 
      }
    }
  }
  
  def getAll:Seq[Country] = ctx.run(query[Country])
  
  val deleteById = quote { (id:UUID) => 
    query[Country].filter(o => o.id == id).delete
  } 

  def load:Seq[Country] = {
    val cc = CountryLoader.fromResource()
    log.info(s"load: ${cc.size}")
    try {
      cc.foreach( c => ctx.run(query[Country].insert(lift(c))))
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
    val q = quote { query[Country].map(c => c.id).size }
    ctx.run(q)
  }

  override def clear:Try[CountryStore] = {
    log.info(s"clear: ")
    truncate()
    Success(this)
  }

  def +(country:Country):Try[CountryStoreDB] = { 
    log.info(s"insert: ${country}")
    try {
      ctx.run(query[Country].insert(lift(country))); 
      Success(this)
    } catch {
      case e:Exception => Failure(new Exception(s"could not insert: ${e}"))
    }
  }

  def -(id:UUID):Try[CountryStoreDB] = { 
    log.info(s"delete: id=${id}")
    try {
      ctx.run(deleteById(lift(id)))
      Success(this)
    } catch {
      case e:Exception => Failure(new Exception(s"could not delete: ${e}"))
    } 
  }
  def -(country:Country):Try[CountryStoreDB] = { this.-(country.id) }

  def get(id:UUID):Option[Country] = {
    log.info(s"get: id=${id}")
    ctx.run(query[Country].filter(o => o.id == lift(id))) match {
      case h :: _ => Some(h)
      case Nil => None
    }
  }

  def getByName(name:String):Option[Country] = {
    log.info(s"get: name=${name}")
    if(name.size==2) {
      ctx.run(query[Country]
        .filter(o => 
          o.iso.toLowerCase == lift(name.toLowerCase())
        )) match {
        case h :: _ => Some(h)
        case Nil => None
      }
    }else {
      ctx.run(query[Country]
        .filter(o => 
          o.name.toLowerCase == lift(name.toLowerCase())
        )) match {
        case h :: _ => Some(h)
        case Nil => None
      }
    }
  }

}