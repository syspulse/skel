package io.syspulse.skel.db

import scala.jdk.CollectionConverters._

import scala.collection.immutable

import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.util.{Try,Success,Failure}
import akka.actor.ActorSystem
import com.typesafe.scalalogging.Logger
import scala.concurrent.ExecutionContext
import scala.concurrent.Await

import io.jvm.uuid._

import io.syspulse.skel.util.Util
import io.syspulse.skel.cli._
import io.syspulse.skel.cli.user.User

import os._
import java.time.format.DateTimeFormatter
import com.typesafe.config.ConfigFactory
import io.getquill.context.jdbc.JdbcContext
import io.getquill.context.jdbc.JdbcContextBase
import io.getquill.context.Context

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.ResultSet
import java.sql.Statement

case class SessionId(id:UUID = UUID.random)

object Ctx {
  //implicit val system = ActorSystem(Behaviors.empty, "app-cli")
  //implicit val ec = system.executionContext
}

abstract class Ctx(dbUri:String,user:Option[User]=None,sid:SessionId = SessionId()) {
  import Ctx._

  def getUri() = dbUri
}

case class CtxNone(dbUri:String) extends Ctx(dbUri)
case class CtxQuill(dbUri:String,db:Option[JdbcContext[_,_]]=None,user:Option[User]=None) extends Ctx(dbUri,user) {
  override def toString() = s"${db}"
}
case class CtxJdbc(dbUri:String,conn:Option[Connection],user:Option[User]=None) extends Ctx(dbUri,user) {
  override def toString() = s"${conn}"
}


case class CliStateLoggedOff(ctx:Ctx) extends CliState {
  override def render():String = s"${ctx.getUri()}"
}
case class CliStateLoggedIn(ctx:Ctx) extends CliState {
  override def render():String = s"${ctx.getUri()}:${ctx.toString}"
}


class CommandConnect(cli:ShellDB,args:String*) extends Command(cli,args) {

  def connectQuill(dbUri:String,dbUser:String,dbPass:String) = {
    val config = s"""db.dataSourceClassName=com.mysql.cj.jdbc.MysqlDataSource
    db.dataSource.url=${dbUri}
    db.dataSource.user=${dbUser}
    db.dataSource.password=${dbPass}
    db.connectionTimeout=30000
    db.idleTimeout=30000
    db.minimumIdle=5
    db.maximumPoolSize=20
    db.poolName=DB-Pool
    db.maxLifetime=2000000"""

    val typesafeConfig = ConfigFactory
      .parseMap(config.split("\\n")
        .map(s=>{val p=s.split("="); p(0) -> (if(p.size<2) "" else p(1))})
        .toMap.asJava
      )

    import io.getquill._

    // reads from application.properties on CLASSPATH
    //val db = new MysqlJdbcContext(SnakeCase, "db")
    // reads from Config
    val db = new MysqlJdbcContext(SnakeCase, typesafeConfig.getConfig("db"))
    import db._

    db
  }

  def connectJdbc(dbUri:String,dbUser:String,dbPass:String) = {
    val conn = DriverManager.getConnection(dbUri, dbUser, dbPass);
    conn
  }

  def exec(st:CliState):Result = {
    st match {
      case CliStateLoggedOff(ctx) => {
        val (dbLib,dbUri,dbUser,dbPass,dbName) = args.toList match {
          case dbLib :: dbUri :: dbUser :: dbPass :: dbName :: _ => (dbLib,dbUri,dbUser,dbPass,dbName)
          case dbLib :: dbUri :: dbUser :: dbPass :: Nil => (dbLib,dbUri,dbUser,dbPass,"")
          case dbLib :: dbUser :: dbPass :: Nil => (dbLib,ctx.getUri(),dbUser,dbPass,"")
          case dbLib :: dbUri :: Nil => (dbLib,dbUri,"test_user","test_pass","")
          case dbUri :: Nil => ("jdbc",dbUri,"mesar_user","medar_pass","")
          case Nil => ("jdbc", ctx.getUri(),"medar_user","medar_pass","")
        }
        
        val user = User(UUID.random,dbUser,"user-1@email")

        dbLib.trim() match {
          case "quill" => {
            val db = connectQuill(dbUri,dbUser,dbPass)
            OK(s"logged in: ${dbUser}: ${db}",CliStateLoggedIn(CtxQuill(dbUri,Some(db),Some(user))))
          }
          case "jdbc" => {
            val db = connectJdbc(dbUri,dbUser,dbPass)
            OK(s"logged in: ${dbUser}: ${db}",CliStateLoggedIn(CtxJdbc(dbUri,Some(db),Some(user))))
          }
        }
        
      }
      case CliStateLoggedIn(_) => OK("already connected",st)
      case _ => WARN("already connected",st)
    }
  }
}

