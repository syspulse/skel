package io.syspulse.skel.crypto

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach

import java.util.{Map => JavaMap, HashMap => JavaHashMap}
import scala.jdk.CollectionConverters._
import scala.util.{Try, Success, Failure}

import org.web3j.protocol.http.HttpService
import io.syspulse.skel.crypto.eth.DebugTraceCall

class Web3jTraceSpec extends AnyWordSpec with Matchers with BeforeAndAfterEach {
  
  // Test data
//   val testRpcUrl = "https://mainnet.infura.io/v3/test"
  val testRpcUrl = "http://geth:8545"
  val testFrom = "0x742d35Cc6634C0532925a3b8D4C9db96C4b4d8b6"
  val testTo = "0x742d35Cc6634C0532925a3b8D4C9db96C4b4d8b6"
  val testData = "0xa9059cbb000000000000000000000000742d35cc6634c0532925a3b8d4c9db96c4b4d8b60000000000000000000000000000000000000000000000000000000000000001"
  
  var web3jTrace: DebugTraceCall = _
  
  override def beforeEach(): Unit = {
    val httpService = new HttpService(testRpcUrl)
    web3jTrace = DebugTraceCall.build(httpService)
  }
  
  override def afterEach(): Unit = {
    if (web3jTrace != null) {
      web3jTrace.shutdown()
    }
  }
  
