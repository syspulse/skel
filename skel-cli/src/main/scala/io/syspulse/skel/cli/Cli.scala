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


// ----------------------------------------- Colors ---
trait Colorful {
  val RESET:String = "\u001B[0m"

  // Bold \u001b[1m
  val BOLD:String = "\u001B[1m"
  // Underline
  val UNDERLINED:String = "\u001B[4;30m"
  // Background
  val BACKGROUND:String = "\u001B[40m"
  // High Intensity
  val BRIGHT:String = "\u001B[0;90m"
  // Bold High Intensity
  val BOLD_BRIGHT:String = "\u001B[1m;90m"
  // High Intensity backgrounds
  val BACKGROUND_BRIGHT:String = "\u001B[0;100m"

  // Regular Colors
  val BLACK:String = "\u001B[0;30m"
  val RED:String = "\u001B[0;31m"
  val GREEN:String = "\u001B[0;32m"
  val YELLOW:String = "\u001B[0;33m"
  val BLUE:String = "\u001B[0;34m" 
  val PURPLE:String = "\u001B[0;35m"
  val CYAN:String = "\u001B[0;36m"
  val WHITE:String = "\u001B[0;37m"
  
  val ERR:String
  val KEY:String
  val VALUE:String
  val SEARCH:String
}


case class ColorfulDay() extends Colorful {
  val ERR:String = RED
  val KEY:String = BLUE
  val VALUE:String = BLACK + BOLD
  val SEARCH:String = BLUE
}

case class ColorfulNight () extends Colorful {
  val ERR:String = RED
  val KEY:String = YELLOW
  val VALUE:String = WHITE + BOLD
  val SEARCH:String = BLUE
}

object Colors {
  val colors = Map(
    "day" ->  ColorfulDay(),
    "night" -> ColorfulNight() 
  )

  def getColorful(name:String):Colorful = colors.get(name).getOrElse(ColorfulNight())
}



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

class CommandHalt(cli:Cli) extends Command(cli,Seq()) {
  def exec(state:CliState):Result = {
    Runtime.getRuntime().halt(0)
    OK("",state)
  }
}

class CommandExit(cli:Cli) extends Command(cli,Seq()) {
  def exec(state:CliState):Result = {
    Runtime.getRuntime().exit(0)
    OK("",state)
  }
}

class CommandHelp(cli:Cli) extends Command(cli,Seq()) {
  def exec(state:CliState):Result = {
    val maxText = cli.getSyntax().map(s => s"${s.words.mkString(",")}".size).max
    val o = cli.getSyntax().map(s => {
      val prefix = s.words.mkString(",")
      val blanks = " " * ((maxText - prefix.size) + 1)
      s"${prefix}${blanks} - ${s.help}"
    }).mkString("\n")
    
    OK(o,state)
  }
}

class CommandUnknown(cli:Cli,cmd:String) extends Command(cli,Seq()) {
  def exec(state:CliState):Result = {
    WARN(s"Unknown Command: '${cmd}'",state)
  }
}

//case class Syntax[-C <: Cli](words:Seq[String],cmd:(C,Seq[String])=>Command,help:String="")
case class Syntax(words:Seq[String],cmd:(Cli,Seq[String])=>Command,help:String="")

object Cli {
  val DEF_SYNTAX = Seq(
    Syntax(Seq("exit"),(cli,args)=>new CommandExit(cli),help="Exit"),
    Syntax(Seq("halt"),(cli,args)=>new CommandHalt(cli),help="Halt"),
    Syntax(Seq("help","h"),(cli,args)=>new CommandHelp(cli),help="show help")
  )
}


abstract class Cli(initState:CliState=CliStateInit(),
                   prompt:String = ">",
                   ignoreUnknown:Boolean = false,
                   syntax:Seq[Syntax]=Cli.DEF_SYNTAX) {
  
  var colors:Colorful = Colors.getColorful("night")
  val CONSOLE = Console.out
  var reader: LineReader = _
  var cursorShape = ">"
  var changedShape = s"${colors.YELLOW}*${colors.RESET}"
  
  def ERR(msg: String): String = s"${colors.ERR}ERR: ${colors.RESET}${msg}"
  def PROMPT = cursorShape
    
  cursorShape = prompt
  
  var syntaxMap: Map[String,Syntax] = moreSyntax(syntax)
  def getSyntax() = syntaxMap.values
  
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
      val r = cmd.exec(state)
      r match {
        case ERR(m,_) => CONSOLE.println(s"${colors.RED}Error${colors.RESET}: ${cmd.getClass.getSimpleName}(${cmd.getArgs}): ${m}")
        case WARN(m,_) => CONSOLE.println(s"${colors.YELLOW}Warning${colors.RESET}: ${cmd.getClass.getSimpleName}(${cmd.getArgs}): ${m}")
        case OK(m,_) => CONSOLE.println(s"${m}")
      }

      state = r.state
    }

    state
  }
  
  
  def runWithParser(parser: org.jline.reader.Parser,script: Seq[String], echo: Boolean ) = {
    var exit = false
    
    for (cmd <- script if !exit) {
      if (echo) CONSOLE.println(cmd)

      val pl: ParsedLine = parser.parse(cmd, 0);

      pl.word() match {
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
          runWithParser(reader.getParser(), Seq(line), true)
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
