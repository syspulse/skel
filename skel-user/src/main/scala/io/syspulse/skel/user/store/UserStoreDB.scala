package io.syspulse.skel.user.store

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

import io.syspulse.skel.config.{Configuration}
import io.syspulse.skel.store.{Store,StoreDB}

import io.syspulse.skel.user.User

// Postgres does not support table name 'user' !
class UserStoreDB(configuration:Configuration,dbConfigRef:String) extends StoreDB[User,UUID](dbConfigRef,"users",Some(configuration)) with UserStore {

  import ctx._
  
  // Because of Postgres, using dynamic schema to override table name to 'users' 
  val table = dynamicQuerySchema[User](tableName)

  def create:Try[Long] = {
    ctx.executeAction(
    s"""CREATE TABLE IF NOT EXISTS ${tableName} (
      id VARCHAR(36) PRIMARY KEY, 
      email VARCHAR(255), 
      name VARCHAR(255),
      xid VARCHAR(255),
      avatar VARCHAR(255),
      ts_created BIGINT
    );
    """
    )

    // why do we still use MySQL which does not even support INDEX IF NOT EXISTS ?...
    //val r = ctx.executeAction("CREATE INDEX IF NOT EXISTS user_name ON user (name);")
    try {
      val r = ctx.executeAction(s"CREATE INDEX user_name ON ${tableName} (name);")
      Success(r)
    } catch {
      case e:Exception => { 
        // short name without full stack (change to check for duplicate index)
        log.warn(s"failed to create index: ${e.getMessage()}"); Success(0) 
      }
    }
  }


  //def all:Seq[User] = ctx.run(query[User])
  def all:Seq[User] = ctx.run(table)

  // val deleteById = quote { (id:UUID) => 
  //   query[User].filter(o => o.id == id).delete    
  // } 
  val deleteById = (id:UUID) => table.filter(_.id == lift(id)).delete

  def +(user:User):Try[UserStoreDB] = { 
    log.info(s"insert: ${user}")
    try {
      //ctx.run(query[User].insert(lift(user)));
      ctx.run(table.insertValue(user.copy(email = user.email.toLowerCase)));
      Success(this)
    } catch {
      case e:Exception => Failure(new Exception(s"could not insert: ${e}"))
    }
  }

  def update(id:UUID,email:Option[String]=None,name:Option[String]=None,avatar:Option[String]=None):Try[User] = {
    this.?(id) match {
      case Success(user) => 
        val user1 = modify(user,email,name,avatar)
        try {
          ctx.run(table.updateValue(user1));
          Success(user1)
        } catch {
          case e:Exception => Failure(new Exception(s"could not update: ${e}"))
        }        
      case f => f
    }
  }

  def del(id:UUID):Try[UserStoreDB] = { 
    log.info(s"delete: id=${id}")
    try {
      //ctx.run(deleteById(lift(id)))
      ctx.run(deleteById(id))
      Success(this)
    } catch {
      case e:Exception => Failure(new Exception(s"could not delete: ${e}"))
    } 
  }

  //override def -(user:User):Try[UserStoreDB] = { this.del(user.id) }

  def ?(id:UUID):Try[User] = {
    log.info(s"select: id=${id}")
    //ctx.run(query[User].filter(o => o.id == lift(id))) match {
    ctx.run(table.filter(o => o.id == lift(id))) match {      
      case h :: _ => Success(h)
      case Nil => Failure(new Exception(s"not found: ${id}"))
    }
  }

  def findByXid(xid:String):Option[User] = {
    log.info(s"find: xid=${xid}")
    //ctx.run(query[User].filter(o => o.xid == lift(xid))) match {
    ctx.run(table.filter(o => o.xid == lift(xid))) match {
      case h :: _ => Some(h)
      case Nil => None
    }
  }

  def findByEmail(email:String):Option[User] = {
    log.info(s"find: emai=${email}")
    ctx.run(table.filter(o => o.email == lift(email.toLowerCase()))) match {
      case h :: _ => Some(h)
      case Nil => None
    }
  }

}