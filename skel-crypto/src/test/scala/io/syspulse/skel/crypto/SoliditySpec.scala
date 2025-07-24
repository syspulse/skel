package io.syspulse.skel.crypto.eth

import scala.util.{Try,Success,Failure}

import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec
import org.web3j.abi.TypeReference
import io.syspulse.skel.util.Util

import org.web3j.abi.datatypes
import java.math.BigInteger
import scala.jdk.CollectionConverters._
import org.web3j.abi.FunctionEncoder
import io.syspulse.skel.crypto.TestData

class SoliditySpec extends AnyWordSpec with Matchers with TestData {
  
  "Solidity.toWeb3Type " should {

    "convert address" in {
      val r = Solidity.toWeb3Type("address","0x123")
      r.toString should === ("0x0000000000000000000000000000000000000123")
    }

    "convert uint256" in {
      val r = Solidity.toWeb3Type("uint256", "100")
      r.isInstanceOf[datatypes.generated.Uint256] shouldBe true
      r.getValue.toString should === ("100")
    }

    "convert bool" in {
      val r = Solidity.toWeb3Type("bool", "true")
      r.isInstanceOf[datatypes.Bool] shouldBe true
      r.getValue shouldBe true
    }

    "convert string" in {
      val r = Solidity.toWeb3Type("string", "hello")
      r.isInstanceOf[datatypes.Utf8String] shouldBe true
      r.getValue should === ("hello")
    }

    "convert bytes32" in {
      val r = Solidity.toWeb3Type("bytes32", "0x0000000000000000000000000000000000000000000000000000000000000054")
      r.isInstanceOf[datatypes.generated.Bytes32] shouldBe true
    }

    "convert simple array" in {
      val r = Solidity.toWeb3Type("address[]", "[0x123,0x456]")
      r.isInstanceOf[datatypes.DynamicArray[_]] shouldBe true
      val arr = r.getValue.asInstanceOf[java.util.List[datatypes.Address]]
      arr.size should === (2)
      arr.get(0).toString should === ("0x0000000000000000000000000000000000000123")
      arr.get(1).toString should === ("0x0000000000000000000000000000000000000456")
    }

    "convert simple tuple" in {
      val r = Solidity.toWeb3Type("(address,uint256)", "(0x123, 100)")
      r.isInstanceOf[datatypes.DynamicStruct] shouldBe true
      val struct = r.getValue.asInstanceOf[java.util.List[datatypes.Type[_]]]
      struct.size should === (2)
      struct.get(0).toString should === ("0x0000000000000000000000000000000000000123")
      struct.get(1).getValue.toString should === ("100")
    }

    "convert nested array of tuples" in {
      val r = Solidity.toWeb3Type("(address,uint256)[]", "[(0x123, 100),(0x456, 200)]")
      r.isInstanceOf[datatypes.DynamicArray[_]] shouldBe true
      val arr = r.getValue.asInstanceOf[java.util.List[datatypes.DynamicStruct]]
      arr.size should === (2)
      
      val firstStruct = arr.get(0).getValue.asInstanceOf[java.util.List[datatypes.Type[_]]]
      firstStruct.get(0).toString should === ("0x0000000000000000000000000000000000000123")
      firstStruct.get(1).getValue.toString should === ("100")
      
      val secondStruct = arr.get(1).getValue.asInstanceOf[java.util.List[datatypes.Type[_]]]
      secondStruct.get(0).toString should === ("0x0000000000000000000000000000000000000456")
      secondStruct.get(1).getValue.toString should === ("200")
    }

    "convert different uint sizes" in {
      val uint8 = Solidity.toWeb3Type("uint8", "100")
      uint8.isInstanceOf[datatypes.generated.Uint8] shouldBe true
      
      val uint32 = Solidity.toWeb3Type("uint32", "100")
      uint32.isInstanceOf[datatypes.generated.Uint32] shouldBe true
      
      val uint256 = Solidity.toWeb3Type("uint", "100") // default size
      uint256.isInstanceOf[datatypes.generated.Uint256] shouldBe true
    }

    "convert different int sizes" in {
      val int8 = Solidity.toWeb3Type("int8", "-100")
      int8.isInstanceOf[datatypes.generated.Int8] shouldBe true
      
      val int32 = Solidity.toWeb3Type("int32", "-100")
      int32.isInstanceOf[datatypes.generated.Int32] shouldBe true
      
      val int256 = Solidity.toWeb3Type("int", "-100") // default size
      int256.isInstanceOf[datatypes.generated.Int256] shouldBe true
    }

    "throw exception for unsupported type" in {
      val exception = intercept[Exception] {
        Solidity.toWeb3Type("unsupported", "value")
      }
      exception.getMessage should include("Unsupported type")
    }
  }

