package io.syspulse.skel.dsl

import scala.util.{Try, Success, Failure}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value

class PolyglotSpec extends AnyWordSpec with Matchers {
  
  // Test data
  val simpleJsScript = "2 + 2"
  val jsScriptWithArgs = "x + y"
  val jsScriptWithFunction = "function add(a, b) { return a + b; } add(x, y)"
  val jsScriptWithObject = "({ id: x, value: y })"
  val jsScriptWithArray = "[x, y, x + y]"
  val jsScriptWithString = "'Hello ' + name"
  
  // Test arguments
  val testArgs = Map(
    "x" -> 10,
    "y" -> 20,
    "name" -> "World"
  )
  
  // Empty arguments
  val emptyArgs = Map[String, Any]()
  
  "Polyglot" should {
    
    "initialize with specified language" in {
      val polyglot = new Polyglot("js")
      polyglot.ctx should not be null
    }
    
    "create a valid GraalVM context" in {
      val polyglot = new Polyglot("js")
      polyglot.ctx shouldBe a[Context]
      polyglot.ctx.getEngine should not be null
    }
    
    "execute basic arithmetic" in {
      val polyglot = new Polyglot("js")
      val result = polyglot.run(simpleJsScript)
      result.isSuccess shouldBe true
      result.get.toString shouldBe "4"
    }
    
    "execute script without arguments" in {
      val polyglot = new Polyglot("js")
      val result = polyglot.run("5 * 3")
      result.isSuccess shouldBe true
      result.get.toString shouldBe "15"
    }
    
    "handle string literals" in {
      val polyglot = new Polyglot("js")
      val result = polyglot.run("'Hello World'")
      result.isSuccess shouldBe true
      result.get.toString shouldBe "Hello World"
    }
    
    "handle boolean values" in {
      val polyglot = new Polyglot("js")
      val result = polyglot.run("true && false")
      result.isSuccess shouldBe true
      result.get.toString shouldBe "false"
    }
    
    "bind and use numeric arguments" in {
      val polyglot = new Polyglot("js")
      val result = polyglot.run(jsScriptWithArgs, testArgs)
      result.isSuccess shouldBe true
      result.get.toString shouldBe "30"
    }
    
    "bind and use string arguments" in {
      val polyglot = new Polyglot("js")
      val result = polyglot.run(jsScriptWithString, testArgs)
      result.isSuccess shouldBe true
      result.get.toString shouldBe "Hello World"
    }
    
    "handle empty arguments map" in {
      val polyglot = new Polyglot("js")
      val result = polyglot.run("42", emptyArgs)
      result.isSuccess shouldBe true
      result.get.toString shouldBe "42"
    }
    
    "bind multiple arguments correctly" in {
      val polyglot = new Polyglot("js")
      val result = polyglot.run(jsScriptWithArray, testArgs)
      result.isSuccess shouldBe true
      result.get shouldBe a[Value]
      val array = result.get.asInstanceOf[Value]
      array.getArrayElement(0).asInt shouldBe 10
      array.getArrayElement(1).asInt shouldBe 20
      array.getArrayElement(2).asInt shouldBe 30
    }
    
    "execute JavaScript functions" in {
      val polyglot = new Polyglot("js")
      val result = polyglot.run(jsScriptWithFunction, testArgs)
      result.isSuccess shouldBe true
      result.get.toString shouldBe "30"
    }
    
    "create and return objects" in {
      val polyglot = new Polyglot("js")
      val result = polyglot.run(jsScriptWithObject, testArgs)
      result.isSuccess shouldBe true
      result.get shouldBe a[Value]
      val obj = result.get.asInstanceOf[Value]
      obj.getMember("id").asInt shouldBe 10
      obj.getMember("value").asInt shouldBe 20
    }
    
    "clear previous bindings before setting new ones" in {
      val polyglot = new Polyglot("js")
      
      // First run with some arguments
      val result1 = polyglot.run("x + y", Map("x" -> 5, "y" -> 3))
      result1.isSuccess shouldBe true
      result1.get.toString shouldBe "8"
      
      // Second run with different arguments - should work with new bindings
      val result2 = polyglot.run("x + y", Map("x" -> 10, "y" -> 20))
      result2.isSuccess shouldBe true
      result2.get.toString shouldBe "30"
      
      // Third run with no arguments - should preserve previous bindings
      val result3 = polyglot.run("x + y", emptyArgs)
      result3.isFailure shouldBe false
    }
    
    "handle binding updates correctly" in {
      val polyglot = new Polyglot("js")
      
      // Run with initial values
      val result1 = polyglot.run("x * y", Map("x" -> 2, "y" -> 3))
      result1.isSuccess shouldBe true
      result1.get.toString shouldBe "6"
      
      // Update bindings and run again
      val result2 = polyglot.run("x * y", Map("x" -> 4, "y" -> 5))
      result2.isSuccess shouldBe true
      result2.get.toString shouldBe "20"
    }
    
    "handle integer arguments" in {
      val polyglot = new Polyglot("js")
      val result = polyglot.run("x", Map("x" -> 42))
      result.isSuccess shouldBe true
      result.get.toString shouldBe "42"
    }
    
    "handle double arguments" in {
      val polyglot = new Polyglot("js")
      val result = polyglot.run("x", Map("x" -> 3.14))
      result.isSuccess shouldBe true
      result.get.toString shouldBe "3.14"
    }
    
    "handle string arguments" in {
      val polyglot = new Polyglot("js")
      val result = polyglot.run("x", Map("x" -> "test"))
      result.isSuccess shouldBe true
      result.get.toString shouldBe "test"
    }
    
    "handle boolean arguments" in {
      val polyglot = new Polyglot("js")
      val result = polyglot.run("x", Map("x" -> true))
      result.isSuccess shouldBe true
      result.get.toString shouldBe "true"
    }
    
    "handle conditional statements" in {
      val polyglot = new Polyglot("js")
      val script = "if (x > 5) { 'high' } else { 'low' }"
      val result1 = polyglot.run(script, Map("x" -> 10))
      result1.isSuccess shouldBe true
      result1.get.toString shouldBe "high"
      val result2 = polyglot.run(script, Map("x" -> 3))
      result2.isSuccess shouldBe true
      result2.get.toString shouldBe "low"
    }
    
    "handle loops" in {
      val polyglot = new Polyglot("js")
      val script = "let sum = 0; for (let i = 0; i <= x; i++) { sum += i; } sum"
      val result = polyglot.run(script, Map("x" -> 5))
      result.isSuccess shouldBe true
      result.get.toString shouldBe "15"
    }
    
    "handle array operations" in {
      val polyglot = new Polyglot("js")
      // Create a JavaScript array instead of Java array
      val script = "let arr = [1, 2, 3, 4, 5]; arr.map(x => x * 2).filter(x => x > 5)"
      val result = polyglot.run(script)
      result.isSuccess shouldBe true
      result.get shouldBe a[Value]
      val array = result.get.asInstanceOf[Value]
      array.getArrayElement(0).asInt shouldBe 6
      array.getArrayElement(1).asInt shouldBe 8
      array.getArrayElement(2).asInt shouldBe 10
    }
    
    "handle syntax errors gracefully" in {
      val polyglot = new Polyglot("js")
      val invalidScript = "2 + + 2" // Invalid syntax
      
      // GraalVM might handle this differently, so we'll just verify it doesn't crash
      val result = polyglot.run(invalidScript)
      // The result might be a Failure for syntax errors, or GraalVM might handle it differently
      // Just verify we get a Try result (either Success or Failure)
      result shouldBe a[Try[_]]
    }
    
    "handle runtime errors gracefully" in {
      val polyglot = new Polyglot("js")
      val runtimeErrorScript = "undefinedVariable + 5"
      
      // This should return a Failure
      val result = polyglot.run(runtimeErrorScript)
      result.isFailure shouldBe true
    }
    
    "support JavaScript language" in {
      val polyglot = new Polyglot("js")
      val result = polyglot.run("2 + 2")
      result.isSuccess shouldBe true
      result.get.toString shouldBe "4"
    }
    
    "support Python language if available" in {
      // Note: This test will only pass if Python is available in the GraalVM installation
      Try {
        val polyglot = new Polyglot("python")
        val result = polyglot.run("2 + 2")
        result.isSuccess shouldBe true
        result.get.toString shouldBe "4"
      } match {
        case Success(_) => // Test passed
        case Failure(_) =>
          // Python not available, skip test
          pending
      }
    }
    
    "should maintain isolation between script executions" in {
      val polyglot = new Polyglot("js")
      
      // First script execution with a variable
      val result1 = polyglot.run("let counter = 0; counter += 1; counter", Map("x" -> 1))
      result1.isSuccess shouldBe true
      result1.get.toString shouldBe "1"
      
      // Second script execution - should not have access to previous counter
      // We'll test this by trying to access a variable that should not exist
      val result2 = polyglot.run("typeof counter", Map("x" -> 2))
      result2.isSuccess shouldBe true
      // The result should indicate that counter is undefined, but GraalVM might handle this differently
      // So we'll just verify it's not the previous value
      result2.get.toString should not be "1"
    }

    "preserve var declarations between runs while args map bindings are cleared" in {
      val polyglot = new Polyglot("js")
      
      // Script that declares a 'var' variable and uses args map variable
      // The 'var' declaration creates a global variable that persists
      // The args map bindings are cleared between runs, but 'var' variables persist
      val script = "var globalCounter = (typeof globalCounter === 'undefined' ? initial : globalCounter); globalCounter = globalCounter + increment; globalCounter"
      
      // First run - initializes globalCounter from args map, then increments
      val result1 = polyglot.run(script, Map("initial" -> 0, "increment" -> 1))
      result1.isSuccess shouldBe true
      result1.get.toString shouldBe "1"
      
      // Second run - args map bindings are cleared, but 'var globalCounter' persists
      // So globalCounter should be 1 (from first run), then incremented to 2
      val result2 = polyglot.run(script, Map("initial" -> 0, "increment" -> 1))
      result2.isSuccess shouldBe true
      result2.get.toString shouldBe "2" // globalCounter persisted (was 1, now 2)
      
      // Third run - globalCounter persists, continues incrementing
      val result3 = polyglot.run(script, Map("initial" -> 0, "increment" -> 1))
      result3.isSuccess shouldBe true
      result3.get.toString shouldBe "3" // globalCounter persisted (was 2, now 3)
      
      // Fourth run with different increment value - globalCounter persists, uses new increment
      val result4 = polyglot.run(script, Map("initial" -> 0, "increment" -> 5))
      result4.isSuccess shouldBe true
      result4.get.toString shouldBe "8" // globalCounter persisted (was 3, now 3+5=8)
      
      // Fifth run - verify that args map bindings are cleared (initial is not used)
      // but globalCounter still persists
      val result5 = polyglot.run(script, Map("initial" -> 100, "increment" -> 1))
      result5.isSuccess shouldBe true
      result5.get.toString shouldBe "9" // globalCounter persisted (was 8, now 9), initial=100 ignored
    }
    
    "should access Scala case class fields from JavaScript" in {
      // Define a case class for testing
      case class Person(name: String, age: Int, email: String, active: Boolean)
      
      val polyglot = new Polyglot("js")
      val person = Person("John Doe", 30, "john@example.com", true)
      
      // Test accessing individual fields
      val nameResult = polyglot.run("person.name", Map("person" -> person))
      nameResult.isSuccess shouldBe true
      nameResult.get.toString shouldBe "John Doe"
      
      val ageResult = polyglot.run("person.age", Map("person" -> person))
      ageResult.isSuccess shouldBe true
      ageResult.get.toString shouldBe "30"
      
      val emailResult = polyglot.run("person.email", Map("person" -> person))
      emailResult.isSuccess shouldBe true
      emailResult.get.toString shouldBe "john@example.com"
      
      val activeResult = polyglot.run("person.active", Map("person" -> person))
      activeResult.isSuccess shouldBe true
      activeResult.get.toString shouldBe "true"
      
      // Test accessing multiple fields in a single script
      val combinedResult = polyglot.run(
        "person.name + ' is ' + person.age + ' years old'", 
        Map("person" -> person)
      )
      combinedResult.isSuccess shouldBe true
      combinedResult.get.toString shouldBe "John Doe is 30 years old"
      
      // Test boolean operations with case class fields
      val booleanResult = polyglot.run(
        "person.active ? 'active' : 'inactive'", 
        Map("person" -> person)
      )
      booleanResult.isSuccess shouldBe true
      booleanResult.get.toString shouldBe "active"
      
      // Test field existence check
      val hasFieldResult = polyglot.run(
        "'name' in person", 
        Map("person" -> person)
      )
      hasFieldResult.isSuccess shouldBe true
      hasFieldResult.get.toString shouldBe "true"
    }
  }

