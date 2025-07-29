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
    
    // "extract _1 from comma-delimited" in {
    //   val result = sr.extractValue("1,2,3", "_1").get
    //   result shouldBe "1"
    // }
    
    // "extract _2 from comma-delimited" in {
    //   val result = sr.extractValue("1,2,3", "_2").get
    //   result shouldBe "2"
    // }
    
    // "extract _3 from comma-delimited" in {
    //   val result = sr.extractValue("1,2,3", "_3").get
    //   result shouldBe "3"
    // }
    
    // "extract _1 from comma-delimited strings" in {
    //   val result = sr.extractValue("a,b,c", "_1").get
    //   result shouldBe "a"
    // }
    
    // "extract _2 from comma-delimited strings" in {
    //   val result = sr.extractValue("a,b,c", "_2").get
    //   result shouldBe "b"
    // }
    
    // "extract _3 from comma-delimited strings" in {
    //   val result = sr.extractValue("a,b,c", "_3").get
    //   result shouldBe "c"
    // }
    
    // "extract _1 from quoted comma-delimited" in {
    //   val result = sr.extractValue(""""hello","world","test"""", "_1").get
    //   result shouldBe """"hello""""
    // }
    
    // "extract _2 from quoted comma-delimited" in {
    //   val result = sr.extractValue("""hello","world","test"""", "_2").get
    //   result shouldBe """"world""""
    // }
    
    // "extract _3 from quoted comma-delimited" in {
    //   val result = sr.extractValue("""hello","world","test"""", "_3").get
    //   result shouldBe """"test""""
    // }
    
    // "extract [0] from array" in {
    //   val result = sr.extractValue("[1,2,3]", "[0]").get
    //   result shouldBe "1"
    // }
    
    // "extract [1] from array" in {
    //   val result = sr.extractValue("[1,2,3]", "[1]").get
    //   result shouldBe "2"
    // }
    
    // "extract [2] from array" in {
    //   val result = sr.extractValue("[1,2,3]", "[2]").get
    //   result shouldBe "3"
    // }
    
    // "extract [0] from string array" in {
    //   val result = sr.extractValue("[a,b,c]", "[0]").get
    //   result shouldBe "a"
    // }
    
    // "extract [1] from string array" in {
    //   val result = sr.extractValue("[a,b,c]", "[1]").get
    //   result shouldBe "b"
    // }
    
    // "extract [2] from string array" in {
    //   val result = sr.extractValue("[a,b,c]", "[2]").get
    //   result shouldBe "c"
    // }
    
    // "extract [0] from quoted array" in {
    //   val result = sr.extractValue("""["hello","world","test"]""", "[0]").get
    //   result shouldBe """"hello""""
    // }
    
    // "extract [1] from quoted array" in {
    //   val result = sr.extractValue("""["hello","world","test"]""", "[1]").get
    //   result shouldBe """"world""""
    // }
    
    // "extract [2] from quoted array" in {
    //   val result = sr.extractValue("""["hello","world","test"]""", "[2]").get
    //   result shouldBe """"test""""
    // }
    
    // "extract (_1) from tuple" in {
    //   val result = sr.extractValue("(1,2,3)", "(_1)").get
    //   result shouldBe "1"
    // }
    
    // "extract (_2) from tuple" in {
    //   val result = sr.extractValue("(1,2,3)", "(_2)").get
    //   result shouldBe "2"
    // }
    
    // "extract (_3) from tuple" in {
    //   val result = sr.extractValue("(1,2,3)", "(_3)").get
    //   result shouldBe "3"
    // }
    
    // "extract (_1) from string tuple" in {
    //   val result = sr.extractValue("(a,b,c)", "(_1)").get
    //   result shouldBe "a"
    // }
    
    // "extract (_2) from string tuple" in {
    //   val result = sr.extractValue("(a,b,c)", "(_2)").get
    //   result shouldBe "b"
    // }
    
    // "extract (_3) from string tuple" in {
    //   val result = sr.extractValue("(a,b,c)", "(_3)").get
    //   result shouldBe "c"
    // }
    
    // "extract (_1) from quoted tuple" in {
    //   val result = sr.extractValue("""("hello","world","test")""", "(_1)").get
    //   result shouldBe """"hello""""
    // }
    
    // "extract (_2) from quoted tuple" in {
    //   val result = sr.extractValue("""("hello","world","test")""", "(_2)").get
    //   result shouldBe """"world""""
    // }
    
    // "extract (_3) from quoted tuple" in {
    //   val result = sr.extractValue("""("hello","world","test")""", "(_3)").get
    //   result shouldBe """"test""""
    // }
    
    // "extract _1(_1) from mixed structure" in {
    //   val result = sr.extractValue("(1,2),3", "_1(_1)").get
    //   result shouldBe "1"
    // }
    
    // "extract _1(_2) from mixed structure" in {
    //   val result = sr.extractValue("(1,2),3", "_1(_2)").get
    //   result shouldBe "2"
    // }
    
    // "extract _2 from mixed structure" in {
    //   val result = sr.extractValue("(1,2),3", "_2").get
    //   result shouldBe "3"
    // }
    
    // "extract _1[0] from array mixed structure" in {
    //   val result = sr.extractValue("[1,2],3", "_1[0]").get
    //   result shouldBe "1"
    // }
    
    // "extract _1[1] from array mixed structure" in {
    //   val result = sr.extractValue("[1,2],3", "_1[1]").get
    //   result shouldBe "2"
    // }
    
    // "extract _2 from array mixed structure" in {
    //   val result = sr.extractValue("[1,2],3", "_2").get
    //   result shouldBe "3"
    // }
    
    // "extract _1 from tuple mixed structure" in {
    //   val result = sr.extractValue("1,(2,3)", "_1").get
    //   result shouldBe "1"
    // }
    
    // "extract _2(_1) from tuple mixed structure" in {
    //   val result = sr.extractValue("1,(2,3)", "_2(_1)").get
    //   result shouldBe "2"
    // }
    
    // "extract _2(_2) from tuple mixed structure" in {
    //   val result = sr.extractValue("1,(2,3)", "_2(_2)").get
    //   result shouldBe "3"
    // }
    
    // "extract (_1) from complex nested" in {
    //   val result = sr.extractValue("""("hello",(1,2,"world"))""", "(_1)").get
    //   result shouldBe """"hello""""
    // }
    
    // "extract (_2(_1)) from complex nested" in {
    //   val result = sr.extractValue("""("hello",(1,2,"world"))""", "(_2(_1))").get
    //   result shouldBe "1"
    // }
    
    // "extract (_2(_2)) from complex nested" in {
    //   val result = sr.extractValue("""("hello",(1,2,"world"))""", "(_2(_2))").get
    //   result shouldBe "2"
    // }
    
    // "extract (_2(_3)) from complex nested" in {
    //   val result = sr.extractValue("""("hello",(1,2,"world"))""", "(_2(_3))").get
    //   result shouldBe """"world""""
    // }
    
    // "extract [0](_1) from array of tuples" in {
    //   val result = sr.extractValue("[(1,2),(3,4)]", "[0](_1)").get
    //   result shouldBe "1"
    // }
    
    // "extract [0](_2) from array of tuples" in {
    //   val result = sr.extractValue("[(1,2),(3,4)]", "[0](_2)").get
    //   result shouldBe "2"
    // }
    
    // "extract [1](_1) from array of tuples" in {
    //   val result = sr.extractValue("[(1,2),(3,4)]", "[1](_1)").get
    //   result shouldBe "3"
    // }
    
    // "extract [1](_2) from array of tuples" in {
    //   val result = sr.extractValue("[(1,2),(3,4)]", "[1](_2)").get
    //   result shouldBe "4"
    // }
    
    // "extract _1 with spaces in path" in {
    //   val result = sr.extractValue("1,2,3", " _1 ").get
    //   result shouldBe "1"
    // }
    
    // "extract _2 with spaces in path" in {
    //   val result = sr.extractValue("1,2,3", " _2 ").get
    //   result shouldBe "2"
    // }
    
    // "extract _3 with spaces in path" in {
    //   val result = sr.extractValue("1,2,3", " _3 ").get
    //   result shouldBe "3"
    // }
    
    // "extract [0] with spaces in path" in {
    //   val result = sr.extractValue("[1,2,3]", " [0] ").get
    //   result shouldBe "1"
    // }
    
    // "extract [1] with spaces in path" in {
    //   val result = sr.extractValue("[1,2,3]", " [1] ").get
    //   result shouldBe "2"
    // }
    
    // "extract [2] with spaces in path" in {
    //   val result = sr.extractValue("[1,2,3]", " [2] ").get
    //   result shouldBe "3"
    // }
    
    // "extract (_1) with spaces in path" in {
    //   val result = sr.extractValue("(1,2,3)", " (_1) ").get
    //   result shouldBe "1"
    // }
    
    // "extract (_2) with spaces in path" in {
    //   val result = sr.extractValue("(1,2,3)", " (_2) ").get
    //   result shouldBe "2"
    // }
    
    // "extract (_3) with spaces in path" in {
    //   val result = sr.extractValue("(1,2,3)", " (_3) ").get
    //   result shouldBe "3"
    // }
    
    // "extract _1 with multiple spaces in path" in {
    //   val result = sr.extractValue("1,2,3", " _ 1 ").get
    //   result shouldBe "1"
    // }
    
    // "extract _2 with multiple spaces in path" in {
    //   val result = sr.extractValue("1,2,3", " _ 2 ").get
    //   result shouldBe "2"
    // }
    
    // "extract _3 with multiple spaces in path" in {
    //   val result = sr.extractValue("1,2,3", " _ 3 ").get
    //   result shouldBe "3"
    // }
    
    // "extract [0] with multiple spaces in path" in {
    //   val result = sr.extractValue("[1,2,3]", " [ 0 ] ").get
    //   result shouldBe "1"
    // }
    
    // "extract [1] with multiple spaces in path" in {
    //   val result = sr.extractValue("[1,2,3]", " [ 1 ] ").get
    //   result shouldBe "2"
    // }
    
    // "extract [2] with multiple spaces in path" in {
    //   val result = sr.extractValue("[1,2,3]", " [ 2 ] ").get
    //   result shouldBe "3"
    // }
    
    // "extract (_1) with multiple spaces in path" in {
    //   val result = sr.extractValue("(1,2,3)", " ( _ 1 ) ").get
    //   result shouldBe "1"
    // }
    
    // "extract (_2) with multiple spaces in path" in {
    //   val result = sr.extractValue("(1,2,3)", " ( _ 2 ) ").get
    //   result shouldBe "2"
    // }
    
    // "extract (_3) with multiple spaces in path" in {
    //   val result = sr.extractValue("(1,2,3)", " ( _ 3 ) ").get
    //   result shouldBe "3"
    // }
    
    // "handle empty path for comma-delimited" in {
    //   val result = sr.extractValue("1,2,3", "").get
    //   result shouldBe "1,2,3"
    // }
    
    // "handle empty path for array" in {
    //   val result = sr.extractValue("[1,2,3]", "").get
    //   result shouldBe "[1,2,3]"
    // }
    
    // "handle empty path for tuple" in {
    //   val result = sr.extractValue("(1,2,3)", "").get
    //   result shouldBe "(1,2,3)"
    // }
    
    // "handle empty path for quoted string" in {
    //   val result = sr.extractValue(""""hello"""", "").get
    //   result shouldBe """"hello""""
    // }
    
    // "handle single unquoted value" in {
    //   val result = sr.extractValue("hello", "").get
    //   result shouldBe "hello"
    // }
    
    // "handle single numeric value" in {
    //   val result = sr.extractValue("123", "").get
    //   result shouldBe "123"
    // }
    
    // "handle single hex value" in {
    //   val result = sr.extractValue("0xFF", "").get
    //   result shouldBe "0xFF"
    // }
    
    // "handle single quoted string" in {
    //   val result = sr.extractValue(""""quoted string"""", "").get
    //   result shouldBe """"quoted string""""
    // }
    
    // "handle empty input" in {
    //   val result = sr.extractValue("", "").get
    //   result shouldBe ""
    // }
    
    // "handle empty tuple" in {
    //   val result = sr.extractValue("()", "").get
    //   result shouldBe "()"
    // }
    
    // "handle empty array" in {
    //   val result = sr.extractValue("[]", "").get
    //   result shouldBe "[]"
    // }
    
    // "handle single element tuple" in {
    //   val result = sr.extractValue("(1,)", "").get
    //   result shouldBe "(1,)"
    // }
    
    // "handle single element array" in {
    //   val result = sr.extractValue("[1,]", "").get
    //   result shouldBe "[1,]"
    // }
    
    // "fail on invalid comma index _0" in {
    //   val result = sr.extractValue("1,2,3", "_0")
    //   result.isFailure shouldBe true
    // }
    
    // "fail on out of bounds comma index _4" in {
    //   val result = sr.extractValue("1,2,3", "_4")
    //   result.isFailure shouldBe true
    // }
    
    // "fail on negative array index [-1]" in {
    //   val result = sr.extractValue("[1,2,3]", "[-1]")
    //   result.isFailure shouldBe true
    // }
    
    // "fail on out of bounds array index [3]" in {
    //   val result = sr.extractValue("[1,2,3]", "[3]")
    //   result.isFailure shouldBe true
    // }
    
    // "fail on invalid tuple index (_0)" in {
    //   val result = sr.extractValue("(1,2,3)", "(_0)")
    //   result.isFailure shouldBe true
    // }
    
    // "fail on out of bounds tuple index (_4)" in {
    //   val result = sr.extractValue("(1,2,3)", "(_4)")
    //   result.isFailure shouldBe true
    // }
    
    // "fail on invalid path syntax" in {
    //   val result = sr.extractValue("1,2,3", "invalid")
    //   result.isFailure shouldBe true
    // }
    
    // "fail on wrong path type for comma-delimited" in {
    //   val result = sr.extractValue("1,2,3", "[0]")
    //   result.isFailure shouldBe true
    // }
    
    // "fail on wrong path type for array" in {
    //   val result = sr.extractValue("[1,2,3]", "_1")
    //   result.isFailure shouldBe true
    // }
    
    // "fail on wrong path type for tuple" in {
    //   val result = sr.extractValue("(1,2,3)", "[0]")
    //   result.isFailure shouldBe true
    // }
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
  }
} 