  "parseFunction" should {
    """parse "func()" - no parameters and no output""" in {
      val (name, inputs, output) = Solidity.parseFunction("func()")
      name should === ("func")
      inputs should === (Vector.empty)
      output should === ("")
    }

    """parse "func(  )" - no parameters and no output and spaces""" in {
      val (name, inputs, output) = Solidity.parseFunction("func(  )")
      name should === ("func")
      inputs should === (Vector.empty)
      output should === ("")
    }

    """parse "func" - no parameters and no output without ()""" in {
      val (name, inputs, output) = Solidity.parseFunction("func")
      name should === ("func")
      inputs should === (Vector.empty)
      output should === ("")
    }

    """parse "func()(uint256)" - no parameters with output""" in {
      val (name, inputs, output) = Solidity.parseFunction("func()(uint256)")
      name should === ("func")
      inputs should === (Vector.empty)
      output should === ("uint256")
    }

    """parse "func(  )(  uint256 )" - no parameters with output and spaces""" in {
      val (name, inputs, output) = Solidity.parseFunction("func(  )(  uint256 )")
      name should === ("func")
      inputs should === (Vector.empty)
      output should === ("uint256")
    }

    """parse "func(address)" - single parameter""" in {
      val (name, inputs, output) = Solidity.parseFunction("func(address)")
      name should === ("func")
      inputs should === (Vector("address"))
      output should === ("")
    }

    """parse "func(address)(uint256)" - single parameter with output""" in {
      val (name, inputs, output) = Solidity.parseFunction("func(address)(uint256)")
      name should === ("func")
      inputs should === (Vector("address"))
      output should === ("uint256")
    }

    """parse "func(address,uint256)" - multiple parameters""" in {
      val (name, inputs, output) = Solidity.parseFunction("func(address,uint256)")
      name should === ("func")
      inputs should === (Vector("address", "uint256"))
      output should === ("")
    }

    """parse "func(address,uint256)(uint256)" - multiple parameters with output""" in {
      val (name, inputs, output) = Solidity.parseFunction("func(address,uint256)(uint256)")
      name should === ("func")
      inputs should === (Vector("address", "uint256"))
      output should === ("uint256")
    }

    """parse "func(address[])" - array parameter""" in {
      val (name, inputs, output) = Solidity.parseFunction("func(address[])")
      name should === ("func")
      inputs should === (Vector("address[]"))
      output should === ("")
    }

    """parse "func((address,uint256))" - single tuple parameter""" in {
      val (name, inputs, output) = Solidity.parseFunction("func((address,uint256))")
      name should === ("func")
      inputs should === (Vector("(address,uint256)"))
      output should === ("")
    }

    """parse "func((address,uint256),(bool,string))" - multiple tuple parameters""" in {
      val (name, inputs, output) = Solidity.parseFunction("func((address,uint256),(bool,string))")
      name should === ("func")
      inputs should === (Vector("(address,uint256)", "(bool,string)"))
      output should === ("")
    }

    """parse "swap(uint256,(address,uint256)[],bool)(uint256)" - complex signature""" in {
      val (name, inputs, output) = Solidity.parseFunction("swap(uint256,(address,uint256)[],bool)(uint256)")
      name should === ("swap")
      inputs should === (Vector("uint256", "(address,uint256)[]", "bool"))
      output should === ("uint256")
    }

    // "throw exception for invalid function signature: invalid(type100)" in {
    //   val exception = intercept[Exception] {
    //     Solidity.parseFunction("invalid(type100)")
    //   }
    //   exception.getMessage should include("Failed to parse function")
    // }

    """parse "getReservesData(address)((address,string,uint256,bool)[],(uint256,int256,int256,uint8))"""" in {
      val (name, inputs, output) = Solidity.parseFunction("getReservesData(address)((address,string,uint256,bool)[],(uint256,int256,int256,uint8))")
      name should === ("getReservesData")
      inputs should === (Vector("address"))
      output should === ("(address,string,uint256,bool)[],(uint256,int256,int256,uint8)")
    }

  }

  "Solidity.encodeFunction" should {
    """encode "balanceOf(address)" - single address parameter""" in {
      val encoded = Solidity.encodeFunction("balanceOf(address)", Seq("0x742d35Cc6634C0532925a3b844Bc454e4438f44e"))
      encoded should === ("0x70a08231000000000000000000000000742d35cc6634c0532925a3b844bc454e4438f44e")
    }

    """encode "transfer(address,uint256)" - address and amount""" in {
      val encoded = Solidity.encodeFunction("transfer(address,uint256)", 
        Seq("0x742d35Cc6634C0532925a3b844Bc454e4438f44e", "1000000000000000000"))
      encoded should === ("0xa9059cbb000000000000000000000000742d35cc6634c0532925a3b844bc454e4438f44e0000000000000000000000000000000000000000000000000de0b6b3a7640000")
    }

    """encode "approve(address,uint256)" - basic approve""" in {
      val encoded = Solidity.encodeFunction("approve(address,uint256)", 
        Seq("0x742d35Cc6634C0532925a3b844Bc454e4438f44e", "1000000000000000000"))
      encoded should === ("0x095ea7b3000000000000000000000000742d35cc6634c0532925a3b844bc454e4438f44e0000000000000000000000000000000000000000000000000de0b6b3a7640000")
    }

    """encode "name()" - no parameters""" in {
      val encoded = Solidity.encodeFunction("name()", Seq.empty)
      encoded should === ("0x06fdde03")
    }

    """encode "symbol()" - no parameters""" in {
      val encoded = Solidity.encodeFunction("symbol()", Seq.empty)
      encoded should === ("0x95d89b41")
    }

    """encode "decimals()" - no parameters""" in {
      val encoded = Solidity.encodeFunction("decimals()", Seq.empty)
      encoded should === ("0x313ce567")
    }

    "throw exception when parameter count mismatch" in {
      val exception = intercept[Exception] {
        Solidity.encodeFunction("transfer(address,uint256)", Seq("0x742d35Cc6634C0532925a3b844Bc454e4438f44e"))
      }
      //exception.getMessage should include("out of bounds")
    }

    "throw exception for invalid address" in {
      val exception = intercept[Exception] {
        Solidity.encodeFunction("transfer(address,uint256)", Seq("not_an_address", "1000000000000000000"))
      }
      //exception.getMessage should include("Invalid address")
    }

    // --------------------------------------------------------------------------------------------
    """encode "balanceOf(address)(uint256)" - with uint output""" in {
      val encoded = Solidity.encodeFunction("balanceOf(address)(uint256)", 
        Seq("0x742d35Cc6634C0532925a3b844Bc454e4438f44e"))
      encoded should === ("0x70a08231000000000000000000000000742d35cc6634c0532925a3b844bc454e4438f44e")
    }

    """encode "getData(uint256)(string)" - with string output""" in {
      val encoded = Solidity.encodeFunction("getData(uint256)(string)", Seq("123"))
      encoded should === ("0x0178fe3f000000000000000000000000000000000000000000000000000000000000007b")
    }

    """encode "getPair(address,address)(address)" - common UniswapV2 function""" in {
      val encoded = Solidity.encodeFunction("getPair(address,address)(address)", 
        Seq(
          "0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2",  // WETH
          "0x6B175474E89094C44Da98b954EedeAC495271d0F"   // DAI
        ))
      encoded should === ("0xe6a43905000000000000000000000000c02aaa39b223fe8d0a0e5c4f27ead9083c756cc20000000000000000000000006b175474e89094c44da98b954eedeac495271d0f")
    }

    """encode "getAmountsOut(uint256,address[])(uint256[])" - complex types""" in {
      val encoded = Solidity.encodeFunction("getAmountsOut(uint256,address[])(uint256[])", 
        Seq(
          "1000000000000000000",
          "[0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2,0x6B175474E89094C44Da98b954EedeAC495271d0F]"
        ))
      encoded should === ("0xd06ca61f0000000000000000000000000000000000000000000000000de0b6b3a764000000000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000002000000000000000000000000c02aaa39b223fe8d0a0e5c4f27ead9083c756cc20000000000000000000000006b175474e89094c44da98b954eedeac495271d0f")
    }

    """encode "getReserves()(uint112,uint112,uint32)" - multiple outputs""" in {
      val encoded = Solidity.encodeFunction("getReserves()(uint112,uint112,uint32)", Seq.empty)
      encoded should === ("0x0902f1ac")
    }

    "throw exception for invalid output type" in {
      val exception = intercept[Exception] {
        Solidity.encodeFunction("getData(uint256)(invalid)", Seq("123"))
      }
      exception.getMessage should include("Unsupported type")
    }
  }

  "Solidity.toTypeReference" should {
    "convert array types" in {
      val types = List(
        "uint256[]" -> classOf[datatypes.DynamicArray[datatypes.generated.Uint256]],
        "address[]" -> classOf[datatypes.DynamicArray[datatypes.Address]],
        "bool[]" -> classOf[datatypes.DynamicArray[datatypes.Bool]],
        "string[]" -> classOf[datatypes.DynamicArray[datatypes.Utf8String]],
        "bytes32[]" -> classOf[datatypes.DynamicArray[datatypes.generated.Bytes32]],
        "int256[]" -> classOf[datatypes.DynamicArray[datatypes.generated.Int256]]
      )

      types.foreach { case (typeStr, expectedClass) =>
        val ref = Solidity.toTypeReference(typeStr)
        ref.getClassType should === (expectedClass)
      }
    }
  }

  "Solidity.encodeFunction with string params" should {
    "parse space-delimited basic parameters" in {
      val cases = List(
        ("transfer(address,uint256)", "0x123 100") -> 
          Seq("0x123", "100"),
        
        ("setData(string,bool)", "hello true") -> 
          Seq("hello", "true"),
        
        ("multiParam(uint256,address,bool)", "123 0x456 true") -> 
          Seq("123", "0x456", "true")
      )

      cases.foreach { case ((func, params), expected) =>
        withClue(s"Testing: $func with params: $params") {
          val encoded = Solidity.encodeFunction(func, params)
          // Verify by re-parsing the parameters
          val (_, inputTypes, _) = Solidity.parseFunction(func)
          inputTypes.size should === (expected.size)
        }
      }
    }

    "parse array parameters with spaces" in {
      val cases = List(
        // Array with spaces after commas
        ("setArray(uint256[])", "[0, 1, 2, 3]") -> 
          Seq("[0, 1, 2, 3]"),
        
        // Array with irregular spaces
        ("setArray(uint256[])", "[0,   1,2,   3]") -> 
          Seq("[0,   1,2,   3]"),
        
        // Multiple parameters with array
        ("setArrayWithAddress(uint256[],address)", "[0, 1, 2] 0x123") -> 
          Seq("[0, 1, 2]", "0x123")
      )

      cases.foreach { case ((func, params), expected) =>
        withClue(s"Testing: $func with params: $params") {
          val encoded = Solidity.encodeFunction(func, params)
          // Verify by re-parsing the parameters
          val (_, inputTypes, _) = Solidity.parseFunction(func)
          inputTypes.size should === (expected.size)
        }
      }
    }

    "parse tuple parameters with spaces" in {
      val cases = List(
        // Simple tuple
        ("setTuple((uint256,address))", "(100, 0x123)") -> 
          Seq("(100, 0x123)"),
        
        // Tuple with array
        ("setComplexTuple((uint256,address[]))", "(100, [0x123, 0x456])") -> 
          Seq("(100, [0x123, 0x456])"),
        
        // Multiple parameters with tuple
        // ("setTupleWithExtra((uint256,address),bool)", "(100, 0x123), true") -> 
        //   Seq("(100, 0x123), true")
      )

      cases.foreach { case ((func, params), expected) =>
        withClue(s"Testing: $func with params: $params") {
          val encoded = Solidity.encodeFunction(func, params)
          // Verify by re-parsing the parameters
          val (_, inputTypes, _) = Solidity.parseFunction(func)
          inputTypes.size should === (expected.size)
        }
      }
    }

    "handle empty parameters" in {
      val encoded = Solidity.encodeFunction("noParams()", "")
      val (_, inputTypes, _) = Solidity.parseFunction("noParams()")
      inputTypes should be (empty)
    }

    "handle complex nested structures" in {
      val cases = List(
        // Array of tuples
        ("setArrayOfTuples((uint256,address)[])", "[(100, 0x123), (200, 0x456)]") -> 
          Seq("[(100, 0x123), (200, 0x456)]"),
        
        // Tuple with nested array and tuple
        ("setNestedStruct( ( uint256,(address,bool)[] ) )", "(100, [(0x123, true), (0x456, false)])") -> 
          Seq("(100, [(0x123, true), (0x456, false)])")
      )

      cases.foreach { case ((func, params), expected) =>
        withClue(s"Testing: $func with params: $params") {
          val encoded = Solidity.encodeFunction(func, params)
          // Verify by re-parsing the parameters
          val (_, inputTypes, _) = Solidity.parseFunction(func)
          inputTypes.size should === (expected.size)
        }
      }
    }
  }

  "Solidity.decodeResult" should {
    "decode uint256 result" in {
      val hex = "0x000000000000000000000000000000000000000000000000000000000000007b" // 123 in hex
      val result = Solidity.decodeResult(hex, "uint256")
      result should === (Success("123"))
    }

    "decode address result" in {
      val hex = "0x000000000000000000000000742d35cc6634c0532925a3b844bc454e4438f44e"
      val result = Solidity.decodeResult(hex, "address")
      result should === (Success("0x742d35cc6634c0532925a3b844bc454e4438f44e"))
    }

    "decode bool result" in {
      val hex = "0x0000000000000000000000000000000000000000000000000000000000000001"
      val result = Solidity.decodeResult(hex, "bool")
      result should === (Success("true"))
    }

    "decode string result" in {
      val hex = "0x0000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000000568656c6c6f000000000000000000000000000000000000000000000000000000"
      val result = Solidity.decodeResult(hex, "string")
      result should === (Success("hello"))
    }

    "decode uint256[] result" in {
      val hex = "0x0000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000000300000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000300000000000000000000000000000000000000000000000000000000000000"
      val result = Solidity.decodeResult(hex, "uint256[]")
      result should === (Success("[1,2,3]"))
    }

    // // ATTENTION: Tuples are not supported because web3j is retarded
    // "decode (uint256,address) tuple result" in {
    //   val hex = "0x000000000000000000000000000000000000000000000000000000000000007b000000000000000000000000742d35cc6634c0532925a3b844bc454e4438f44e"
    //   val result = Solidity.decodeResult(hex, "(uint256,address)")
    //   result should === (Success("(123, 0x742d35cc6634c0532925a3b844bc454e4438f44e)"))
    // }

    "fail for invalid hex data" in {
      val result = Solidity.decodeResult("invalid_hex", "uint256")
      result shouldBe a[Failure[_]]
    }

    "fail for unsupported return type" in {
      val result = Solidity.decodeResult("0x00", "unsupported")
      result shouldBe a[Failure[_]]
    }
  }
}
