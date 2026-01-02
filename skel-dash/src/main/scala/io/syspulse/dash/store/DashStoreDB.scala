package io.syspulse.dash.store

import scala.util.{Failure,Success,Try}
import scala.collection.immutable
import com.typesafe.scalalogging.Logger
import io.jvm.uuid.UUID
import java.time.LocalDateTime

import io.getquill._
import io.getquill.context._

import io.syspulse.skel.config.{Configuration}
import io.syspulse.skel.store.{Store,StoreDB}

import io.syspulse.dash.server._
import io.syspulse.dash.Dash

import spray.json._
import io.syspulse.dash.server.DashJson._

class DashStoreDB(configuration:Configuration,dbConfigRef:String) 
  extends StoreDB[Dash,String](dbConfigRef,"dash",Some(configuration)) 
  with DashStore {

  lazy private val log = Logger(getClass)
  
  def id:String = "db"

  import ctx._  
  lazy protected  val table = dynamicQuerySchema[Dash](tableName)
  
  def indexPid = "dash_pid"
  def indexTid = "dash_tid"

  // ATTENTION: called from constructor, so derived class vals are not initialized yet !
  def create:Try[Long] = {
    val CREATE_INDEX_PID_MYSQL_SQL = s"CREATE INDEX ${indexPid} ON ${tableName} (pid);"
    val CREATE_INDEX_PID_POSTGRES_SQL = s"CREATE INDEX IF NOT EXISTS ${indexPid} ON ${tableName} (pid);"
    val CREATE_INDEX_TID_MYSQL_SQL = s"CREATE INDEX ${indexTid} ON ${tableName} (tid);"
    val CREATE_INDEX_TID_POSTGRES_SQL = s"CREATE INDEX IF NOT EXISTS ${indexTid} ON ${tableName} (tid);"
    
    val CREATE_INDEX_PID_SQL = getDbType match {
      case "mysql" => CREATE_INDEX_PID_MYSQL_SQL
      case "postgres" => CREATE_INDEX_PID_POSTGRES_SQL
    }

    val CREATE_INDEX_TID_SQL = getDbType match {
      case "mysql" => CREATE_INDEX_TID_MYSQL_SQL
      case "postgres" => CREATE_INDEX_TID_POSTGRES_SQL
    }

    // ATTENTION: 1MB Blob VARCHAR
    val CREATE_TABLE_MYSQL_SQL = 
      s"""CREATE TABLE IF NOT EXISTS ${tableName} (
        id VARCHAR(36) PRIMARY KEY, 
        layout VARCHAR(65000),
        name VARCHAR(250),
        info VARCHAR(1024),
        tags VARCHAR(200),
        pid VARCHAR(36), 
        tid VARCHAR(36),
        ts BIGINT,
        ts0 BIGINT,
        status INT
      );
      """

    val CREATE_TABLE_POSTGRES_SQL = 
      s"""CREATE TABLE IF NOT EXISTS ${tableName} (
        id VARCHAR(36) PRIMARY KEY, 
        layout VARCHAR(65000),
        name VARCHAR(250),
        info VARCHAR(1024),
        tags VARCHAR(200),
        pid VARCHAR(36), 
        tid VARCHAR(36),
        ts BIGINT,
        ts0 BIGINT,
        status INT
      );
      """

    val CREATE_TABLE_SQL = getDbType match {
      case "mysql" => CREATE_TABLE_MYSQL_SQL
      case "postgres" => CREATE_TABLE_POSTGRES_SQL
    }
        
    val r1 = try {      
      log.info(s"table: '${tableName}': ${getDbType}: '${CREATE_TABLE_SQL.replaceAll("\\s+"," ")}'")
      val r1 = ctx.executeAction(CREATE_TABLE_SQL)(ExecutionInfo.unknown, ())
      log.info(s"table: '${tableName}': created: ${r1}")    
      Success(r1)
    } catch {
      case e:Exception => { 
        // short name without full stack (change to check for duplicate index)
        // remove ERROR to avpid kubernetes treating it as ERROR
        log.error(s"failed to create table: '${tableName}': ${e.getMessage()}")
        Failure(e) 
      }
    }

    createIndex(indexPid,CREATE_INDEX_PID_SQL)
    createIndex(indexTid,CREATE_INDEX_TID_SQL)

    r1
  }

  def createIndex(indexName:String,indexSQL:String) = {
    try {
      log.info(s"index: '${indexName}': ${getDbType}: '${indexSQL.replaceAll("\\s+"," ")}'")
      val r = ctx.executeAction(indexSQL)(ExecutionInfo.unknown, ())
      log.info(s"index: '${indexName}': created: ${r}")

      Success(r)
    } catch {
      case e1:org.postgresql.util.PSQLException if(e1.getMessage.contains("already exists")) => {
        log.info(s"index: '${indexName}': ${e1.getMessage().replaceFirst("ERROR: ","")}")
        Success(0)
      }
      case e:Exception => { 
        // short name without full stack (change to check for duplicate index)
        // remove ERROR to avpid kubernetes treating it as ERROR
        log.warn(s"failed to create index: '${indexName}': ${e.getMessage().replaceFirst("ERROR: ","")}")
        Failure(e) 
      }
    }
  }

  // if conversion is needed
  def toDash(d:Dash):Dash = {
    d
  }

  def fromDash(d:Dash):Dash = {
    d
  }
    
  def all(tid:Option[String],pid:Option[String]):Seq[Dash] = {
    val rr = 
      if(pid.isDefined && tid.isDefined)
        ctx.run(query[Dash].filter(ad => ad.pid == lift(pid) && ad.tid == lift(tid)))
      else if(pid.isDefined)
        ctx.run(query[Dash].filter(ad => ad.pid == lift(pid)))
      else if(tid.isDefined)
        ctx.run(query[Dash].filter(ad => ad.tid == lift(tid)))
      else
        ctx.run(query[Dash])
    
    log.debug(s"all: pid=${tid}/${pid}: ${rr}")
    rr.map(r => toDash(r))
  }

  def ??(id:String):Option[Dash] = ???(id,None,None).toOption

  // ATTENTION: tid is currently not used, since pid is unique !
  def ???(id:String,tid:Option[String],pid:Option[String]): Try[Dash] = {
    log.debug(s"SELECT: ${tid}/${pid}/${id}")
    try { 
      val rr = if(pid.isDefined)
        ctx.run(query[Dash].filter(w => w.id == lift(id) && w.pid == lift(pid)))
      else
        ctx.run(query[Dash].filter(w => w.id == lift(id)))

      rr match {
      //ctx.run(table.filter(w => w.addr == lift(addr))) match {      
        case h :: _ => Success(toDash(h))
        case Nil => Failure(new Exception(s"not found: '${pid}/${id}'"))
      }
    } catch {
      case e:Exception => Failure(e)
    }
  }

  val deleteById = quote { (id:String,pid:Option[String]) => 
    query[Dash].filter(o => o.id == id && o.pid == pid).delete    
  }   

  def +++(ad:Dash):Try[Dash] = { 
    log.info(s"UPSERT: ${ad.tid}/${ad.pid}/${ad.id}")
    try {
      val q = quote {
        query[Dash].insertValue(lift(ad)).onConflictUpdate(_.id)(
          (t, e) => t.layout -> e.layout,
          (t, e) => t.name -> e.name,
          (t, e) => t.info -> e.info,
          (t, e) => t.tags -> e.tags,
          (t, e) => t.pid -> e.pid,
          (t, e) => t.tid -> e.tid,
          (t, e) => t.ts -> e.ts,
          (t, e) => t.ts0 -> e.ts0
        )
      }
      val r = ctx.run(q)   
      log.info(s"UPSERT: ${r}")
      Success(ad)
    } catch {
      case e:Exception => Failure(new Exception(s"could not insert: ${ad.tid}/${ad.pid}/${ad.id}",e))
    }
  }

  def +(c:Dash):Try[Dash] = {
    val now = System.currentTimeMillis()
    val ad = fromDash(c)
    +++(ad).map(_ => c)
  }

  def del(id:String):Try[String] = del(id,None,None).map(_ => id)
  
  // ATTENTION: tid is currently not used, since pid is unique !
  def del(id:String,tid:Option[String],pid:Option[String]):Try[String] = { 
    log.info(s"DELETE: ${tid}/${pid}/${id}")
    try {
      ctx.run(deleteById(lift(id),lift(pid)))
      match {
        case 0 => Failure(new Exception(s"not found: ${pid}/${id}"))
        case _ => 
          Success(id)
      } 

    } catch {
      case e:Exception => Failure(e)
    } 
  }

  def size(tid:Option[String],pid:Option[String]):Long = {
    if(pid.isDefined && tid.isDefined) {
      ctx.run(query[Dash].filter(p => p.pid == lift(pid) && p.tid == lift(tid)).size)
    } 
    else if(pid.isDefined) {
      ctx.run(query[Dash].filter(p => p.pid == lift(pid)).size)
      ctx.run(totalSQL())
    }
    else if(tid.isDefined) {
      ctx.run(query[Dash].filter(p => p.tid == lift(tid)).size)
      ctx.run(totalSQL())
    } else {
      super.size
    }
  }
  
}