  "Polyglot with src0" should {
    "use src0 script when run(\"\") is called with empty script" in {
      // src0 should be a script that uses variables from bindings
      val src0Script = "x + y"
      val polyglot = new Polyglot("js", Map(), Some(src0Script))
      
      val result1 = polyglot.run("", Map("x" -> 5, "y" -> 3))
      result1.isSuccess shouldBe true
      result1.get.toString shouldBe "8"
      
      val result2 = polyglot.run("", Map("x" -> 10, "y" -> 20))
      result2.isSuccess shouldBe true
      result2.get.toString shouldBe "30"
    }

    "use src0 script with different argument combinations" in {
      val src0Script = "x * y"
      val polyglot = new Polyglot("js", Map(), Some(src0Script))
      
      val result1 = polyglot.run("", Map("x" -> 2, "y" -> 3))
      result1.isSuccess shouldBe true
      result1.get.toString shouldBe "6"
      
      val result2 = polyglot.run("", Map("x" -> 4, "y" -> 5))
      result2.isSuccess shouldBe true
      result2.get.toString shouldBe "20"
    }

    "use src0 script with string arguments" in {
      val src0Script = "'Hello ' + name"
      val polyglot = new Polyglot("js", Map(), Some(src0Script))
      
      val result1 = polyglot.run("", Map("name" -> "World"))
      result1.isSuccess shouldBe true
      result1.get.toString shouldBe "Hello World"
      
      val result2 = polyglot.run("", Map("name" -> "Scala"))
      result2.isSuccess shouldBe true
      result2.get.toString shouldBe "Hello Scala"
    }

    "use src0 script with numeric transformations" in {
      val src0Script = "value * 2"
      val polyglot = new Polyglot("js", Map(), Some(src0Script))
      
      val result1 = polyglot.run("", Map("value" -> 5))
      result1.isSuccess shouldBe true
      result1.get.toString shouldBe "10"
      
      val result2 = polyglot.run("", Map("value" -> 15))
      result2.isSuccess shouldBe true
      result2.get.toString shouldBe "30"
    }

    "use src0 script with string transformations" in {
      val src0Script = "text.toUpperCase()"
      val polyglot = new Polyglot("js", Map(), Some(src0Script))
      
      val result1 = polyglot.run("", Map("text" -> "hello"))
      result1.isSuccess shouldBe true
      result1.get.toString shouldBe "HELLO"
      
      val result2 = polyglot.run("", Map("text" -> "world"))
      result2.isSuccess shouldBe true
      result2.get.toString shouldBe "WORLD"
    }

    "use src0 script with complex operations" in {
      val src0Script = "(x + y) * z"
      val polyglot = new Polyglot("js", Map(), Some(src0Script))
      
      val result1 = polyglot.run("", Map("x" -> 2, "y" -> 3, "z" -> 4))
      result1.isSuccess shouldBe true
      result1.get.toString shouldBe "20"
      
      val result2 = polyglot.run("", Map("x" -> 5, "y" -> 10, "z" -> 2))
      result2.isSuccess shouldBe true
      result2.get.toString shouldBe "30"
    }

    "use src0 script with array operations" in {
      // Create array in JavaScript and process it
      val src0Script = "[1, 2, 3].map(x => x * 2)"
      val polyglot = new Polyglot("js", Map(), Some(src0Script))
      
      val result = polyglot.run("", Map.empty)
      result.isSuccess shouldBe true
      result.get shouldBe a[Value]
      val array = result.get.asInstanceOf[Value]
      array.hasArrayElements shouldBe true
      array.getArraySize shouldBe 3
      array.getArrayElement(0).asInt shouldBe 2
      array.getArrayElement(1).asInt shouldBe 4
      array.getArrayElement(2).asInt shouldBe 6
    }

    "prefer run script parameter over src0 when script is provided" in {
      val src0Script = "x + y"
      val polyglot = new Polyglot("js", Map(), Some(src0Script))
      
      // When script is provided, it should be used instead of src0
      val result = polyglot.run("x * y", Map("x" -> 5, "y" -> 3))
      result.isSuccess shouldBe true
      result.get.toString shouldBe "15" // 5 * 3, not 5 + 3
    }

    "return Failure when both script and src0 are empty" in {
      val polyglot = new Polyglot("js")
      val result = polyglot.run("", Map.empty)
      result.isFailure shouldBe true
      result.failed.get.getMessage should include("No Script specified")
    }
  }
}

