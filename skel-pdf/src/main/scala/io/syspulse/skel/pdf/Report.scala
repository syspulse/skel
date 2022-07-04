package io.syspulse.skel.pdf

import scala.jdk.CollectionConverters._

import com.typesafe.scalalogging.Logger

import io.syspulse.skel.util.Util
import io.syspulse.skel.pdf.issue._


object Report extends App {
  
  val templateDir = args.headOption.getOrElse("templates/T0")
  val issuesDir = "./skel-pdf/projects/Project-1/issues/"
  val outputFile = "output.pdf"
  
//   val issues = Range(1,5).map(i => Issue(s"ID-${i}",s"Issue ${i}",severity=i, desc=s"""
// ### Information

// __Hash__: ${Util.sha256(i.toString)}

// Detailed Issue description is here:

// Reference: [http://github.com](http://github.com)

// ```
// code {
//    val s = "String"
// }
// ```
// """)).toList

  val issues = Issue.parseDir(issuesDir).toList

  println(issues)

  new IssuesGenerator4(issues).generate(templateDir,outputFile);
}
