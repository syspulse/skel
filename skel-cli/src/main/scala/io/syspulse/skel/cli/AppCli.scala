package io.syspulse.skel.cli

import scala.collection.immutable

import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.util.{Try,Success,Failure}
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger
import scala.concurrent.ExecutionContext
import scala.concurrent.Await

import io.jvm.uuid._

import io.syspulse.skel.util.Util
import io.syspulse.skel.cli._
import io.syspulse.skel.crypto.Eth



import os._
import java.time.format.DateTimeFormatter
import io.syspulse.skel.crypto.wallet.Signer

object Ctx {
  implicit val system = ActorSystem(Behaviors.empty, "app-cli")
  implicit val ec = system.executionContext
}

case class Ctx(uri:String,user:Option[User]=None,signer:Option[Signer]=None) {
  import Ctx._
  //val medarClient = new AppClientHttp(uri,signer)
}


case class CliStateLoggedOff(ctx:Ctx) extends CliState
case class CliStateLoggedIn(ctx:Ctx) extends CliState

class CommandConnect(cli:AppCli,args:String*) extends Command(cli,args) {
  def exec(state:CliState):Result = {
    state match {
      case CliStateLoggedOff(ctx)  => {
        val (uri) = args.toList match {
          case uri :: _ => (uri)
          case Nil => return ERR("missing uri",state) 
        }
        OK(s"connected: ${uri}",CliStateLoggedOff(ctx.copy(uri = uri)))
      }
      case CliStateLoggedIn(_) => OK("already logged in",state)
      case _ => WARN("already logged in",state)
    }
  }
}


class CommandLoginUser(cli:AppCli,args:String*) extends Command(cli,args) {
  val DEF_KEYSTORE = (os.home / ".config" / "skel" / "keystore.json").toString
  def exec(state:CliState):Result = {
    state match {
      case CliStateLoggedOff(ctx) => {
        val (userId,kk) = args.toList match {
          case userId :: pass :: keystoreFile :: _ => (userId,Eth.readKeystore(pass,keystoreFile))
          case userId :: pass :: Nil => (userId,Eth.readKeystore(pass,DEF_KEYSTORE))
          case userId :: Nil => (userId,Eth.readKeystore("",DEF_KEYSTORE))
          case Nil => ("00000000-0000-0000-1000-000000000001",Eth.readKeystore("",DEF_KEYSTORE))
          //case Nil => return ERR("missing uri,userId,password",state) 
        }
        
        if(kk.isFailure) {
          return ERR(s"could not load keystore: ${kk}",CliStateLoggedOff(ctx))
        }
        
        val signer = Signer(UUID(userId),kk.get._1,kk.get._2)
        val user = User(UUID(userId),"user-1","user-1@email",List(signer))
        
        OK(s"logged in: ${user}",CliStateLoggedIn(Ctx(ctx.uri,Some(user),Some(signer))))
      }
      case CliStateLoggedIn(_) => OK("already logged in",state)
      case _ => WARN("already logged in",state)
    }
  }
}

class CommandAdd(cli:AppCli,args:String*) extends Command(cli,args) {
  def exec(state:CliState):Result = {
    println(s"args=${args}")
    state match {
      case CliStateLoggedOff(ctx) => ERR(s"not logged-in",state)
      case CliStateLoggedIn(ctx) => {
        val (name) = args.toList match {
          case name :: Nil =>                               (name)
          case _ => return ERR(s"missing <tag> <name> <telemetry> <measure> <units> [description] [when]",state)
        }

        //val r = Await.result(f, Duration("5 seconds"))
        var r = 0
        
        OK(s"${r}",state)
      }
      case _ => ERR("in unkown state",state)
    }
  }
}


// class CommandHealthFind(cli:AppCli,args:String*) extends CommandHealthFinder(cli,args: _*) {
//   def exec(state:CliState):Result = {
//     state match {
//       case CliStateLoggedOff(ctx) => ERR(s"not logged-in",state)
//       case CliStateLoggedIn(ctx:Ctx) => {

//         val (date,name) = args.toList match {
//           case date :: name :: _ => (date,Some(name))
//           case date :: _ => (date,None)
//           case Nil => ("all",None)
//         }

//         val out = find(ctx,date,name)
//         OK(s"${out}",state)
//       }
//       case _ => ERR("in unkown state",state)
//     }
//   }
// }


class AppCli(serverUri:String) extends Cli(initState = CliStateLoggedOff(Ctx(serverUri)) ) {
  implicit val system = ActorSystem(Behaviors.empty, "app-cli")
  implicit val ec = system.executionContext

  val tsFormat = "yyyy-MM-dd HH:mm:ss" //DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
  
  addSyntax(Seq (
    Syntax(words=Seq("connect","c"),cmd = (cli,args)=>new CommandConnect(this,args: _*),help="Connect"),
    Syntax(words=Seq("login","l"),cmd = (cli,args)=>new CommandLoginUser(this,args: _*),help="Login"),
    Syntax(words=Seq("add","a"),cmd = (cli,args)=>new CommandAdd(this,args: _*),help="Add")
  ))

  def uri:String = serverUri
}
