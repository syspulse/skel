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

    s"re-compiled vs. compiled performance" in {
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

      Util.timed(10) { 
        val c = e.compile(e.parse(script))
        val r = c().asInstanceOf[List[Int]]
      }      
    }
  }

  "ScalaInterpreter" should {

    // s"run script: '${SCRIPT_1}'" in {
    //   val r = new ScalaInterpreter().run(SCRIPT_1)
    //   info(s"r=${r}")
    //   r.toString should === ("test")
    // }

    // s"run script'" in {
    //   val r = new ScalaInterpreter().run("""println("test")""")
    //   info(s"r=${r}")
    //   // r.toString should === ("test")
    // }

  }

  "SCALA" should {

    // s"run script: '${SCRIPT_1}'" in {
    //   val r = new SCALA().run(SCRIPT_2)
    //   info(s"r=${r}")
    //   r.toString should === ("test")
    // }

    // s"test" in {

    //   val engine = new ScriptEngineManager().getEngineByName("scala")
    //   val settings = engine.asInstanceOf[scala.tools.nsc.interpreter.IMain].settings
    //   settings.embeddedDefaults[DummyClass]
    //   engine.eval("val x: Int = 5")
    //   val thing = engine.eval("x + 9").asInstanceOf[Int]
    // }

    // s"run with input args" in {
    //   val data = Seq(1,2,3)
    //   val r = new SCALA().run("""
    //     data.filter(v => v == f).map(v => v + 10)
    //   """,
    //   Map(
    //     "data" -> data,
    //     "f" -> 3
    //   ))
    //   info(s"r=${r}")
    //   //r.toString should === ("test")
    // }
  }

}


