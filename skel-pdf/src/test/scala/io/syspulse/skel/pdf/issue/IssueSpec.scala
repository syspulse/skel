package io.syspulse.skel.util

import org.scalatest.{Ignore}
import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import java.time._
import io.jvm.uuid._
import io.syspulse.skel.util.Util
import io.syspulse.skel.pdf.issue.Issue

class IssueSpec extends AnyWordSpec with Matchers {
  val testDir = this.getClass.getClassLoader.getResource(".").getPath + "../../../"
  
  "Issue" should {

    "be created from MD text" in {
      val md1 = """
# Segmenation fault

ID: __PRJ1-35__

Severity: __5__

Status: __New__

Reference: http://github.com/project/code

## Description

Start of the bug description

File: [http://github.com/project/code/file.cpp](http://github.com/project/code/file.cpp)

```
code {
    val s = "String"
}
```

## Recommendation

Fix this line:

```
code {
    val s = "String"
}
```

"""
      val issueTry1 = Issue.parse(md1)
      //info(s"${issue1}")
      val issue1 = issueTry1.get
      issue1.id should === ("PRJ1-35")
      issue1.title should === ("Segmenation fault")
      issue1.severity should === (5)
      issue1.status should === ("New")
      issue1.desc should !== ("")
      issue1.recommend should !== ("")
    }    


    "be created from MD file" in {
      val issueTry1 = Issue.parseFile(s"${testDir}/projects/Project-1/issues/SegFault-1.md")
      //info(s"${issue1}")
      val issue1 = issueTry1.get
      issue1.id should === ("PRJ1-35")
      issue1.title should === ("Segmenation fault")
      issue1.severity should === (5)
      issue1.status should === ("New")
      issue1.desc should !== ("")
      issue1.recommend should !== ("")
    }

    "be 2 discovered in directory" in {
      val issues = Issue.parseDir(s"${testDir}/projects/Project-1/issues/")
      
      issues.size should === (2)
    }
  }
}
