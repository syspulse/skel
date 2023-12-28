package io.syspulse.skel.dsl

import org.scalatest.{Ignore}
import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import java.time._
import io.jvm.uuid._
import io.syspulse.skel.util.Util
import scala.util.Success
import javax.script.ScriptEngineManager
import com.typesafe.scalalogging.Logger
import scala.tools.nsc.interpreter.shell.Scripted
import io.syspulse.skel.dsl.ScalaInterpreter

class DummyClass

class DslSpec extends AnyWordSpec with Matchers {
  
  val SCRIPT_1 = """new String("test")"""
  val SCRIPT_2 = """
  import scala._
  import scala.lang._
  new String("test")
  """

  "ScalaToolbox" should {

    s"run script: '${SCRIPT_1}'" in {
      val r = new ScalaToolbox().run(SCRIPT_1)
      info(s"r = ${r}")
      r.toString should === ("test")
    }

    s"reify" in {
      import scala.reflect.runtime.universe.reify
      import scala.tools.reflect.ToolBox
      val d1 = Seq(1,2,3)
      val r = scala.reflect.runtime.currentMirror.mkToolBox()
        .eval(reify{ 
          d1.filter(_ ==2).map(_ * 100)
        }.tree).asInstanceOf[List[Int]]

      info(s"r = ${r}")
      r should === (List(200))
    }

    s"compile and process Seq()" in {
      import scala.tools.reflect.ToolBox
      import scala.reflect.runtime.universe._
      import scala.reflect.runtime.currentMirror

      val d1 = Seq(1,2,3)
      val d1s = s"""Seq(${d1.mkString(",")})"""
      val script = s"""$d1s.filter(_ ==2).map(_ * 100)"""
      info(s"script=${script}")
      val e = scala.reflect.runtime.currentMirror.mkToolBox()
      
      val c = e.compile(e.parse(script))
      val r = c().asInstanceOf[List[Int]]
      info(s"r = ${r}")
      r should === (List(200))
    }

    s"compare re-compiled vs. compiled performance" in {
      import scala.tools.reflect.ToolBox
      import scala.reflect.runtime.universe._
      import scala.reflect.runtime.currentMirror

      val d1 = Seq(1,2,3)
      val d1s = s"""Seq(${d1.mkString(",")})"""
      val script = s"""$d1s.filter(_ ==2).map(_ * 100)"""
      info(s"script=${script}")
      
      val e = scala.reflect.runtime.currentMirror.mkToolBox()
      
      implicit val log = Logger(s"${this}")
      
      val c = e.compile(e.parse(script))
      Util.timed(1000) {         
        val r = c().asInstanceOf[List[Int]]
      }

      Util.timed(1) { 
        val c = e.compile(e.parse(script))
        val r = c().asInstanceOf[List[Int]]
      }      
    }

    s"parse ujson " in {
      import scala.tools.reflect.ToolBox
      import scala.reflect.runtime.universe._
      import scala.reflect.runtime.currentMirror

      import ujson._

      val script = s"""
      val s = "{ \\"id\\":\\"111111\\",\\"v\\":100 }"; 
      val o = ujson.read(s); 
      val id = ujson.read(s).obj("id").str
      val v = ujson.read(s).obj("v").num
      id.toString + "/" + v.toString
      """
      info(s"script=${script}")
      
      val e = scala.reflect.runtime.currentMirror.mkToolBox()
      
      implicit val log = Logger(s"${this}")
      
      val c = e.compile(e.parse(script))
      val r1 = c().asInstanceOf[String]
      val r2 = c().toString
      info(s"r1 = $r1")
      info(s"r2 = $r2")
    }
  }


  "ScalaInterpreter" ignore {

    s"run script: '${SCRIPT_1}'" in {
      val r = new ScalaInterpreter().run(SCRIPT_1)
      info(s"r=${r}")
      r.toString should === ("test")
    }
  }

  "Interpreter Shell" ignore {
    s"test" in {
      import javax.script._
      import scala.tools.nsc.Settings
            
      val settings: Settings = new Settings      
      settings.usejavacp.value = true
      settings.dependencyfile.value = "*.scala"
      settings.sourcepath.value = "src/main/scripts"
      val engine: Scripted = Scripted(new Scripted.Factory, settings)
      
      val var1 = "test"
      engine.getContext.setAttribute("var1",var1,ScriptContext.ENGINE_SCOPE)

      //val reader = new FileReader("src/main/scripts/script-1.scala")
      val d1 = Seq(1,2,3)
      val d1s = s"""Seq(${d1.mkString(",")})"""
      val script = s"""$d1s.filter(_ ==2).map(_ * 100)"""
      info(s"script=${script}")
      
      val compiledScript : CompiledScript = engine.compile(script)
      val r = compiledScript.eval()
    }

    s"run script" in {
      scala.tools.nsc.interpreter.shell.Scripted().eval("""System.out.println("Hello, World!")""")
    }
  }

  "ScriptEngineManager" ignore {

    // this works:
    // JAVA_HOME=/opt/jdk1.8.0_211 /opt/scala-2.13.3/bin/scala -cp /opt/scala-2.13.3/lib/scala-compiler.jar
    // scala.tools.nsc.interpreter.shell.Scripted().eval("""System.out.println("Hello, World!")""")    
    
    s"run script" in {
      import javax.script._
      import javax.script.ScriptEngine;
      import javax.script.ScriptEngineManager
      import scala.tools.nsc.interpreter.IMain
      
      val engine = new ScriptEngineManager().getEngineByName("scala");

      engine.asInstanceOf[IMain].settings.usejavacp.value = true        

      val testScript = "var a:Int =  10";
      engine.eval(testScript);      
    }
  }

//   This fails on test with:
//
//   java.lang.AssertionError: assertion failed: 
// [info]   No RuntimeVisibleAnnotations in classfile with ScalaSignature attribute: class Predef
  "ScalaScript" should {
    s"run Scala script" in {
      val e = new ScalaScript()
      e should !== (null)
    }
  }
}