  "Web3jTrace" should {
    
    "be created with HttpService using build method" in {
      val httpService = new HttpService(testRpcUrl)
      val trace = DebugTraceCall.build(httpService)
      
      trace should not be null
      trace.shutdown()
    }
    
    "create traceCall request correctly" in {
      val request = web3jTrace.traceCall(testFrom, testTo, testData)
      
      request should not be null
      request.getMethod shouldBe "debug_traceCall"
      request.getParams should not be null
    }
    
    "handle null parameters gracefully" in {
      val result = Try {
        web3jTrace.traceCall(null, testTo, testData)
      }
      
      result.isSuccess shouldBe true
    }
    
    "handle empty parameters" in {
      val result = Try {
        web3jTrace.traceCall("", testTo, testData)
      }
      
      result.isSuccess shouldBe true
    }
    
    "should handle different data formats" in {
      val testCases = List(
        ("0x", "Empty data"),
        ("0xa9059cbb", "Function selector only"),
        ("0xa9059cbb000000000000000000000000742d35cc6634c0532925a3b8d4c9db96c4b4d8b60000000000000000000000000000000000000000000000000000000000000001", "Full transfer data")
      )
      
      testCases.foreach { case (data, description) =>
        val result = Try {
          web3jTrace.traceCall(testFrom, testTo, data).send()
        }
        
        result.isSuccess shouldBe true
        val response = result.get
        
        // Test JSON result functionality
        val jsonResult = response.getResultAsJson()
        jsonResult should not be null
        
        info(s"$description - JSON result: $jsonResult")
        
        // Test getting result as specific type
        val resultAsMap = response.getResultAs(classOf[java.util.Map[String, Object]])
        resultAsMap should not be null
      }
    }
    
    "handle different address formats" in {
      val addresses = List(
        "0x742d35Cc6634C0532925a3b8D4C9db96C4b4d8b6", // checksummed
        "0x742d35cc6634c0532925a3b8d4c9db96c4b4d8b6", // lowercase
        "0x742D35CC6634C0532925A3B8D4C9DB96C4B4D8B6"  // uppercase
      )
      
      addresses.foreach { addr =>
        val result = Try {
          web3jTrace.traceCall(addr, testTo, testData)
        }
        
        result.isSuccess shouldBe true
      }
    }
    
    "create request with correct parameters structure" in {
      val request = web3jTrace.traceCall(testFrom, testTo, testData)
      val params = request.getParams
      
      params should not be null
      params.size() shouldBe 3 // [callObject, blockNumber, options]
      
      // First param should be the call object
      val callObject = params.get(0).asInstanceOf[JavaMap[String, String]]
      callObject.get("from") shouldBe testFrom
      callObject.get("to") shouldBe testTo
      callObject.get("data") shouldBe testData
      
      // Second param should be block number
      params.get(1) shouldBe "latest"
      
      // Third param should be options
      val options = params.get(2).asInstanceOf[JavaMap[String, Object]]
      options.get("tracer") shouldBe "callTracer" // Default tracer
      options.get("tracerConfig") shouldBe null // No tracerConfig by default
    }
    
    "support different tracer types" in {
      val tracers = List("callTracer", "stateDiffTracer", "prestateTracer")
      
      tracers.foreach { tracer =>
        val request = web3jTrace.traceCall(testFrom, testTo, testData, tracer)
        val params = request.getParams
        val options = params.get(2).asInstanceOf[JavaMap[String, Object]]
        
        options.get("tracer") shouldBe tracer
      }
    }
    
    "support tracerConfig parameter" in {
      val tracerConfig = Map[String, Object](
        "disableStorage" -> true.asInstanceOf[Object],
        "disableCode" -> true.asInstanceOf[Object]
      )
      
      val request = web3jTrace.traceCall(testFrom, testTo, testData, "prestateTracer", tracerConfig.asJava)
      val params = request.getParams
      val options = params.get(2).asInstanceOf[JavaMap[String, Object]]
      
      options.get("tracer") shouldBe "prestateTracer"
      options.get("tracerConfig") shouldBe tracerConfig.asJava
    }
    
    "support tracerConfig with different options" in {
      val tracerConfigs = List(
        Map[String, Object]("disableStorage" -> true.asInstanceOf[Object]),
        Map[String, Object]("disableCode" -> true.asInstanceOf[Object]),
        Map[String, Object](
          "disableStorage" -> true.asInstanceOf[Object],
          "disableCode" -> true.asInstanceOf[Object],
          "onlyTopCall" -> false.asInstanceOf[Object]
        )
      )
      
      tracerConfigs.foreach { config =>
        val request = web3jTrace.traceCall(testFrom, testTo, testData, "prestateTracer", config.asJava)
        val params = request.getParams
        val options = params.get(2).asInstanceOf[JavaMap[String, Object]]
        
        options.get("tracer") shouldBe "prestateTracer"
        options.get("tracerConfig") shouldBe config.asJava
      }
    }
    
    "simulate transfer(address,uint256)" in {
      val data = "0xa9059cbb000000000000000000000000fc011860c9e4b840ab97c2c3936611c88fce3673000000000000000000000000000000000000000000000000000000000000000b"
      val result = Try {
        web3jTrace.traceCall("0xfC011860c9E4B840AB97c2c3936611c88fcE3673", "0xe76C6c83af64e4C60245D8C7dE953DF673a7A33D", data).send()
      }
      
      result.isSuccess shouldBe true
    }
    
    "simulate transfer(address,uint256) with prestateTracer" in {
      val data = "0xa9059cbb000000000000000000000000fc011860c9e4b840ab97c2c3936611c88fce3673000000000000000000000000000000000000000000000000000000000000000b"
      val result = Try {
        web3jTrace.traceCall("0xfC011860c9E4B840AB97c2c3936611c88fcE3673", "0xe76C6c83af64e4C60245D8C7dE953DF673a7A33D", data, "prestateTracer").send()
      }

      //info(s"result = ${result}")      
      
      result.isSuccess shouldBe true
    }
    
    "simulate transfer with prestateTracer and tracerConfig" in {
      val data = "0xa9059cbb000000000000000000000000fc011860c9e4b840ab97c2c3936611c88fce3673000000000000000000000000000000000000000000000000000000000000000b"
      val tracerConfig = Map[String, Object](
        "disableStorage" -> true.asInstanceOf[Object],
        "disableCode" -> true.asInstanceOf[Object]
      )
      
      val result = Try {
        web3jTrace.traceCall("0xfC011860c9E4B840AB97c2c3936611c88fcE3673", "0xe76C6c83af64e4C60245D8C7dE953DF673a7A33D", data, "prestateTracer", tracerConfig.asJava).send()
      }
      
      result.isSuccess shouldBe true
    }

    "simulate transfer(address,uint256) with callTracer / withLog" in {
      val data = "0xa9059cbb000000000000000000000000fc011860c9e4b840ab97c2c3936611c88fce3673000000000000000000000000000000000000000000000000000000000000000b"
      val tracerConfig = Map[String, Object](
        "withLog" -> true.asInstanceOf[Object],
        "onlyTopCall" -> false.asInstanceOf[Object]
      )
      val result = Try {
        web3jTrace.traceCall("0xfC011860c9E4B840AB97c2c3936611c88fcE3673", "0xe76C6c83af64e4C60245D8C7dE953DF673a7A33D", 
            data, "callTracer", tracerConfig.asJava
        ).send()
      }

      info(s"result = ${result}")      
      
      result.isSuccess shouldBe true
    }

    "simulate Reverted trace call stack with callTracer / withLog" in {
      val data = "0x78e111f60000000000000000000000004db8adc45492a191dab373fe5cb76f84a4eacda300000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000008f4ff529500000000000000000000000000000000000000000000000000000000"
      val tracerConfig = Map[String, Object](
        "withLog" -> true.asInstanceOf[Object],
        "onlyTopCall" -> false.asInstanceOf[Object]
      )
      val result = Try {
        web3jTrace.traceCall("0xc33e9AA83cb52854211f3ca85608E755b6AC932d", "0xA69babEF1cA67A37Ffaf7a485DfFF3382056e78C", 
            data, "callTracer", tracerConfig.asJava
        ).send()
      }

      info(s"result = ${result}")      
      
      result.isSuccess shouldBe true
    }

    "should return JSON results for different tracer types" in {
      val tracers = List("callTracer", "prestateTracer")
      val data = "0xa9059cbb000000000000000000000000742d35cc6634c0532925a3b8d4c9db96c4b4d8b60000000000000000000000000000000000000000000000000000000000000001"
      
      tracers.foreach { tracer =>
        val result = Try {
          web3jTrace.traceCall(testFrom, testTo, data, tracer).send()
        }
        
        result.isSuccess shouldBe true
        val response = result.get
        
        // Get result as JSON string
        val jsonResult = response.getResultAsJson()
        jsonResult should not be null
        jsonResult should not be empty
        
        info(s"$tracer JSON result: $jsonResult")
        
        // Verify it's valid JSON
        jsonResult should startWith("{")
        jsonResult should endWith("}")
      }
    }

    "should return pretty JSON results" in {
      val data = "0xa9059cbb000000000000000000000000742d35cc6634c0532925a3b8d4c9db96c4b4d8b60000000000000000000000000000000000000000000000000000000000000001"
      
      val result = Try {
        web3jTrace.traceCall(testFrom, testTo, data, "callTracer").send()
      }
      
      result.isSuccess shouldBe true
      val response = result.get
      
      // Get compact JSON
      val compactJson = response.getResultAsJson(false)
      compactJson should not be null
      compactJson should not contain "\n" // Should be on one line
      
      // Get pretty JSON
      val prettyJson = response.getResultAsJson(true)
      prettyJson should not be null
      prettyJson should contain("\n") // Should have line breaks
      prettyJson should contain("  ") // Should have indentation
      
      info(s"Compact JSON: $compactJson")
      info(s"Pretty JSON:\n$prettyJson")
      
      // Both should represent the same data
      compactJson.replaceAll("\\s", "") shouldBe prettyJson.replaceAll("\\s", "")
    }
  }  
} 