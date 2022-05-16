package io.syspulse.skel.cli

import scala.collection.immutable

import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.util.{Try,Success,Failure}
import akka.actor.typed.ActorSystem
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.util.Util

import scala.concurrent.ExecutionContext
import scala.concurrent.Await
import akka.actor.typed.scaladsl.Behaviors

abstract class CliState
case class CliStateInit() extends CliState

abstract class Result(msg:String,st:CliState) {
  def message = msg
  def state = st
}
case class OK(msg:String,st:CliState) extends Result(msg,st)
case class ERR(msg:String,st:CliState) extends Result(msg,st)
case class WARN(msg:String,st:CliState) extends Result(msg,st)

abstract class Command(cli:Cli,args:Seq[String] ) {
  def exec(state:CliState):Result
  def getArgs = if(args.isEmpty) "" else args.reduce(_ + "," + _)
}

class CommandExit(cli:Cli) extends Command(cli,Seq()) {
  def exec(state:CliState):Result = {
    Runtime.getRuntime().halt(0)
    OK("",state)
  }
}

//case class Syntax[-C <: Cli](words:Seq[String],cmd:(C,Seq[String])=>Command,help:String="")
case class Syntax(words:Seq[String],cmd:(Cli,Seq[String])=>Command,help:String="")

abstract class Cli(initState:CliState=CliStateInit(),
                   prompt:String = ">",
                   syntax:Seq[Syntax]=Seq(Syntax(Seq("exit","halt"),(cli,args)=>new CommandExit(cli),help="exit"))) {
  var syntaxMap: Map[String,Syntax] = moreSyntax(syntax)

  private def moreSyntax(syntax:Seq[Syntax]) = {
    if(syntax.size > 0) {
      val newCmd = syntax.map( stx => {
        stx.words.map( w =>  w -> stx)
      })
      newCmd.flatten.toMap
    } else
      Map[String,Syntax]()
  }

  def addSyntax(syntax:Seq[Syntax]) = {
    syntaxMap = syntaxMap ++ moreSyntax(syntax)
  }


  var state: CliState = initState
  
  def parse(commands:String*):List[Command] = {
    
    commands.mkString(";").split("[;\\n]").flatMap( s => {
      val cmdArgs = CliUtil.parseText(s).toList //s.split("\\s+").filter(!_.trim.isEmpty).toList
      val cmd:Option[Command] = cmdArgs match {
        case Nil => None
        case cmdName :: Nil => syntaxMap.get(cmdName).map( stx => stx.cmd(this,Seq()))
        case cmdName :: args => syntaxMap.get(cmdName).map( stx => stx.cmd(this,args.toSeq))
      }
      cmd
    }).toList
  }

  def run(commands:String*):CliState = {
    val cmds = parse(commands: _*)
    run(cmds)
  }

  def run(cmds:List[Command]):CliState = {
    for( cmd <- cmds ) {
      val r = cmd.exec(state)
      r match {
        case ERR(m,_) => Console.println(s"${Console.RED}Error${Console.RESET}: ${cmd.getClass.getSimpleName}(${cmd.getArgs}): ${m}")
        case WARN(m,_) => Console.println(s"${Console.YELLOW}Warning${Console.RESET}: ${cmd.getClass.getSimpleName}(${cmd.getArgs}): ${m}")
        case OK(m,_) => Console.println(s"${m}")
      }

      state = r.state
    }

    state
  }
  
}
