package io.syspulse.skel.cli

import scala.collection.immutable

import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.Logger

import org.jline.reader.{
  MaskingCallback,
  ParsedLine,
  LineReader,
  LineReaderBuilder
}
import org.jline.reader.impl.LineReaderImpl
import org.jline.terminal.{Terminal, TerminalBuilder}

import io.jvm.uuid._

import io.syspulse.skel.util.Util

import scala.concurrent.ExecutionContext
import scala.concurrent.Await
import akka.actor.ActorSystem

abstract class CliState
case class CliStateInit() extends CliState

abstract class Result(message:String,state:CliState) {
  def msg = message
  def st = state

  def result = message
}

case class OK(message:String,state:CliState) extends Result(message,state)
case class ERR(message:String,state:CliState) extends Result(message,state)
case class WARN(message:String,state:CliState) extends Result(message,state)
case class FUTURE(f:Future[Result],message:String,state:CliState) extends Result(message,state)

object Command {
  val as:ActorSystem = ActorSystem("skel-cli") //ActorSystem(Behaviors.empty, "skel-cli")
  val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
}

abstract class Command(cli:Cli,args:Seq[String]) {
  implicit val as:ActorSystem = Command.as
  implicit val ec: scala.concurrent.ExecutionContext = Command.ec

  def exec(st:CliState):Result
  def getArgs = if(args.isEmpty) "" else args.reduce(_ + "," + _)
}

class BlockingCommand(cli:Cli,cmd:Command,timeout:Duration = Duration("10 seconds")) extends Command(cli,Seq()) {
  override def exec(st:CliState):Result = {
    //println(s"Blocking: ${this}: cmd=${cmd}")
    cmd.exec(st) match {
      case FUTURE(f,_,_) => Await.result(f,timeout)
      case r @ _ => r
    }
  }
}

class CommandHalt(cli:Cli) extends Command(cli,Seq()) {
  def exec(st:CliState):Result = {
    Runtime.getRuntime().halt(0)
    OK("",st)
  }
}

class CommandExit(cli:Cli) extends Command(cli,Seq()) {
  def exec(st:CliState):Result = {
    Runtime.getRuntime().exit(0)
    OK("",st)
  }
}

class CommandHelp(cli:Cli) extends Command(cli,Seq()) {
  def exec(st:CliState):Result = {
    val maxText = cli.getSyntax().map(s => s"${s.words.mkString(",")}".size).max
    val o = cli.getSyntax().map(s => {
      val prefix = s.words.mkString(",")
      val blanks = " " * ((maxText - prefix.size) + 1)
      s"${prefix}${blanks} - ${s.help}"
    }).mkString("\n")

    OK(o,st)
  }
}

class CommandUnknown(cli:Cli,cmd:String) extends Command(cli,Seq()) {
  def exec(st:CliState):Result = {
    WARN(s"Unknown Command: '${cmd}'",st)
  }
}

//case class Syntax[-C <: Cli](words:Seq[String],cmd:(C,Seq[String])=>Command,help:String="")
case class Syntax(words:Seq[String],cmd:(Cli,Seq[String])=>Command,help:String="")

object Cli {
  val DEF_SYNTAX = Seq(
    Syntax(Seq("exit"),(cli,args)=>new CommandExit(cli),help="Exit"),
    Syntax(Seq("halt"),(cli,args)=>new CommandHalt(cli),help="Halt"),
    Syntax(Seq("help","hlp","h"),(cli,args)=>new CommandHelp(cli),help="Show help")
  )
}


