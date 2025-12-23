package io.syspulse.skel.crypto.eth

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import scala.util.{Success, Failure}

class SolidityResultSpec extends AnyWordSpec with Matchers {
  val sr = SolidityResult()
  
  "SolidityResult.parse" should {
    
    "parse int" in {
      val input = "10"
      val parsed = sr.parse(input).get
      parsed.toString shouldBe "10"
    }

    "parse string" in {
      val input = """ "hello " """
      val parsed = sr.parse(input).get
      parsed.toString shouldBe """"hello """"
    }

    "parse addr" in {
      val input = " 0x1234567890abcdef "
      val parsed = sr.parse(input).get
      parsed.toString shouldBe "0x1234567890abcdef"
    }

    "parse (int)" in {
      val input = "( 20)"
      val parsed = sr.parse(input).get
      parsed.toString shouldBe "(20)"
    }

    "parse comma-delimited values" in {
      val input = "1,2,3"
      val parsed = sr.parse(input).get
      parsed.toString shouldBe "1,2,3"
    }
    
    "parse array values int" in {
      val input = "[ 1,2 ,3]"
      val parsed = sr.parse(input).get
      parsed.toString shouldBe "[1,2,3]"
    }

    "parse array values string" in {
      val input = "[ \"hello\", \"world\", \"test\" ]"
      val parsed = sr.parse(input).get
      parsed.toString shouldBe """["hello","world","test"]"""
    }
    
    "parse tuple values" in {
      val input = "(1, 2,3 )"
      val parsed = sr.parse(input).get
      parsed.toString shouldBe "(1,2,3)"

      parsed.index(0) shouldBe Value("1")
      parsed.index(0).toString shouldBe "1"
    }        
    
    "parse nested structures" in {
      val input = "[(1,2),(3,4)]"
      val parsed = sr.parse(input).get
      parsed.toString shouldBe "[(1,2),(3,4)]"
    }
    
    "parse complex nested structures" in {
      val input = """("hello",(1,2,"world"))"""
      val parsed = sr.parse(input).get
      parsed.toString shouldBe """("hello",(1,2,"world"))"""
    }

    "parse complex result" in {
      val input = """ "hello",(1,2,"world")"""
      val parsed = sr.parse(input).get
      parsed.toString shouldBe """"hello",(1,2,"world")"""
    }
    
    
  }

  "SolidityResult.parsePath" should {
    
    "parse path 100" in {
      val parsed = sr.parsePath("100").get
      info(s"parsed: ${parsed}")
      //parsed shouldBe List(IndexPath(0))
    }

    "parse path (0)" in {
      val parsed = sr.parsePath("(0)").get
      info(s"parsed: ${parsed}")
      //parsed shouldBe List(IndexPath(0))
    }

    "parse path (0)[2]" in {
      val parsed = sr.parsePath("(0).[2]").get
      info(s"parsed: ${parsed}")
      //parsed shouldBe List(IndexPath(0))
    }    
  }

