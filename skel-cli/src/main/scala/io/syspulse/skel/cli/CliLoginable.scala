package io.syspulse.skel.cli

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
import io.syspulse.skel.crypto.Eth


import os._
import java.time.format.DateTimeFormatter
import io.syspulse.skel.crypto.wallet.Signer

case class SessionId(id:UUID = UUID.random)

object Ctx {
  //implicit val system = ActorSystem(Behaviors.empty, "app-cli")
  //implicit val ec = system.executionContext
}

case class Ctx(uri:String,user:Option[User]=None,sid:SessionId = SessionId()) {
  import Ctx._
  //val medarClient = new CliLoginableentHttp(uri,signer)
  
}


case class CliStateLoggedOff(ctx:Ctx) extends CliState {
  override def render():String = s"${ctx.uri}"
}
case class CliStateLoggedIn(ctx:Ctx) extends CliState {
  override def render():String = s"${ctx.uri}:${ctx.sid}"
}

class CommandConnect(cli:CliLoginable,args:String*) extends Command(cli,args) {
  def exec(st:CliState):Result = {
    st match {
      case CliStateLoggedOff(ctx)  => {
        val (uri) = args.toList match {
          case uri :: _ => (uri)
          case Nil => return ERR("missing uri",st) 
        }
        OK(s"connected: ${uri}",CliStateLoggedOff(ctx.copy(uri = uri)))
      }
      case CliStateLoggedIn(_) => OK("already logged-in",st)
      case _ => WARN("already logged-in",st)
    }
  }
}


class CommandLoginUser(cli:CliLoginable,args:String*) extends Command(cli,args) {
  val DEF_KEYSTORE = (os.home / ".config" / "skel" / "keystore.json").toString
  def exec(st:CliState):Result = {
    st match {
      case CliStateLoggedOff(ctx) => {
        val (userId,kk) = args.toList match {
          case userId :: pass :: keystoreFile :: _ => (userId,Eth.readKeystore(pass,keystoreFile))
          case userId :: pass :: Nil => (userId,Eth.readKeystore(pass,DEF_KEYSTORE))
          case userId :: Nil => (userId,Success(Eth.generateRandom()))
          case Nil => ("00000000-0000-0000-1000-000000000001",Success(Eth.generateRandom()))
          //case Nil => return ERR("missing uri,userId,password",st) 
        }
        
        if(kk.isFailure) {
          return ERR(s"could not load keystore: ${kk}",CliStateLoggedOff(ctx))
        }
        
        val signer = Signer(UUID(userId),kk.get._1,kk.get._2)
        val user = User(UUID(userId),"user-1","user-1@email",List(signer))
        
        OK(s"logged in: ${user}",CliStateLoggedIn(Ctx(ctx.uri,Some(user))))
      }
      case CliStateLoggedIn(_) => OK("already logged-in",st)
      case _ => WARN("already logged-in",st)
    }
  }
}

class CommandAdd(cli:CliLoginable,args:String*) extends Command(cli,args) {
  def exec(st:CliState):Result = {
    st match {
      case CliStateLoggedOff(ctx) => ERR(s"not logged-in",st)
      case CliStateLoggedIn(ctx) => {
        val (name) = args.toList match {
          case name :: Nil =>                               (name)
          case _ => return ERR(s"missing <tag> <name> <telemetry> <measure> <units> [description] [when]",st)
        }

        //val r = Await.result(f, Duration("5 seconds"))
        var r = 0
        
        OK(s"${r}",st)
      }
      case _ => ERR("in unkown st",st)
    }
  }
}


class CommandFutureSleep(cli:CliLoginable,args:String*) extends Command(cli,args) {
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

class CommandSleep(cli:CliLoginable,args:String*) extends Command(cli,args) {
  def exec(st:CliState):Result = {
    val (time) = args.toList match {
      case msec :: Nil => (msec.toLong)
      case _ => (1000L)
    }

    Thread.sleep(time)
    OK(s"woke: ${time} msec",st)
  }
}

class CommandIpConfig(cli:CliLoginable,args:String*) extends Command(cli,args) {
  def exec(st:CliState):Result = {
    val (attr) = args.toList match {
      case attr :: Nil => (attr.toString)
      case _ => ("")
    }
    val blocking = ! attr.isEmpty

    if(blocking) {
      // blocking
      val ipconfig = http.ipconfig.HttpClient()
      OK(s"${ipconfig}",st)
    } else {
      // non-blocking
      val fipconfig = new http.ipconfig.HttpClient().getIp2()
      val f = fipconfig.map( r => {
        cli.uprintln(s"${r}")
        OK(s"${r}",st)
      })
      FUTURE(f,s"waiting result from ${f}...",st)
    }
  }
}


// class CommandHealthFind(cli:CliLoginable,args:String*) extends CommandHealthFinder(cli,args: _*) {
//   def exec(st:CliState):Result = {
//     st match {
//       case CliStateLoggedOff(ctx) => ERR(s"not logged-in",st)
//       case CliStateLoggedIn(ctx:Ctx) => {

//         val (date,name) = args.toList match {
//           case date :: name :: _ => (date,Some(name))
//           case date :: _ => (date,None)
//           case Nil => ("all",None)
//         }

//         val out = find(ctx,date,name)
//         OK(s"${out}",st)
//       }
//       case _ => ERR("in unkown st",st)
//     }
//   }
// }


class CliLoginable(serverUri:String) extends Cli(initState = CliStateLoggedOff(Ctx(serverUri)) ) {
  
  val tsFormat = "yyyy-MM-dd HH:mm:ss" //DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
  
  addSyntax(Seq (
    Syntax(words=Seq("connect","c"),cmd = (cli,args)=>new CommandConnect(this,args: _*),help="Connect"),
    Syntax(words=Seq("login","l"),cmd = (cli,args)=>new CommandLoginUser(this,args: _*),help="Login"),
    Syntax(words=Seq("add","a"),cmd = (cli,args)=>new CommandAdd(this,args: _*),help="Add"),
    Syntax(words=Seq("future","fut","f"),cmd = (cli,args)=>new CommandFutureSleep(this,args: _*),help="Create non-blocking Future (def: 1000 msec)"),
    Syntax(words=Seq("sleep"),cmd = (cli,args)=>new CommandSleep(this,args: _*),help="Blocking sleep (def: 1000 msec)"),
    
    Syntax(words=Seq("ipconfig"),cmd = (cli,args)=>new CommandIpConfig(this,args: _*),help="get public IP and metadata HttpClient")
  ))

  def uri:String = serverUri
}