abstract class Cli(initState:CliState=CliStateInit(),
                   prompt:String = ">",
                   ignoreUnknown:Boolean = false,
                   syntax:Seq[Syntax]=Cli.DEF_SYNTAX) {
  
  var colors:Colorful = CliColors.getColorful("night")
  var reader: LineReader = _
  var cursorShape = ">"
  var changedShape = s"${colors.YELLOW}*${colors.RESET}"
  
  def ERR(msg: String): String = s"${colors.ERR}ERR: ${colors.RESET}${msg}"
  def PROMPT = cursorShape
  
  // ATTENTION: Unsafe operation on non-public method
  // jline3 fix needed
  def updatePrompt() = {
    try {
      reader.asInstanceOf[LineReaderImpl].setPrompt(PROMPT);
      
      reader.callWidget(LineReader.CLEAR);
      //reader.getTerminal().writer().println(PROMPT);
      reader.callWidget(LineReader.REDRAW_LINE);
      reader.callWidget(LineReader.REDISPLAY);
      reader.getTerminal().writer().flush();
    } catch {
      case e:Exception => // ignore
    }
  }
  val CONSOLE = Console.out
  def uprintln(args:String) = {
    CONSOLE.println(args)
    //CONSOLE.flush()
    updatePrompt()
  }

  cursorShape = prompt
  
  var syntaxMap: Map[String,Syntax] = moreSyntax(syntax)

  // same command with different syntax
  def getSyntax() = syntaxMap.values.toList.distinct
  
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


  var st: CliState = initState
  
  def parse(commands:String*):List[Command] = {
    commands.mkString(";").split("[;\\n]").flatMap( s => {
      val cmdArgs = CliUtil.parseText(s).toList
      
      val cmd:Option[Command] = cmdArgs match {
        case Nil => None
        case cmdName :: Nil => {
          val b = cmdName.startsWith("!")
          
          (if(b) syntaxMap.get(cmdName.tail) else syntaxMap.get(cmdName))
            .map( stx => stx.cmd(this,Seq()))
            .map( stx => if(b) new BlockingCommand(this,stx) else stx )
        }
        case cmdName :: args => {
          val b = cmdName.startsWith("!")
          (if(b) syntaxMap.get(cmdName.tail) else syntaxMap.get(cmdName))
            .map( stx => stx.cmd(this,args.toSeq))
            .map( stx => if(b) new BlockingCommand(this,stx) else stx )
        }
      }
      if(ignoreUnknown || cmd.isDefined)
        cmd
      else
        Some(new CommandUnknown(this,cmdArgs.mkString(" ")))
    }).toList
  }

  def run(commands:String*):CliState = {
    val cmds = parse(commands: _*)
    run(cmds)
  }

  def run(cmds:List[Command]):CliState = {
    for( cmd <- cmds ) {
      val r = cmd.exec(st)
      r match {
        case ERR(m,_) => CONSOLE.println(s"${colors.RED}Error${colors.RESET}: ${cmd.getClass.getSimpleName}(${cmd.getArgs}): ${m}")
        case WARN(m,_) => CONSOLE.println(s"${colors.YELLOW}Warning${colors.RESET}: ${cmd.getClass.getSimpleName}(${cmd.getArgs}): ${m}")
        case OK(m,_) => CONSOLE.println(s"${m}")
        case FUTURE(_,m,_) => CONSOLE.println(s"${m}")
      }

      st = r.st
    }
    st
  }
  
  
  def runWithParser(parser: org.jline.reader.Parser,script: Seq[String], echo: Boolean ) = {
    var exit = false
    
    for (cmd <- script if !exit) {
      if (echo) CONSOLE.println(cmd)

      val pl: ParsedLine = parser.parse(cmd, 0);

      pl.line match {
        case "" => ; 

        case op: String => {
          val cmds = parse(op)
          run(cmds)
        }
      }
    }
    
  }

  def shell() = {
    
    val terminal = TerminalBuilder
      .builder()
      .system(true)
      .signalHandler(Terminal.SignalHandler.SIG_IGN)
      .build();

    reader = LineReaderBuilder
      .builder()
      .terminal(terminal)
      //.completer(new MyCompleter())
      //.highlighter(new MyHighlighter())
      //.parser(new MyParser())
      .build();

    implicit val out = terminal.writer() //new PrintWriter(reader.getOutput())

    var exit = false
    var line: String = ""

    try {
      while (!exit && {
              line = reader.readLine(
                PROMPT,
                "",
                null.asInstanceOf[MaskingCallback],
                null
              ); line
            } != null) {
      
        try {
          runWithParser(reader.getParser(), Seq(line), false)
        } catch {
          case e: org.jline.reader.EndOfFileException => exit = true          
          case e: Exception                           => out.println(e)
        }
        
      }      
      
    } catch {
      case e @ (_:org.jline.reader.UserInterruptException | _:org.jline.reader.EndOfFileException) => {
        System.exit(2)
      }
    }
  } 
  
}
