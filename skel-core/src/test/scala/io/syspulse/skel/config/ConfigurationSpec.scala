package io.syspulse.skel.config

import org.scalatest.{Ignore}
import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import java.time._
import io.jvm.uuid._
import io.syspulse.skel.util.Util

case class DataUnit(v:Double,unit:String)
case class Data(ts:Long,v:DataUnit)
case class DataList(name:String,data:List[Data])

class ConfigurationSpec extends AnyWordSpec with Matchers {
  val testDir = this.getClass.getClassLoader.getResource(".").getPath

  "Configuration" should {

    "return multiple args parameters" in {
      val args = Array("1","2","3")
      val c = Configuration.withPriority(Seq(
        new ConfigurationArgs(args,"test-1","",
          ArgString('s', "param.str",""),
          ArgInt('i', "param.int",""),
          ArgParam("<params...>")
        )
      ))

      c.getAll().size should === (0)

      c.getParams().size should === (3)
      c.getParams() should === (Seq("1","2","3"))
    }

    "get List from 'str1,  2, 1000 ' " in {
      val args = Array("-s","str1,  2, 1000 ")
      val c = Configuration.withPriority(Seq(
        new ConfigurationArgs(args,"test-1","",
          ArgString('s', "param.list","")          
        )
      ))
      
      val s = c.getListString("param.list") 
      s.size should === (3)
      s should === (Seq("str1","2","1000"))
    }

    "get List from default " in {
      val args = Array[String]()
      val c = Configuration.withPriority(Seq(
        new ConfigurationArgs(args,"test-1","",
          ArgString('s', "param.list","")
        )
      ))
      
      val s = c.getListString("param.list",Seq("1","2")) 
      s.size should === (2)
      s should === (Seq("1","2"))
    }

    "get List from default empty Seq()" in {
      val args = Array[String]()
      val c = Configuration.withPriority(Seq(
        new ConfigurationArgs(args,"test-1","",
          ArgString('s', "param.list","")
        )
      ))
      
      val s = c.getListString("param.list",Seq()) 
      s.size should === (0)
      s should === (Seq())
    }

    "get List from file: 'str1,  2, 1000 ' " in {
      val args = Array("-s",s" file://${testDir}/resource-2.conf")
      val c = Configuration.withPriority(Seq(
        new ConfigurationArgs(args,"test-1","",
          ArgString('s', "param.list","")          
        )
      ))
      
      val s = c.getListString("param.list") 
      info(s"s = ${s}")
      s.size should === (3)
      s should === (Seq("str1","2","1000"))
    }

    "withEnv inject $HOME into var-${HOME}-_-${USER}" in {
      val var1 = "var-${HOME}-_-${USER}"
      val var2 = Configuration.withEnv(var1)
      
      var2 should !== (var1)
      var2 should === (s"var-${sys.env("HOME")}-_-${sys.env("USER")}")
    }

    "return String arg with Environment Var" in {
      val args = Array("-s","value-${USER}")
      val c = Configuration.withPriority(Seq(
        new ConfigurationArgs(args,"test-1","",
          ArgString('s', "param.env",""),
          ArgParam("<params...>")
        )
      ))
      
      c.getString("param.env") should === (Some(s"value-${sys.env("USER")}"))
    }
    
    "get String from file" in {
      val args = Array("-s",s"file://${testDir}/VAR_FILE.conf")
      val c = Configuration.withPriority(Seq(
        new ConfigurationArgs(args,"test-10","",
          ArgString('s', "param.str", "file ref" )
        )
      ))
      
      val s = c.getSmartString("param.str") 
      s should === (Some("value-10"))
    }
  }
}