  "SolidityResult.extract" should {
    
    "extract default value" in {
      val e1 = sr.extract("10", "").get
      info(s"e1: ${e1} ${e1.getClass}")
      e1.toString shouldBe "10"

      val e2 = sr.extract("(10)", "").get
      info(s"e2: ${e2} ${e2.getClass}")
      e2.toString shouldBe "(10)"

      val e3 = sr.extract("0x123", "").get
      info(s"e3: ${e3} ${e3.getClass}")
      e3.toString shouldBe "0x123"

      val e4 = sr.extract("\"text\"", "").get
      info(s"e4: ${e4} ${e4.getClass}")
      e4.toString shouldBe "\"text\""
      
    }

    "extract int 10" in {
      val e1 = sr.extract("10", "(0)").get
      info(s"e1: ${e1} ${e1.getClass}")
      e1.toString shouldBe "10"

      val e2 = sr.extract("(10)", "(0)").get
      info(s"e2: ${e2} ${e2.getClass}")
      e2.toString shouldBe "10"
    }

    "extract int (100,200,300)" in {
      val e1 = sr.extract("(100,200,300)", "(0)").get
      info(s"e1: ${e1} ${e1.getClass}")
      e1.toString shouldBe "100"

      val e2 = sr.extract("(100,200,300)", "(1)").get
      info(s"e2: ${e2} ${e2.getClass}")
      e2.toString shouldBe "200"

      val e3 = sr.extract("(100,200,300)", "(2)").get
      info(s"e3: ${e3} ${e3.getClass}")
      e3.toString shouldBe "300"

      val e4 = sr.extract("(100,200,300)", "(3)")
      info(s"e4: ${e4} ${e4.getClass}")
      e4.isFailure shouldBe true
    }

    "extract array [100,200,300]" in {
      val e1 = sr.extract("[100,200,300]", "[0]").get
      info(s"e1: ${e1} ${e1.getClass}")
      e1.toString shouldBe "100"

      val e2 = sr.extract("[100,200,300]", "[1]").get
      info(s"e2: ${e2} ${e2.getClass}")
      e2.toString shouldBe "200"

      val e3 = sr.extract("[100,200,300]", "[2]").get
      info(s"e3: ${e3} ${e3.getClass}")
      e3.toString shouldBe "300"

      val e4 = sr.extract("[100,200,300]", "[3]")
      info(s"e4: ${e4} ${e4.getClass}")
      e4.isFailure shouldBe true
    }

    "extract truple (10,text,0x123,[100,200,300])" in {
      val e1 = sr.extract("(10,\"text\",0x123,[100,200,300])", "(0)").get
      info(s"e1: ${e1} ${e1.getClass}")
      e1.toString shouldBe "10"

      val e2 = sr.extract("(10,\"text\",0x123,[100,200,300])", "(1)").get
      info(s"e2: ${e2} ${e2.getClass}")
      e2.toString shouldBe """"text""""

      val e3 = sr.extract("(10,\"text\",0x123,[100,200,300])", "(2)").get
      info(s"e3: ${e3} ${e3.getClass}")
      e3.toString shouldBe "0x123"

      val e4 = sr.extract("(10,\"text\",0x123,[100,200,300])", "(3).[1]").get
      info(s"e4: ${e4} ${e4.getClass}")
      e4.toString shouldBe "200"

      val e5 = sr.extract("(10,\"text\",0x123,[100,200,300])", "(3).[2]").get
      info(s"e5: ${e5} ${e5.getClass}")
      e5.toString shouldBe "300"
      
    }

    "extract result two value 10,(0x123,[100,200,300])" in {
      val e1 = sr.extract("(10,(0x123,[100,200,300]))", "(0)").get
      info(s"e1: ${e1} ${e1.getClass}")
      e1.toString shouldBe "10"

      val e2 = sr.extract("(10,(0x123,[100,200,300]))", "(1).[0]").get
      info(s"e2: ${e2} ${e2.getClass}")
      e2.toString shouldBe "0x123"

      val e3 = sr.extract("(10,(0x123,[100,200,300]))", "(1).(1)").get
      info(s"e3: ${e3} ${e3.getClass}")
      e3.toString shouldBe "[100,200,300]"

      val e4 = sr.extract("(10,(0x123,[100,200,300]))", "(1).(1).[0]").get
      info(s"e4: ${e4} ${e4.getClass}")
      e4.toString shouldBe "100"      
      
    }

    "extract tuple array [(1,text-1,0x123),(2,text-2,0x456),(3,text-3,0x789)]" in {
      val e1 = sr.extract("[(1,\"text-1\",0x123),(2,\"text-2\",0x456),(3,\"text-3\",0x789)]", "[0]").get
      info(s"e1: ${e1} ${e1.getClass}")
      e1.toString shouldBe "(1,\"text-1\",0x123)"

      val e2 = sr.extract("[(1,\"text-1\",0x123),(2,\"text-2\",0x456),(3,\"text-3\",0x789)]", "[1].(0)").get
      info(s"e2: ${e2} ${e2.getClass}")
      e2.toString shouldBe "2"

      val e3 = sr.extract("[(1,\"text-1\",0x123),(2,\"text-2\",0x456),(3,\"text-3\",0x789)]", "[2].(2)").get
      info(s"e3: ${e3} ${e3.getClass}")
      e3.toString shouldBe "0x789"
      
      
    }

    "parse solidity return multiples" in {
      val input = "[(1,\"text-1\",0x123),(2,\"text-2\",0x456)],(400,0x001)"
      val parsed = sr.parse(input).get
      parsed.toString shouldBe "[(1,\"text-1\",0x123),(2,\"text-2\",0x456)],(400,0x001)"
    }

    // "extract stupid Solidity return second param [(1,text-1,0x123),(2,text-2,0x456)],(40000,0x000000000000001)" in {
    //   val e1 = sr.extract("[(1,\"text-1\",0x123),(2,\"text-2\",0x456)],(40000,0x000000000000001)", "(1)").get
    //   info(s"e1: ${e1} ${e1.getClass}")
    //   e1.toString shouldBe "40000"
      
      
    // }
  }
} 