class CommandQuill(cli:ShellDB,args:String*) extends Command(cli,args) {
  def exec(st:CliState):Result = {
    st match {
      case CliStateLoggedOff(ctx) => ERR(s"not connected",st)
      case CliStateLoggedIn(ctx) => {
        val sql = args.mkString(" ")
        //val r = Await.result(f, Duration("5 seconds"))

        cli.CONSOLE.println(s"Executing: '${sql}'...")

        val r = 
          ctx match {
            case CtxQuill(dbUri, db, user) => Some(db.get.executeQuerySingle(sql))
            case _ => None
          }
        
        val o = r
        OK(s"${o}",st)
      }
      case _ => ERR("in unkown state",st)
    }
  }
}

class CommandSQL(cli:ShellDB,args:String*) extends Command(cli,args) {
  def exec(st:CliState):Result = {
    st match {
      case CliStateLoggedOff(ctx) => ERR(s"not connected",st)
      case CliStateLoggedIn(ctx) => {
        val sql = args.mkString(" ")
        
        cli.CONSOLE.println(s"Executing: '${sql}'...")
        
        val o = ctx match {
          case CtxJdbc(dbUri, db, user) => {
            val statement:Statement = db.get.createStatement()
            val rs:ResultSet = statement.executeQuery(sql)
            
            val metadata = rs.getMetaData()
            val n = metadata.getColumnCount()              

            var i = 0;
            var o = ""
            while(rs.next()) {
              o = o + (1 to n).foldLeft("")(_ + rs.getString(_) + ",") + "\n"
              i = i + 1
            }
            Some(o)
          }
          case _ => None
        }
        
        OK(s"${o}",st)
      }
      case _ => ERR("in unkown state",st)
    }
  }
}


class CommandFutureSleep(cli:ShellDB,args:String*) extends Command(cli,args) {
  def exec(st:CliState):Result = {
    val (time) = args.toList match {
      case msec :: Nil => (msec.toLong)
      case _ => (1000L)
    }

    val f = Future{ 
      Thread.sleep(time)
      val message = s"${this}: FINISHED: ${time} msec"
      cli.uprintln(s"${Thread.currentThread()}: ${this}: ${message}")
      OK(message,st)
    }
    
    FUTURE(f,s"${f}: ${time} msec",st)
  }
}

class CommandSleep(cli:ShellDB,args:String*) extends Command(cli,args) {
  def exec(st:CliState):Result = {
    val (time) = args.toList match {
      case msec :: Nil => (msec.toLong)
      case _ => (1000L)
    }

    Thread.sleep(time)
    OK(s"woke: ${time} msec",st)
  }
}


class ShellDB(dbUri:String) extends Cli(initState = CliStateLoggedOff(CtxNone(dbUri)) ) {
  
  val tsFormat = "yyyy-MM-dd HH:mm:ss" //DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
  
  addSyntax(Seq (
    Syntax(words=Seq("connect","c"),cmd = (cli,args)=>new CommandConnect(this,args: _*),help="Connect to db: <type> <uri> <user> <pass> (type = [jdbc,quill]"),
    Syntax(words=Seq("quill","q"),cmd = (cli,args)=>new CommandQuill(this,args: _*),help="Quill statement (quill): quill <sql>"),
    Syntax(words=Seq("sql","s"),cmd = (cli,args)=>new CommandSQL(this,args: _*),help="SQL statement (jdbc): sql <sql>"),
    Syntax(words=Seq("future","fut","f"),cmd = (cli,args)=>new CommandFutureSleep(this,args: _*),help="Create non-blocking Future (def: 1000 msec)"),
    Syntax(words=Seq("sleep"),cmd = (cli,args)=>new CommandSleep(this,args: _*),help="Blocking sleep (def: 1000 msec)"),
  
  ))

  def uri:String = dbUri
}
