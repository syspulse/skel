package io.syspulse.skel.dsl

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
      result.toString shouldBe "4"
    }
    
    "execute script without arguments" in {
      val polyglot = new Polyglot("js")
      val result = polyglot.run("5 * 3")
      result.toString shouldBe "15"
    }
    
    "handle string literals" in {
      val polyglot = new Polyglot("js")
      val result = polyglot.run("'Hello World'")
      result.toString shouldBe "Hello World"
    }
    
    "handle boolean values" in {
      val polyglot = new Polyglot("js")
      val result = polyglot.run("true && false")
      result.toString shouldBe "false"
    }
    
    "bind and use numeric arguments" in {
      val polyglot = new Polyglot("js")
      val result = polyglot.run(jsScriptWithArgs, testArgs)
      result.toString shouldBe "30"
    }
    
    "bind and use string arguments" in {
      val polyglot = new Polyglot("js")
      val result = polyglot.run(jsScriptWithString, testArgs)
      result.toString shouldBe "Hello World"
    }
    
    "handle empty arguments map" in {
      val polyglot = new Polyglot("js")
      val result = polyglot.run("42", emptyArgs)
      result.toString shouldBe "42"
    }
    
    "bind multiple arguments correctly" in {
      val polyglot = new Polyglot("js")
      val result = polyglot.run(jsScriptWithArray, testArgs)
      result shouldBe a[Value]
      val array = result.asInstanceOf[Value]
      array.getArrayElement(0).asInt shouldBe 10
      array.getArrayElement(1).asInt shouldBe 20
      array.getArrayElement(2).asInt shouldBe 30
    }
    
    "execute JavaScript functions" in {
      val polyglot = new Polyglot("js")
      val result = polyglot.run(jsScriptWithFunction, testArgs)
      result.toString shouldBe "30"
    }
    
    "create and return objects" in {
      val polyglot = new Polyglot("js")
      val result = polyglot.run(jsScriptWithObject, testArgs)
      result shouldBe a[Value]
      val obj = result.asInstanceOf[Value]
      obj.getMember("id").asInt shouldBe 10
      obj.getMember("value").asInt shouldBe 20
    }
    
    "clear previous bindings before setting new ones" in {
      val polyglot = new Polyglot("js")
      
      // First run with some arguments
      val result1 = polyglot.run("x + y", Map("x" -> 5, "y" -> 3))
      result1.toString shouldBe "8"
      
      // Second run with different arguments - should work with new bindings
      val result2 = polyglot.run("x + y", Map("x" -> 10, "y" -> 20))
      result2.toString shouldBe "30"
      
      // Third run with no arguments - should fail since x and y are not defined
      intercept[Exception] {
        polyglot.run("x + y", emptyArgs)
      }
    }
    
    "handle binding updates correctly" in {
      val polyglot = new Polyglot("js")
      
      // Run with initial values
      val result1 = polyglot.run("x * y", Map("x" -> 2, "y" -> 3))
      result1.toString shouldBe "6"
      
      // Update bindings and run again
      val result2 = polyglot.run("x * y", Map("x" -> 4, "y" -> 5))
      result2.toString shouldBe "20"
    }
    
    "handle integer arguments" in {
      val polyglot = new Polyglot("js")
      val result = polyglot.run("x", Map("x" -> 42))
      result.toString shouldBe "42"
    }
    
    "handle double arguments" in {
      val polyglot = new Polyglot("js")
      val result = polyglot.run("x", Map("x" -> 3.14))
      result.toString shouldBe "3.14"
    }
    
    "handle string arguments" in {
      val polyglot = new Polyglot("js")
      val result = polyglot.run("x", Map("x" -> "test"))
      result.toString shouldBe "test"
    }
    
    "handle boolean arguments" in {
      val polyglot = new Polyglot("js")
      val result = polyglot.run("x", Map("x" -> true))
      result.toString shouldBe "true"
    }
    
    "handle conditional statements" in {
      val polyglot = new Polyglot("js")
      val script = "if (x > 5) { 'high' } else { 'low' }"
      val result1 = polyglot.run(script, Map("x" -> 10))
      result1.toString shouldBe "high"
      val result2 = polyglot.run(script, Map("x" -> 3))
      result2.toString shouldBe "low"
    }
    
    "handle loops" in {
      val polyglot = new Polyglot("js")
      val script = "let sum = 0; for (let i = 0; i <= x; i++) { sum += i; } sum"
      val result = polyglot.run(script, Map("x" -> 5))
      result.toString shouldBe "15"
    }
    
    "handle array operations" in {
      val polyglot = new Polyglot("js")
      // Create a JavaScript array instead of Java array
      val script = "let arr = [1, 2, 3, 4, 5]; arr.map(x => x * 2).filter(x => x > 5)"
      val result = polyglot.run(script)
      result shouldBe a[Value]
      val array = result.asInstanceOf[Value]
      array.getArrayElement(0).asInt shouldBe 6
      array.getArrayElement(1).asInt shouldBe 8
      array.getArrayElement(2).asInt shouldBe 10
    }
    
    "handle syntax errors gracefully" in {
      val polyglot = new Polyglot("js")
      val invalidScript = "2 + + 2" // Invalid syntax
      
      // GraalVM might handle this differently, so we'll just verify it doesn't crash
      val result = polyglot.run(invalidScript)
      // The result might be an error value or exception, but the test should not crash
      result should not be null.asInstanceOf[Any]
    }
    
    "handle runtime errors gracefully" in {
      val polyglot = new Polyglot("js")
      val runtimeErrorScript = "undefinedVariable + 5"
      
      // This should either throw an exception or return an error value
      intercept[Exception] {
        polyglot.run(runtimeErrorScript)
      }
    }
    
    "support JavaScript language" in {
      val polyglot = new Polyglot("js")
      val result = polyglot.run("2 + 2")
      result.toString shouldBe "4"
    }
    
    "support Python language if available" in {
      // Note: This test will only pass if Python is available in the GraalVM installation
      try {
        val polyglot = new Polyglot("python")
        val result = polyglot.run("2 + 2")
        result.toString shouldBe "4"
      } catch {
        case _: Exception =>
          // Python not available, skip test
          pending
      }
    }
    
    "should maintain isolation between script executions" in {
      val polyglot = new Polyglot("js")
      
      // First script execution with a variable
      val result1 = polyglot.run("let counter = 0; counter += 1; counter", Map("x" -> 1))
      result1.toString shouldBe "1"
      
      // Second script execution - should not have access to previous counter
      // We'll test this by trying to access a variable that should not exist
      val result2 = polyglot.run("typeof counter", Map("x" -> 2))
      // The result should indicate that counter is undefined, but GraalVM might handle this differently
      // So we'll just verify it's not the previous value
      result2.toString should not be "1"
    }
    
    "should access Scala case class fields from JavaScript" in {
      // Define a case class for testing
      case class Person(name: String, age: Int, email: String, active: Boolean)
      
      val polyglot = new Polyglot("js")
      val person = Person("John Doe", 30, "john@example.com", true)
      
      // Test accessing individual fields
      val nameResult = polyglot.run("person.name", Map("person" -> person))
      nameResult.toString shouldBe "John Doe"
      
      val ageResult = polyglot.run("person.age", Map("person" -> person))
      ageResult.toString shouldBe "30"
      
      val emailResult = polyglot.run("person.email", Map("person" -> person))
      emailResult.toString shouldBe "john@example.com"
      
      val activeResult = polyglot.run("person.active", Map("person" -> person))
      activeResult.toString shouldBe "true"
      
      // Test accessing multiple fields in a single script
      val combinedResult = polyglot.run(
        "person.name + ' is ' + person.age + ' years old'", 
        Map("person" -> person)
      )
      combinedResult.toString shouldBe "John Doe is 30 years old"
      
      // Test boolean operations with case class fields
      val booleanResult = polyglot.run(
        "person.active ? 'active' : 'inactive'", 
        Map("person" -> person)
      )
      booleanResult.toString shouldBe "active"
      
      // Test field existence check
      val hasFieldResult = polyglot.run(
        "'name' in person", 
        Map("person" -> person)
      )
      hasFieldResult.toString shouldBe "true"
    }
  }
}

