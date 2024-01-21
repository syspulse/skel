package io.syspulse.skel.user.store

import scala.util.{Try,Success,Failure}
import scala.concurrent.Future

import io.jvm.uuid._

import io.getquill._
import io.getquill.context._

import scala.jdk.CollectionConverters._
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.config.{Configuration}
import io.syspulse.skel.store.{Store,StoreDB}

import io.syspulse.skel.user.User

// Postgres does not support table name 'user' !
class UserStoreDB(configuration:Configuration,dbConfigRef:String) 
  extends StoreDB[User,UUID](dbConfigRef,"users",Some(configuration)) 
  with UserStore {

  import ctx._
  
  // Because of Postgres, using dynamic schema to override table name to 'users' 
  val table = dynamicQuerySchema[User](tableName)
  
  def indexUserName = "user_name"

  // ATTENTION: called from constructor, so derived class vals are not initialized yet !
  def create:Try[Long] = {    
    // val createTableMySqlSQL = () => quote {     
    //   infix"""CREATE TABLE IF NOT EXISTS ${lift(tableName)} (
    //     id VARCHAR(36) PRIMARY KEY, 
    //     email VARCHAR(255), 
    //     name VARCHAR(255),
    //     xid VARCHAR(255),
    //     avatar VARCHAR(255),
    //     ts_created BIGINT
    //   );
    //   """.as[Long]
    // }

    // val createTablePostgresSQL = () => quote {     
    //   infix"""CREATE TABLE IF NOT EXISTS ${lift(tableName)} (
    //     id UUID PRIMARY KEY, 
    //     email VARCHAR(255), 
    //     name VARCHAR(255),
    //     xid VARCHAR(255),
    //     avatar VARCHAR(255),
    //     ts_created BIGINT
    //   );
    //   """.as[Long]
    // }

    // val createIndexSQL = () => quote {
    //   infix"CREATE INDEX user_name ON ${lift(tableName)} (name);".as[Long]
    // }

    // val createTableSQL = getDbType match {
    //   case "mysql" => createTableMySqlSQL
    //   case "postgres" => createTablePostgresSQL
    // }

    val CREATE_INDEX_MYSQL_SQL = s"CREATE INDEX ${indexUserName} ON ${tableName} (name);"
    val CREATE_INDEX_POSTGRES_SQL = s"CREATE INDEX IF NOT EXISTS ${indexUserName} ON ${tableName} (name);"    
    
    val CREATE_INDEX_SQL = getDbType match {
      case "mysql" => CREATE_INDEX_MYSQL_SQL
      case "postgres" => CREATE_INDEX_POSTGRES_SQL
    }

    val CREATE_TABLE_MYSQL_SQL = 
      s"""CREATE TABLE IF NOT EXISTS ${tableName} (
        id VARCHAR(36) PRIMARY KEY, 
        email VARCHAR(255), 
        name VARCHAR(255),
        xid VARCHAR(255),
        avatar VARCHAR(255),
        ts_created BIGINT
      );
      """

    val CREATE_TABLE_POSTGRES_SQL = 
      s"""CREATE TABLE IF NOT EXISTS ${tableName} (
        id UUID PRIMARY KEY, 
        email VARCHAR(255), 
        name VARCHAR(255),
        xid VARCHAR(255),
        avatar VARCHAR(255),
        ts_created BIGINT
      );
      """

    val CREATE_TABLE_SQL = getDbType match {
      case "mysql" => CREATE_TABLE_MYSQL_SQL
      case "postgres" => CREATE_TABLE_POSTGRES_SQL
    }
    
    // why do we still use MySQL which does not even support INDEX IF NOT EXISTS ?...    
    try {
      // val r1 = ctx.run(createTableSQL())
      // val r2 = ctx.run(createIndexSQL())      

      val r1 = ctx.executeAction(CREATE_TABLE_SQL)(ExecutionInfo.unknown, ())
      log.info(s"table: ${tableName}: ${r1}")
      val r2 = ctx.executeAction(CREATE_INDEX_SQL)(ExecutionInfo.unknown, ())
      log.info(s"index: ${indexUserName}: ${r2}")

      Success(r1)
    } catch {
      case e:Exception => { 
        // short name without full stack (change to check for duplicate index)
        log.warn(s"failed to create: ${e.getMessage()}"); 
        Failure(e) 
      }
    }
  }

  //def all:Seq[User] = ctx.run(query[User])
  def all:Seq[User] = ctx.run(table)

  // val deleteById = quote { (id:UUID) => 
  //   query[User].filter(o => o.id == id).delete    
  // } 
  val deleteById = (id:UUID) => table.filter(_.id == lift(id)).delete

  def +(user:User):Try[User] = { 
    log.info(s"INSERT: ${user}")
    try {
      //ctx.run(query[User].insertValue(lift(user)));
      ctx.run(table.insertValue(user.copy(email = user.email.toLowerCase)));
      Success(user)
    } catch {
      case e:Exception => Failure(new Exception(s"could not insert: ${e}"))
    }
  }

  def update(id:UUID,email:Option[String]=None,name:Option[String]=None,avatar:Option[String]=None):Try[User] = {
    this.?(id) match {
      case Success(user) =>
        val user1 = modify(user,email,name,avatar)

        log.info(s"UPDATE: ${user1}")
        try {
          val q = 
            table
              .filter(u => u.id == lift(id))
              .update(
                set(_.name, quote(lift(user1.name))),
                set(_.email, quote(lift(user1.email))),
                set(_.avatar, quote(lift(user1.avatar)))
              )
          
          ctx.run(q)

          //Success(user1)
          // query again
          this.?(id)

        } catch {
          case e:Exception => Failure(new Exception(s"could not update: ${e}"))
        }
      case f => f
  }}

  def del(id:UUID):Try[UUID] = { 
    log.info(s"DELETE: id=${id}")
    try {
      //ctx.run(deleteById(lift(id)))
      ctx.run(deleteById(id)) match {
        case 0 => Failure(new Exception(s"not found: ${id}"))
        case _ => Success(id)
      } 
      
    } catch {
      case e:Exception => Failure(new Exception(s"could not delete: ${e}"))
    } 
  }

  //override def -(user:User):Try[UserStoreDB] = { this.del(user.id) }

  def ?(id:UUID):Try[User] = {
    log.info(s"SELECT: id=${id}")
    //ctx.run(query[User].filter(o => o.id == lift(id))) match {
    try { 
      ctx.run(table.filter(o => o.id == lift(id))) match {      
        case h :: _ => Success(h)
        case Nil => Failure(new Exception(s"not found: ${id}"))
      }
    } catch {
      case e:Exception => Failure(e)
    }
  }

  def findByXid(xid:String):Option[User] = {
    log.info(s"FIND: xid=${xid}")
    //ctx.run(query[User].filter(o => o.xid == lift(xid))) match {
    ctx.run(table.filter(o => o.xid == lift(xid))) match {
      case h :: _ => Some(h)
      case Nil => None
    }
  }

  def findByEmail(email:String):Option[User] = {
    log.info(s"FIND: emai=${email}")
    ctx.run(table.filter(o => o.email == lift(email.toLowerCase()))) match {
      case h :: _ => Some(h)
      case Nil => None
    }
  }

  // Async ======================================================================================================================
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  override def sizeAsync:Future[Long] = Future{ size }
  override def allAsync:Future[Seq[User]] = Future{ all }
  override def +!(user:User):Future[User] = Future{ this.+(user).get }
  def updateAsync(id:UUID,email:Option[String]=None,name:Option[String]=None,avatar:Option[String]=None):Future[User] = Future {
    update(id,email,name,avatar).get
  }
  override def delAsync(id:UUID):Future[UUID] = Future { del(id).get }       
  override def ?!(id:UUID):Future[User] = Future { this.?(id).get }    
  def findByXidAsync(xid:String):Future[User] = Future { findByXid(xid).get }
  def findByEmailAsync(email:String):Future[User] = Future { findByEmail(email).get }

}