package io.syspulse.skel.pdf.issue

import scala.jdk.CollectionConverters._

import laika.io._
import laika.io.implicits._
import laika.parse.code._
import laika.api._
import laika.format._
import laika.markdown.github._

import com.typesafe.scalalogging.Logger

import os._
import io.syspulse.skel.util.Util
import scala.util.Try
import scala.util.Success
import scala.util.Failure

case class Issue(id:String,title:String,desc:String,severity:Int,status:String,ref:String,recommend:String)

object Issue {
  val transformer = Transformer.from(Markdown).to(HTML).using(GitHubFlavor,SyntaxHighlighting).build
  def apply(id:String,title:String = "", desc:String = "", severity:Int = 4,status:String="New",ref:String="",recommend:String="") = {
    println(s"=====> '${desc}'")
    val descHtml = 
      transformer.transform(desc) match {
        case Left(e) => desc
        case Right(html) => html
      }
    new Issue(id,title,descHtml,severity,status,ref,recommend)
  }

  // very memory heavy unoptimized parser
  def parse(mdText:String):Try[Issue] = {
    def unbold(s:String) = s.trim.stripPrefix("__").stripSuffix("__")
    var phase = ""
    try {
      val issue = mdText.split("\n").foldLeft(Issue(""))( (issue,line) => {
        //println(s"line='${line}'")

        val s = line.trim()
        val issue1:Issue = 
          s match {
            case title if s.matches("^#\\s+.*") => 
              phase = "title"
              issue.copy(title = title.split("#").tail.mkString.trim)
              
            case desc if s.matches("(?i)^##\\s+Description.*") => 
              phase = "desc"
              issue

            case recommend if s.matches("(?i)^##\\s+Recommendation.*") => 
              phase = "rec"
              issue

            case id if phase=="title" && s.matches("(?i)^ID:.*") => 
              issue.copy(id = unbold(s.split(":").tail.mkString))

            case id if phase=="title" && s.matches("(?i)^Severity:.*") => 
              issue.copy(severity = unbold(s.split(":").tail.mkString).toInt)

            case id if phase=="title" && s.matches("(?i)^Status:.*") => 
              issue.copy(status = unbold(s.split(":").tail.mkString))

            case id if phase=="title" && s.matches("(?i)^Reference:.*") => 
              issue.copy(ref = s.split(":").tail.mkString)

            case _ => {
              phase match {
                case "desc" => issue.copy(desc = issue.desc + line + "\n")
                case "rec" => issue.copy(recommend = issue.recommend + line + "\n")
                case _ => issue
              }            
            }
          }
              
        
        issue1
      })

      Success(
        Issue(issue.id, issue.title, issue.desc, issue.severity, issue.status, issue.ref,issue.recommend)
      )
    } catch {
      case e:Exception => Failure(e)
    }
  }

  def parseFile(mdFile:String):Try[Issue] = {
    val mdText = os.read(os.Path(mdFile,os.pwd))
    parse(mdText)
  }

  def parseDir(mdDir:String):Seq[Issue] = {
    os.list(os.Path(mdDir,os.pwd)).flatMap( mdFile =>{
      val mdText = os.read(os.Path(mdFile,os.pwd))
      parse(mdText).toOption
    })
  }
}
