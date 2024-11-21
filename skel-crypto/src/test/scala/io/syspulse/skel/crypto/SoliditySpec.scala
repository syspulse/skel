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
      val r = Solidity.toWeb3Type("(address,uint256)", "(0x123 100)")
      r.isInstanceOf[datatypes.DynamicStruct] shouldBe true
      val struct = r.getValue.asInstanceOf[java.util.List[datatypes.Type[_]]]
      struct.size should === (2)
      struct.get(0).toString should === ("0x0000000000000000000000000000000000000123")
      struct.get(1).getValue.toString should === ("100")
    }

    "convert nested array of tuples" in {
      val r = Solidity.toWeb3Type("(address,uint256)[]", "[(0x123 100),(0x456 200)]")
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

    """parse "func()(uint256)" - no parameters with output""" in {
      val (name, inputs, output) = Solidity.parseFunction("func()(uint256)")
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

    """parse "func(address,uint256)" - multiple parameters""" in {
      val (name, inputs, output) = Solidity.parseFunction("func(address,uint256)")
      name should === ("func")
      inputs should === (Vector("address", "uint256"))
      output should === ("")
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

    "throw exception for invalid function signature" in {
      val exception = intercept[Exception] {
        Solidity.parseFunction("invalid signature")
      }
      exception.getMessage should include("Failed to parse function")
    }
  }

  "Solidity.encodeFunction" should {
    """encode "balanceOf(address)" - single address parameter""" in {
      val encoded = Solidity.encodeFunction("balanceOf(address)", Array("0x742d35Cc6634C0532925a3b844Bc454e4438f44e"))
      encoded should === ("0x70a08231000000000000000000000000742d35cc6634c0532925a3b844bc454e4438f44e")
    }

    """encode "transfer(address,uint256)" - address and amount""" in {
      val encoded = Solidity.encodeFunction("transfer(address,uint256)", 
        Array("0x742d35Cc6634C0532925a3b844Bc454e4438f44e", "1000000000000000000"))
      encoded should === ("0xa9059cbb000000000000000000000000742d35cc6634c0532925a3b844bc454e4438f44e0000000000000000000000000000000000000000000000000de0b6b3a7640000")
    }

    """encode "approve(address,uint256)" - basic approve""" in {
      val encoded = Solidity.encodeFunction("approve(address,uint256)", 
        Array("0x742d35Cc6634C0532925a3b844Bc454e4438f44e", "1000000000000000000"))
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
  }
}
