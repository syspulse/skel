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

class SolidityTupleSpec extends AnyWordSpec with Matchers with TestData {
  
  "Solidity Tuples" should {

    "decode simple (uint256,address) tuple result" in {
      // cast abi-encode "someFunc((uint256,address))" "(123,0x742d35cc6634c0532925a3b844bc454e4438f44e)"                  
      val hex = "0x000000000000000000000000000000000000000000000000000000000000007b000000000000000000000000742d35cc6634c0532925a3b844bc454e4438f44e"
      val result = SolidityTuple.decodeTupleResult(hex, "(uint256,address)")
      result should === ("(123,0x742d35cc6634c0532925a3b844bc454e4438f44e)")
    }

    "decode (uint256,string) tuple result" in {
      // cast abi-encode "someFunc((uint256,string))" "(123,\"Hello World\")"
      // 0x0000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000007b0000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000000b48656c6c6f20576f726c64000000000000000000000000000000000000000000
      //val hex = "0x0000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000007b0000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000000b48656c6c6f20576f726c64000000000000000000000000000000000000000000"
      val hex = "0000000000000000000000000000000000000000000000000000000000000020" + "000000000000000000000000000000000000000000000000000000000000007b0000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000000b48656c6c6f20576f726c64000000000000000000000000000000000000000000"
      val result = SolidityTuple.decodeTupleResult(hex, "(uint256,string)")
      result should === ("(123,\"Hello World\")")
    }

    "decode (uint256,string,(int,address)) recursive tuple result" in {
      val hex = "0x000000000000000000000000000000000000000000000000000000000000007b00000000000000000000000000000000000000000000000000000000000000600000000000000000000000000000000000000000000000000000000000000005000000000000000000000000742d35cc6634c0532925a3b844bc454e4438f44e000000000000000000000000000000000000000000000000000000000000000a48656c6c6f20576f726c6400000000000000000000000000000000000000000000"
      val result = SolidityTuple.decodeTupleResult(hex, "(uint256,string,(int,address))")
      result should === ("(123,\"Hello World\",(5,0x742d35cc6634c0532925a3b844bc454e4438f44e))")
    }

    "decode (bool,uint256,address) tuple result" in {
      val hex = "0x000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000000007b000000000000000000000000742d35cc6634c0532925a3b844bc454e4438f44e"
      val result = SolidityTuple.decodeTupleResult(hex, "(bool,uint256,address)")
      result should === ("(true,123,0x742d35cc6634c0532925a3b844bc454e4438f44e)")
    }

    "decode (uint256,(bool,string),address) nested tuple result" in {
      val hex = "0x000000000000000000000000000000000000000000000000000000000000007b00000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000000600000000000000000000000742d35cc6634c0532925a3b844bc454e4438f44e000000000000000000000000000000000000000000000000000000000000000a48656c6c6f20576f726c6400000000000000000000000000000000000000000000"
      val result = SolidityTuple.decodeTupleResult(hex, "(uint256,(bool,string),address)")
      result should === ("(123,(true,\"Hello World\"),0x742d35cc6634c0532925a3b844bc454e4438f44e)")
    }

    "decode (bytes32,uint256) tuple result" in {
      val hex = "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef000000000000000000000000000000000000000000000000000000000000007b"
      val result = SolidityTuple.decodeTupleResult(hex, "(bytes32,uint256)")
      result should === ("(0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef,123)")
    }

    "decode (uint256,bytes) tuple result" in {
      val hex = "0x000000000000000000000000000000000000000000000000000000000000007b0000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000000a48656c6c6f20576f726c6400000000000000000000000000000000000000000000"
      val result = SolidityTuple.decodeTupleResult(hex, "(uint256,bytes)")
      result should === ("(123,0x48656c6c6f20576f726c64)")
    }

    "decode complex nested tuple (uint256,(bool,(string,address)),bytes32)" in {
      val hex = "0x000000000000000000000000000000000000000000000000000000000000007b00000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000000600000000000000000000000742d35cc6634c0532925a3b844bc454e4438f44e1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef000000000000000000000000000000000000000000000000000000000000000a48656c6c6f20576f726c6400000000000000000000000000000000000000000000"
      val result = SolidityTuple.decodeTupleResult(hex, "(uint256,(bool,(string,address)),bytes32)")
      result should === ("(123,(true,(\"Hello World\",0x742d35cc6634c0532925a3b844bc454e4438f44e)),0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef)")
    }

    "handle empty hex data" in {
      val result = SolidityTuple.decodeTupleResult("", "(uint256,address)")
      result should === ("")
    }

    "handle 0x hex data" in {
      val result = SolidityTuple.decodeTupleResult("0x", "(uint256,address)")
      result should === ("")
    }

    "use enhanced decodeResult method" in {
      val hex = "0x000000000000000000000000000000000000000000000000000000000000007b000000000000000000000000742d35cc6634c0532925a3b844bc454e4438f44e"
      val result = SolidityTuple.decodeTupleResult(hex, "(uint256,address)")
      result should === ("(123,0x742d35cc6634c0532925a3b844bc454e4438f44e)")
    }

    "use enhanced decodeData method" in {
      val hex = "0x000000000000000000000000000000000000000000000000000000000000007b000000000000000000000000742d35cc6634c0532925a3b844bc454e4438f44e"
      val result = SolidityTuple.decodeDataEnhanced("(uint256,address)", hex)
      result should === ("(123,0x742d35cc6634c0532925a3b844bc454e4438f44e)")
    }

    "fall back to original method for non-tuple types" in {
      val hex = "0x000000000000000000000000000000000000000000000000000000000000007b"
      val result = SolidityTuple.decodeDataEnhanced("uint256", hex)
      result should === ("123")
    }

    // // ==========================================================================================================
    // // Primitive Type Tests
    // // ==========================================================================================================

    "decode uint256 primitive type" in {
      val hex = "0x000000000000000000000000000000000000000000000000000000000000007b"
      val result = SolidityTuple.decodeTupleResult(hex, "uint256")
      result should === ("123")
    }

    "decode int256 primitive type" in {
      val hex = "0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff85"
      val result = SolidityTuple.decodeTupleResult(hex, "int256")
      result should === ("-123")
    }

    "decode address primitive type" in {
      val hex = "0x000000000000000000000000742d35cc6634c0532925a3b844bc454e4438f44e"
      val result = SolidityTuple.decodeTupleResult(hex, "address")
      result should === ("0x742d35cc6634c0532925a3b844bc454e4438f44e")
    }

    "decode bool primitive type" in {
      val hex = "0x0000000000000000000000000000000000000000000000000000000000000001"
      val result = SolidityTuple.decodeTupleResult(hex, "bool")
      result should === ("true")
    }

    "decode bool false primitive type" in {
      val hex = "0x0000000000000000000000000000000000000000000000000000000000000000"
      val result = SolidityTuple.decodeTupleResult(hex, "bool")
      result should === ("false")
    }

    "decode bytes32 primitive type" in {
      val hex = "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"
      val result = SolidityTuple.decodeTupleResult(hex, "bytes32")
      result should === ("0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef")
    }

    "decode string primitive type" in {
      val hex = "0x0000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000000b48656c6c6f20576f726c64000000000000000000000000000000000000000000"
      val result = SolidityTuple.decodeTupleResult(hex, "string")
      result should === ("\"Hello World\"")
    }

    "decode bytes primitive type" in {
      val hex = "0x0000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000000b48656c6c6f20576f726c64000000000000000000000000000000000000000000"
      val result = SolidityTuple.decodeTupleResult(hex, "bytes")
      result should === ("0x48656c6c6f20576f726c64")
    }

// =========================================================================================================================================
    // ==========================================================================================================
    // decodePrimitiveTypeABI Tests - Comprehensive ABI Primitive Type Decoding
    // ==========================================================================================================

    "decodePrimitiveTypeABI - uint256" in {
      val hex = "000000000000000000000000000000000000000000000000000000000000007b"
      val result = SolidityTuple.decodeTupleResult("0x" + hex, "uint256")
      result should === ("123")
    }

    "decodePrimitiveTypeABI - uint128" in {
      val hex = "000000000000000000000000000000000000000000000000000000000000007b"
      val result = SolidityTuple.decodeTupleResult("0x" + hex, "uint128")
      result should === ("123")
    }

    "decodePrimitiveTypeABI - uint64" in {
      val hex = "000000000000000000000000000000000000000000000000000000000000007b"
      val result = SolidityTuple.decodeTupleResult("0x" + hex, "uint64")
      result should === ("123")
    }

    "decodePrimitiveTypeABI - uint32" in {
      val hex = "000000000000000000000000000000000000000000000000000000000000007b"
      val result = SolidityTuple.decodeTupleResult("0x" + hex, "uint32")
      result should === ("123")
    }

    "decodePrimitiveTypeABI - uint8" in {
      val hex = "000000000000000000000000000000000000000000000000000000000000007b"
      val result = SolidityTuple.decodeTupleResult("0x" + hex, "uint8")
      result should === ("123")
    }

    "decodePrimitiveTypeABI - int256 positive" in {
      val hex = "000000000000000000000000000000000000000000000000000000000000007b"
      val result = SolidityTuple.decodeTupleResult("0x" + hex, "int256")
      result should === ("123")
    }

    "decodePrimitiveTypeABI - int256 negative" in {
      val hex = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff85"
      val result = SolidityTuple.decodeTupleResult("0x" + hex, "int256")
      result should === ("-123")
    }

    "decodePrimitiveTypeABI - int128" in {
      val hex = "000000000000000000000000000000000000000000000000000000000000007b"
      val result = SolidityTuple.decodeTupleResult("0x" + hex, "int128")
      result should === ("123")
    }

    "decodePrimitiveTypeABI - int64" in {
      val hex = "000000000000000000000000000000000000000000000000000000000000007b"
      val result = SolidityTuple.decodeTupleResult("0x" + hex, "int64")
      result should === ("123")
    }

    "decodePrimitiveTypeABI - int32" in {
      val hex = "000000000000000000000000000000000000000000000000000000000000007b"
      val result = SolidityTuple.decodeTupleResult("0x" + hex, "int32")
      result should === ("123")
    }

    "decodePrimitiveTypeABI - int8" in {
      val hex = "000000000000000000000000000000000000000000000000000000000000007b"
      val result = SolidityTuple.decodeTupleResult("0x" + hex, "int8")
      result should === ("123")
    }

    "decodePrimitiveTypeABI - address" in {
      val hex = "000000000000000000000000742d35cc6634c0532925a3b844bc454e4438f44e"
      val result = SolidityTuple.decodeTupleResult("0x" + hex, "address")
      result should === ("0x742d35cc6634c0532925a3b844bc454e4438f44e")
    }

    "decodePrimitiveTypeABI - bool true" in {
      val hex = "0000000000000000000000000000000000000000000000000000000000000001"
      val result = SolidityTuple.decodeTupleResult("0x" + hex, "bool")
      result should === ("true")
    }

    "decodePrimitiveTypeABI - bool false" in {
      val hex = "0000000000000000000000000000000000000000000000000000000000000000"
      val result = SolidityTuple.decodeTupleResult("0x" + hex, "bool")
      result should === ("false")
    }

    "decodePrimitiveTypeABI - boolean true" in {
      val hex = "0000000000000000000000000000000000000000000000000000000000000001"
      val result = SolidityTuple.decodeTupleResult("0x" + hex, "boolean")
      result should === ("true")
    }

    "decodePrimitiveTypeABI - bytes32" in {
      val hex = "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"
      val result = SolidityTuple.decodeTupleResult("0x" + hex, "bytes32")
      result should === ("0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef")
    }

    "decodePrimitiveTypeABI - string with offset" in {
      // ABI format: [offset][length][string data]
      val hex = "0000000000000000000000000000000000000000000000000000000000000020" + // offset = 32
                "000000000000000000000000000000000000000000000000000000000000000b" + // length = 11
                "48656c6c6f20576f726c64000000000000000000000000000000000000000000" // "Hello World"
      val result = SolidityTuple.decodeTupleResult("0x" + hex, "string")
      result should === ("\"Hello World\"")
    }

    "decodePrimitiveTypeABI - string empty" in {
      val hex = "0000000000000000000000000000000000000000000000000000000000000020" + // offset = 32
                "0000000000000000000000000000000000000000000000000000000000000000"   // length = 0
      val result = SolidityTuple.decodeTupleResult("0x" + hex, "string")
      result should === ("\"\"")
    }

    "decodePrimitiveTypeABI - bytes with offset" in {
      // ABI format: [offset][length][bytes data]
      val hex = "0000000000000000000000000000000000000000000000000000000000000020" + // offset = 32
                "000000000000000000000000000000000000000000000000000000000000000b" + // length = 11
                "48656c6c6f20576f726c64000000000000000000000000000000000000000000" // "Hello World"
      val result = SolidityTuple.decodeTupleResult("0x" + hex, "bytes")
      result should === ("0x48656c6c6f20576f726c64")
    }

    "decodePrimitiveTypeABI - bytes empty" in {
      val hex = "0000000000000000000000000000000000000000000000000000000000000020" + // offset = 32
                "0000000000000000000000000000000000000000000000000000000000000000"   // length = 0
      val result = SolidityTuple.decodeTupleResult("0x" + hex, "bytes")
      result should === ("0x")
    }

    "decodePrimitiveTypeABI - large uint256" in {
      val hex = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
      val result = SolidityTuple.decodeTupleResult("0x" + hex, "uint256")
      result should === ("115792089237316195423570985008687907853269984665640564039457584007913129639935")
    }

    "decodePrimitiveTypeABI - large int256" in {
      val hex = "8000000000000000000000000000000000000000000000000000000000000000"
      val result = SolidityTuple.decodeTupleResult("0x" + hex, "int256")
      result should === ("-57896044618658097711785492504343953926634992332820282019728792003956564819968")
    }

    "decodePrimitiveTypeABI - zero address" in {
      val hex = "0000000000000000000000000000000000000000000000000000000000000000"
      val result = SolidityTuple.decodeTupleResult("0x" + hex, "address")
      result should === ("0x0000000000000000000000000000000000000000")
    }

    "decodePrimitiveTypeABI - string with special characters" in {
      val hex = "0000000000000000000000000000000000000000000000000000000000000020" + // offset = 32
                "000000000000000000000000000000000000000000000000000000000000000e" + // length = 23
                "48656c6c6f20576f726c64212121000000000000000000000000000000000000" // "Hello World!!!"
      val result = SolidityTuple.decodeTupleResult("0x" + hex, "string")
      result should === ("\"Hello World!!!\"")
    }

    "decodePrimitiveTypeABI - bytes with non-printable characters" in {
      val hex = "0000000000000000000000000000000000000000000000000000000000000020" + // offset = 32
                "0000000000000000000000000000000000000000000000000000000000000004" + // length = 4
                "deadbeef0000000000000000000000000000000000000000000000000000000000" // 0xdeadbeef
      val result = SolidityTuple.decodeTupleResult("0x" + hex, "bytes")
      result should === ("0xdeadbeef")
    }

    // ==========================================================================================================
    // Array Type Tests - Static and Dynamic Arrays
    // ==========================================================================================================

    "decode uint256[] dynamic array" in {
      val hex = "0000000000000000000000000000000000000000000000000000000000000020" + // offset to array
                "0000000000000000000000000000000000000000000000000000000000000002" + // length = 2
                "000000000000000000000000000000000000000000000000000000000000000a" + // 10
                "0000000000000000000000000000000000000000000000000000000000000014"   // 20
      // val hex = "00000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000a0000000000000000000000000000000000000000000000000000000000000014"                
      val result = SolidityTuple.decodeTupleResult("0x" + hex, "uint256[]")
      result should === ("[10,20]")
    }

    "decode address[2] static array" in {
      val hex = "000000000000000000000000742d35cc6634c0532925a3b844bc454e4438f44e" +
                "000000000000000000000000742d35cc6634c0532925a3b844bc454e4438f44e"
      val result = SolidityTuple.decodeTupleResult("0x" + hex, "address[2]")
      result should === ("[0x742d35cc6634c0532925a3b844bc454e4438f44e,0x742d35cc6634c0532925a3b844bc454e4438f44e]")
    }

    "decode bool[3] static array" in {
      val hex = "0000000000000000000000000000000000000000000000000000000000000001" +
                "0000000000000000000000000000000000000000000000000000000000000000" +
                "0000000000000000000000000000000000000000000000000000000000000001"
      val result = SolidityTuple.decodeTupleResult("0x" + hex, "bool[3]")
      result should === ("[true,false,true]")
    }

    "decode bytes32[2] static array" in {
      val hex = "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef" +
                "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890"
      val result = SolidityTuple.decodeTupleResult("0x" + hex, "bytes32[2]")
      result should === ("[0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef,0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890]")
    }

    "decode string[] dynamic array" in {
      val hex = "0000000000000000000000000000000000000000000000000000000000000020" + // offset to array
                "0000000000000000000000000000000000000000000000000000000000000002" + // length = 2
                "0000000000000000000000000000000000000000000000000000000000000040" + // offset to first string
                "0000000000000000000000000000000000000000000000000000000000000080" + // offset to second string
                "0000000000000000000000000000000000000000000000000000000000000005" + // length = 5
                "48656c6c6f000000000000000000000000000000000000000000000000000000" + // "Hello"
                "0000000000000000000000000000000000000000000000000000000000000005" + // length = 5
                "576f726c64000000000000000000000000000000000000000000000000000000"   // "World"
      // val hex =    "0000000000000000000000000000000000000000000000000000000000000020" + 
      //              "0000000000000000000000000000000000000000000000000000000000000002" + 
      //              "0000000000000000000000000000000000000000000000000000000000000040" + 
      //              "0000000000000000000000000000000000000000000000000000000000000080" + 
      //              "0000000000000000000000000000000000000000000000000000000000000005" + 
      //              "48656c6c6f000000000000000000000000000000000000000000000000000000" + 
      //              "0000000000000000000000000000000000000000000000000000000000000005" + 
      //              "576f726c64000000000000000000000000000000000000000000000000000000"
      val result = SolidityTuple.decodeTupleResult("0x" + hex, "string[]")
      result should === ("[\"Hello\",\"World\"]")
    }

    "decode bytes[] dynamic array" in {
      val hex = "0000000000000000000000000000000000000000000000000000000000000020" + // offset to array
                "0000000000000000000000000000000000000000000000000000000000000002" + // length = 2
                "0000000000000000000000000000000000000000000000000000000000000040" + // offset to first bytes
                "0000000000000000000000000000000000000000000000000000000000000080" + // offset to second bytes
                "0000000000000000000000000000000000000000000000000000000000000004" + // length = 4
                "deadbeef00000000000000000000000000000000000000000000000000000000" + // 0xdeadbeef
                "0000000000000000000000000000000000000000000000000000000000000004" + // length = 4
                "cafebabe00000000000000000000000000000000000000000000000000000000"   // 0xcafebabe
      val result = SolidityTuple.decodeTupleResult("0x" + hex, "bytes[]")
      result should === ("[0xdeadbeef,0xcafebabe]")
    }

    "decode empty dynamic array" in {
      val hex = "0000000000000000000000000000000000000000000000000000000000000020" + // offset to array
                "0000000000000000000000000000000000000000000000000000000000000000"   // length = 0
      val result = SolidityTuple.decodeTupleResult("0x" + hex, "uint256[]")
      result should === ("[]")
    }

    // ==========================================================================================================
    // Arrays in Tuples Tests
    // ==========================================================================================================

    // "decode (uint256[],address) tuple" in {
    //   val hex = "0000000000000000000000000000000000000000000000000000000000000040" + // offset to array
    //             "000000000000000000000000742d35cc6634c0532925a3b844bc454e4438f44e" + // address
    //             "0000000000000000000000000000000000000000000000000000000000000002" + // length = 2
    //             "000000000000000000000000000000000000000000000000000000000000000a" + // 10
    //             "0000000000000000000000000000000000000000000000000000000000000014"   // 20
    //   val result = SolidityTuple.decodeTupleResult("0x" + hex, "(uint256[],address)")
    //   result should === ("([10,20],0x742d35cc6634c0532925a3b844bc454e4438f44e)")
    // }

    // "decode (uint256,address[]) tuple" in {
    //   val hex = "000000000000000000000000000000000000000000000000000000000000007b" + // uint256 = 123
    //             "0000000000000000000000000000000000000000000000000000000000000040" + // offset to array
    //             "0000000000000000000000000000000000000000000000000000000000000002" + // length = 2
    //             "000000000000000000000000742d35cc6634c0532925a3b844bc454e4438f44e" + // address 1
    //             "000000000000000000000000742d35cc6634c0532925a3b844bc454e4438f44e"   // address 2
    //   val result = SolidityTuple.decodeTupleResult("0x" + hex, "(uint256,address[])")
    //   result should === ("(123,[0x742d35cc6634c0532925a3b844bc454e4438f44e,0x742d35cc6634c0532925a3b844bc454e4438f44e])")
    // }

    // "decode (uint256,(bool,string),address[]) complex nested tuple" in {
    //   val hex = "000000000000000000000000000000000000000000000000000000000000007b" + // uint256 = 123
    //             "0000000000000000000000000000000000000000000000000000000000000001" + // bool = true
    //             "0000000000000000000000000000000000000000000000000000000000000060" + // offset to string
    //             "0000000000000000000000000000000000000000000000000000000000000080" + // offset to address array
    //             "000000000000000000000000000000000000000000000000000000000000000a" + // string length = 10
    //             "48656c6c6f20576f726c6400000000000000000000000000000000000000000000" + // "Hello World"
    //             "0000000000000000000000000000000000000000000000000000000000000002" + // array length = 2
    //             "000000000000000000000000742d35cc6634c0532925a3b844bc454e4438f44e" + // address 1
    //             "000000000000000000000000742d35cc6634c0532925a3b844bc454e4438f44e"   // address 2
    //   val result = SolidityTuple.decodeTupleResult("0x" + hex, "(uint256,(bool,string),address[])")
    //   result should === ("(123,(true,\"Hello World\"),[0x742d35cc6634c0532925a3b844bc454e4438f44e,0x742d35cc6634c0532925a3b844bc454e4438f44e])")
    // }

    // "decode (uint256,address)[] array of tuples" in {
    //   val hex = "0000000000000000000000000000000000000000000000000000000000000020" + // offset to array
    //             "0000000000000000000000000000000000000000000000000000000000000002" + // length = 2
    //             "000000000000000000000000000000000000000000000000000000000000000a" + // 10
    //             "000000000000000000000000742d35cc6634c0532925a3b844bc454e4438f44e" + // address 1
    //             "0000000000000000000000000000000000000000000000000000000000000014" + // 20
    //             "000000000000000000000000742d35cc6634c0532925a3b844bc454e4438f44e"   // address 2
    //   val result = SolidityTuple.decodeTupleResult("0x" + hex, "(uint256,address)[]")
    //   result should === ("[(10,0x742d35cc6634c0532925a3b844bc454e4438f44e),(20,0x742d35cc6634c0532925a3b844bc454e4438f44e)]")
    // }

    // "decode (bool,string)[] array of tuples" in {
    //   val hex = "0000000000000000000000000000000000000000000000000000000000000020" + // offset to array
    //             "0000000000000000000000000000000000000000000000000000000000000002" + // length = 2
    //             "0000000000000000000000000000000000000000000000000000000000000001" + // bool = true
    //             "0000000000000000000000000000000000000000000000000000000000000040" + // offset to first string
    //             "0000000000000000000000000000000000000000000000000000000000000000" + // bool = false
    //             "0000000000000000000000000000000000000000000000000000000000000080" + // offset to second string
    //             "0000000000000000000000000000000000000000000000000000000000000005" + // length = 5
    //             "48656c6c6f00000000000000000000000000000000000000000000000000000000" + // "Hello"
    //             "0000000000000000000000000000000000000000000000000000000000000005" + // length = 5
    //             "576f726c6400000000000000000000000000000000000000000000000000000000"   // "World"
    //   val result = SolidityTuple.decodeTupleResult("0x" + hex, "(bool,string)[]")
    //   result should === ("[(true,\"Hello\"),(false,\"World\")]")
    // }    
  }
}
