package io.syspulse.skel.user.store

import scala.util.Try
import scala.util.{Success,Failure}

import io.jvm.uuid._

import io.getquill._
import io.getquill.context._

import scala.jdk.CollectionConverters._
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.config.{Configuration}
import io.syspulse.skel.store.{Store,StoreDB,StoreDBAsync}

import io.syspulse.skel.user.User
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.Await
import scala.concurrent.Future


// Postgres does not support table name 'user' !
class UserStoreDBAsync(configuration:Configuration,dbConfigRef:String) 
  extends StoreDBAsync[User,UUID](dbConfigRef,"users",Some(configuration)) 
  //with UserStoreAsync {
  with UserStore {

  private val log = Logger(getClass)
  import ctx._
  
  // Because of Postgres, using dynamic schema to override table name to 'users' 
  val table = dynamicQuerySchema[User](tableName)

  def indexUserName = "user_name"
  
  // ATTENTION: called from constructor, so derived class vals are not initialized yet !
  def create:Try[Long] = {
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

    try {
      val f1 = ctx.executeAction(CREATE_TABLE_SQL)(ExecutionInfo.unknown, ())
      val r1 = Await.result(f1,FiniteDuration(10000L,TimeUnit.MILLISECONDS))
      log.info(s"table: ${tableName}: ${r1}")

      val f2 = ctx.executeAction(CREATE_INDEX_SQL)(ExecutionInfo.unknown, ())
      val r2 = Await.result(f2,FiniteDuration(10000L,TimeUnit.MILLISECONDS))
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


  override def allAsync:Future[Seq[User]] = ctx.run(table)

  override def +!(user:User):Future[User] = { 
    log.info(s"INSERT: ${user}")
    ctx.run(table.insertValue(user.copy(email = user.email.toLowerCase))).map(_ => user)
  }

  def updateAsync(id:UUID,email:Option[String]=None,name:Option[String]=None,avatar:Option[String]=None):Future[User] = {
    for {
      user <- this.?!(id)
      user1 <- {
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
          this.?!(id)

        } catch {
          case e:Exception => throw new Exception(s"could not update: ${e}")
        }
      }
    } yield user1
  }

  // val deleteById = quote { (id:UUID) => 
  //   query[User].filter(o => o.id == id).delete    
  // } 
  val deleteById = (id:UUID) => table.filter(_.id == lift(id)).delete

  override def delAsync(id:UUID):Future[UUID] = { 
    log.info(s"DELETE: id=${id}")
    ctx.run(deleteById(id)).map(r => r match {
      case 0 => throw new Exception(s"not found: ${id}")
      case _ => id
    })
  }
  
  override def ?!(id:UUID):Future[User] = {
    log.info(s"SELECT: id=${id}")
    ctx.run(table.filter(o => o.id == lift(id))).map(r => r.headOption match {
      case Some(u) => u
      case None => throw new Exception(s"user not found: ${id}")
    })
  }

  def findByXidAsync(xid:String):Future[User] = {
    log.info(s"FIND: xid=${xid}")
    ctx.run(table.filter(o => o.xid == lift(xid))).map(r => r.headOption match {
      case Some(u) => u
      case None => throw new Exception(s"user not found: ${xid}")
    })
  }

  def findByEmailAsync(email:String):Future[User] = {
    log.info(s"FIND: emai=${email}")
    ctx.run(table.filter(o => o.email == lift(email.toLowerCase()))).map(r => r.headOption match {
      case Some(u) => u
      case None => throw new Exception(s"user not found: ${email}")
    })
  }
  

  // === Sync ================================================================================================
  def +(user:User):Try[User] = Store.fromFuture(this.+!(user))
  def del(id:UUID):Try[UUID] = Store.fromFuture(this.delAsync(id))
  def ?(id:UUID):Try[User] = Store.fromFuture(this.?!(id))
  def all:Seq[User] = Await.result(this.allAsync,FiniteDuration(15000L,TimeUnit.MILLISECONDS))
  def size:Long = Await.result(this.sizeAsync,FiniteDuration(15000L,TimeUnit.MILLISECONDS))
  def findByXid(xid:String):Option[User] = Store.fromFuture(this.findByXidAsync(xid)).toOption
  def findByEmail(email:String):Option[User] = Store.fromFuture(this.findByEmailAsync(email)).toOption
  def update(id:UUID, email:Option[String] = None, name:Option[String] = None, avatar:Option[String] = None):Try[User] = 
    Store.fromFuture(this.updateAsync(id,email,name,avatar))
}