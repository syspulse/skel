package io.syspulse.skel.pdf

import com.typesafe.scalalogging.Logger

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._

import io.jvm.uuid._

import io.syspulse.skel.pdf.issue._
import scala.util.Try
import scala.util.Success

class ReportGenerator(templateDir:String,issuesDir:String,outputFile:String = "output.pdf") {
  val log = Logger(s"${this}")

  def generate():Try[String] = {
    log.info(s"template=${templateDir}: input=${issuesDir}: output=${outputFile}")

    val issues = Issue.parseDir(issuesDir).toList
    log.info(s"issues: ${issues.size}")

    new IssuesGenerator4(issues).generate(templateDir,outputFile);    
          
    Success(outputFile)
  }
}

