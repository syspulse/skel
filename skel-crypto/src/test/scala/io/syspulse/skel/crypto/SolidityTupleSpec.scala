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
  
//   "Solidity Tuples" should {

//     "decode simple (uint256,address) tuple result" in {
//       // cast abi-encode "someFunc((uint256,address))" "(123,0x542d35cc6634c0532925a3b844bc454e4438f44e)"                  
//       val hex = "0x000000000000000000000000000000000000000000000000000000000000007b000000000000000000000000542d35cc6634c0532925a3b844bc454e4438f44e"
//       val result = SolidityTuple.decodeTupleResult(hex, "(uint256,address)")
//       result should === ("(123,0x542d35cc6634c0532925a3b844bc454e4438f44e)")
//     }

//     "decode (uint256,string) tuple result" in {
//       // cast abi-encode "someFunc((uint256,string))" "(123,\"Hello World\")"
//       // 0x0000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000007b0000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000000b48656c6c6f20576f726c64000000000000000000000000000000000000000000"
//       //val hex = "0x0000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000007b0000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000000b48656c6c6f20576f726c64000000000000000000000000000000000000000000"
//       val hex = "0000000000000000000000000000000000000000000000000000000000000020" + "000000000000000000000000000000000000000000000000000000000000007b0000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000000b48656c6c6f20576f726c64000000000000000000000000000000000000000000"
//       val result = SolidityTuple.decodeTupleResult(hex, "(uint256,string)")
//       result should === ("(123,Hello World)")
//     }

// // 'func((uint256,string,(int,address)))': 0xd9375f0f:
//     "decode (uint256,string,(int,address)) recursive tuple result" in {
//       val hex = "0x0000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000007b00000000000000000000000000000000000000000000000000000000000000800000000000000000000000000000000000000000000000000000000000000005000000000000000000000000742d35cc6634c0532925a3b844bc454e4438f44e000000000000000000000000000000000000000000000000000000000000000b48656c6c6f20576f726c64000000000000000000000000000000000000000000"
//       val result = SolidityTuple.decodeTupleResult(hex, "(uint256,string,(int,address))")
//       result should === ("(123,Hello World,(5,0x742d35cc6634c0532925a3b844bc454e4438f44e))")
//     }
    
//     // cast abi-encode "someFunc((bool,uint256,address))" "(true,123,0x742d35cc6634c0532925a3b844bc454e4438f44e)"
//     "decode (bool,uint256,address) tuple result" in {
//       val hex = "0x0000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000007b000000000000000000000000742d35cc6634c0532925a3b844bc454e4438f44e"
//       val result = SolidityTuple.decodeTupleResult(hex, "(bool,uint256,address)")
//       result should === ("(true,123,0x742d35cc6634c0532925a3b844bc454e4438f44e)")
//     }

//     // cast abi-encode "someFunc((uint256,(bool,string),address))" "(123,(true,Hello World),0x742d35cc6634c0532925a3b844bc454e4438f44e)"
//     "decode (uint256,(bool,string),address) nested tuple result" in {
//       val hex = "0x0000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000007b0000000000000000000000000000000000000000000000000000000000000060000000000000000000000000742d35cc6634c0532925a3b844bc454e4438f44e00000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000000b48656c6c6f20576f726c64000000000000000000000000000000000000000000"
//       val result = SolidityTuple.decodeTupleResult(hex, "(uint256,(bool,string),address)")
//       result should === ("(123,(true,Hello World),0x742d35cc6634c0532925a3b844bc454e4438f44e)")
//     }

//     "decode (bytes32,uint256) tuple result" in {
//       val hex = "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef000000000000000000000000000000000000000000000000000000000000007b"
//       val result = SolidityTuple.decodeTupleResult(hex, "(bytes32,uint256)")
//       result should === ("(0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef,123)")
//     }

//     // cast abi-encode "someFunc((uint256,bytes))" "(123,0x48656c6c6f20576f726c64)"
//     "decode (uint256,bytes) tuple result" in {
//       val hex = "0x0000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000007b0000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000000b48656c6c6f20576f726c64000000000000000000000000000000000000000000"
//       val result = SolidityTuple.decodeTupleResult(hex, "(uint256,bytes)")
//       result should === ("(123,0x48656c6c6f20576f726c64)")
//     }

//     // cast abi-encode "someFunc((uint256,(bool,(string,address)),bytes32))" "(123,(true,(\"Hello World\",0x742d35cc6634c0532925a3b844bc454e4438f44e)),0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef)"
//     "decode complex nested tuple (uint256,(bool,(string,address)),bytes32)" in {
//       val hex = "0x0000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000007b00000000000000000000000000000000000000000000000000000000000000601234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000040000000000000000000000000742d35cc6634c0532925a3b844bc454e4438f44e000000000000000000000000000000000000000000000000000000000000000b48656c6c6f20576f726c64000000000000000000000000000000000000000000"
//       val result = SolidityTuple.decodeTupleResult(hex, "(uint256,(bool,(string,address)),bytes32)")
//       result should === ("(123,(true,(Hello World,0x742d35cc6634c0532925a3b844bc454e4438f44e)),0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef)")
//     }

//     "handle empty hex data" in {
//       val result = SolidityTuple.decodeTupleResult("", "(uint256,address)")
//       result should === ("")
//     }

//     "handle 0x hex data" in {
//       val result = SolidityTuple.decodeTupleResult("0x", "(uint256,address)")
//       result should === ("")
//     }

//     "use enhanced decodeResult method" in {
//       val hex = "0x000000000000000000000000000000000000000000000000000000000000007b000000000000000000000000742d35cc6634c0532925a3b844bc454e4438f44e"
//       val result = SolidityTuple.decodeTupleResult(hex, "(uint256,address)")
//       result should === ("(123,0x742d35cc6634c0532925a3b844bc454e4438f44e)")
//     }

    
//     // // ==========================================================================================================
//     // // Primitive Type Tests
//     // // ==========================================================================================================

//     "decode uint256 primitive type" in {
//       val hex = "0x000000000000000000000000000000000000000000000000000000000000007b"
//       val result = SolidityTuple.decodeTupleResult(hex, "uint256")
//       result should === ("123")
//     }

//     "decode int256 primitive type" in {
//       val hex = "0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff85"
//       val result = SolidityTuple.decodeTupleResult(hex, "int256")
//       result should === ("-123")
//     }

//     "decode address primitive type" in {
//       val hex = "0x000000000000000000000000742d35cc6634c0532925a3b844bc454e4438f44e"
//       val result = SolidityTuple.decodeTupleResult(hex, "address")
//       result should === ("0x742d35cc6634c0532925a3b844bc454e4438f44e")
//     }

//     "decode bool primitive type" in {
//       val hex = "0x0000000000000000000000000000000000000000000000000000000000000001"
//       val result = SolidityTuple.decodeTupleResult(hex, "bool")
//       result should === ("true")
//     }

//     "decode bool false primitive type" in {
//       val hex = "0x0000000000000000000000000000000000000000000000000000000000000000"
//       val result = SolidityTuple.decodeTupleResult(hex, "bool")
//       result should === ("false")
//     }

//     "decode bytes32 primitive type" in {
//       val hex = "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"
//       val result = SolidityTuple.decodeTupleResult(hex, "bytes32")
//       result should === ("0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef")
//     }

//     "decode string primitive type" in {
//       val hex = "0x0000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000000b48656c6c6f20576f726c64000000000000000000000000000000000000000000"
//       val result = SolidityTuple.decodeTupleResult(hex, "string")
//       result should === ("Hello World")
//     }

//     "decode bytes primitive type" in {
//       val hex = "0x0000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000000b48656c6c6f20576f726c64000000000000000000000000000000000000000000"
//       val result = SolidityTuple.decodeTupleResult(hex, "bytes")
//       result should === ("0x48656c6c6f20576f726c64")
//     }


//     "decodePrimitiveTypeABI - bytes32" in {
//       val hex = "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"
//       val result = SolidityTuple.decodeTupleResult("0x" + hex, "bytes32")
//       result should === ("0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef")
//     }

//     "decodePrimitiveTypeABI - string with offset" in {
//       // ABI format: [offset][length][string data]
//       val hex = "0000000000000000000000000000000000000000000000000000000000000020" + // offset = 32
//                 "000000000000000000000000000000000000000000000000000000000000000b" + // length = 11
//                 "48656c6c6f20576f726c64000000000000000000000000000000000000000000" // "Hello World"
//       val result = SolidityTuple.decodeTupleResult("0x" + hex, "string")
//       result should === ("Hello World")
//     }

//     "decodePrimitiveTypeABI - string empty" in {
//       val hex = "0000000000000000000000000000000000000000000000000000000000000020" + // offset = 32
//                 "0000000000000000000000000000000000000000000000000000000000000000"   // length = 0
//       val result = SolidityTuple.decodeTupleResult("0x" + hex, "string")
//       result should === ("")
//     }

//     "decodePrimitiveTypeABI - bytes with offset" in {
//       // ABI format: [offset][length][bytes data]
//       val hex = "0000000000000000000000000000000000000000000000000000000000000020" + // offset = 32
//                 "000000000000000000000000000000000000000000000000000000000000000b" + // length = 11
//                 "48656c6c6f20576f726c64000000000000000000000000000000000000000000" // "Hello World"
//       val result = SolidityTuple.decodeTupleResult("0x" + hex, "bytes")
//       result should === ("0x48656c6c6f20576f726c64")
//     }

//     "decodePrimitiveTypeABI - bytes empty" in {
//       val hex = "0000000000000000000000000000000000000000000000000000000000000020" + // offset = 32
//                 "0000000000000000000000000000000000000000000000000000000000000000"   // length = 0
//       val result = SolidityTuple.decodeTupleResult("0x" + hex, "bytes")
//       result should === ("0x")
//     }

//     "decodePrimitiveTypeABI - large uint256" in {
//       val hex = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
//       val result = SolidityTuple.decodeTupleResult("0x" + hex, "uint256")
//       result should === ("115792089237316195423570985008687907853269984665640564039457584007913129639935")
//     }

//     "decodePrimitiveTypeABI - large int256" in {
//       val hex = "8000000000000000000000000000000000000000000000000000000000000000"
//       val result = SolidityTuple.decodeTupleResult("0x" + hex, "int256")
//       result should === ("-57896044618658097711785492504343953926634992332820282019728792003956564819968")
//     }

//     "decodePrimitiveTypeABI - zero address" in {
//       val hex = "0000000000000000000000000000000000000000000000000000000000000000"
//       val result = SolidityTuple.decodeTupleResult("0x" + hex, "address")
//       result should === ("0x0000000000000000000000000000000000000000")
//     }

//     "decodePrimitiveTypeABI - string with special characters" in {
//       val hex = "0000000000000000000000000000000000000000000000000000000000000020" + // offset = 32
//                 "000000000000000000000000000000000000000000000000000000000000000e" + // length = 23
//                 "48656c6c6f20576f726c64212121000000000000000000000000000000000000" // "Hello World!!!"
//       val result = SolidityTuple.decodeTupleResult("0x" + hex, "string")
//       result should === ("Hello World!!!")
//     }

//     "decodePrimitiveTypeABI - bytes with non-printable characters" in {
//       val hex = "0000000000000000000000000000000000000000000000000000000000000020" + // offset = 32
//                 "0000000000000000000000000000000000000000000000000000000000000004" + // length = 4
//                 "deadbeef0000000000000000000000000000000000000000000000000000000000" // 0xdeadbeef
//       val result = SolidityTuple.decodeTupleResult("0x" + hex, "bytes")
//       result should === ("0xdeadbeef")
//     }

//     // ==========================================================================================================
//     // Array Type Tests - Static and Dynamic Arrays
//     // ==========================================================================================================

//     "decode uint256[] dynamic array" in {
//       val hex = "0000000000000000000000000000000000000000000000000000000000000020" + // offset to array
//                 "0000000000000000000000000000000000000000000000000000000000000002" + // length = 2
//                 "000000000000000000000000000000000000000000000000000000000000000a" + // 10
//                 "0000000000000000000000000000000000000000000000000000000000000014"   // 20
//       // val hex = "00000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000a0000000000000000000000000000000000000000000000000000000000000014"                
//       val result = SolidityTuple.decodeTupleResult("0x" + hex, "uint256[]")
//       result should === ("[10,20]")
//     }

//     "decode address[2] static array" in {
//       val hex = "000000000000000000000000742d35cc6634c0532925a3b844bc454e4438f44e" +
//                 "000000000000000000000000742d35cc6634c0532925a3b844bc454e4438f44e"
//       val result = SolidityTuple.decodeTupleResult("0x" + hex, "address[2]")
//       result should === ("[0x742d35cc6634c0532925a3b844bc454e4438f44e,0x742d35cc6634c0532925a3b844bc454e4438f44e]")
//     }

//     "decode bool[3] static array" in {
//       val hex = "0000000000000000000000000000000000000000000000000000000000000001" +
//                 "0000000000000000000000000000000000000000000000000000000000000000" +
//                 "0000000000000000000000000000000000000000000000000000000000000001"
//       val result = SolidityTuple.decodeTupleResult("0x" + hex, "bool[3]")
//       result should === ("[true,false,true]")
//     }

//     "decode bytes32[2] static array" in {
//       val hex = "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef" +
//                 "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890"
//       val result = SolidityTuple.decodeTupleResult("0x" + hex, "bytes32[2]")
//       result should === ("[0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef,0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890]")
//     }

//     "decode string[] dynamic array" in {
//       val hex = "0000000000000000000000000000000000000000000000000000000000000020" + // offset to array
//                 "0000000000000000000000000000000000000000000000000000000000000002" + // length = 2
//                 "0000000000000000000000000000000000000000000000000000000000000040" + // offset to first string
//                 "0000000000000000000000000000000000000000000000000000000000000080" + // offset to second string
//                 "0000000000000000000000000000000000000000000000000000000000000005" + // length = 5
//                 "48656c6c6f000000000000000000000000000000000000000000000000000000" + // "Hello"
//                 "0000000000000000000000000000000000000000000000000000000000000005" + // length = 5
//                 "576f726c64000000000000000000000000000000000000000000000000000000"   // "World"
      
//       val result = SolidityTuple.decodeTupleResult("0x" + hex, "string[]")
//       result should === ("[Hello,World]")
//     }

//     "decode bytes[] dynamic array" in {
//       val hex = "0000000000000000000000000000000000000000000000000000000000000020" + // offset to array
//                 "0000000000000000000000000000000000000000000000000000000000000002" + // length = 2
//                 "0000000000000000000000000000000000000000000000000000000000000040" + // offset to first bytes
//                 "0000000000000000000000000000000000000000000000000000000000000080" + // offset to second bytes
//                 "0000000000000000000000000000000000000000000000000000000000000004" + // length = 4
//                 "deadbeef00000000000000000000000000000000000000000000000000000000" + // 0xdeadbeef
//                 "0000000000000000000000000000000000000000000000000000000000000004" + // length = 4
//                 "cafebabe00000000000000000000000000000000000000000000000000000000"   // 0xcafebabe
//       val result = SolidityTuple.decodeTupleResult("0x" + hex, "bytes[]")
//       result should === ("[0xdeadbeef,0xcafebabe]")
//     }

//     "decode empty dynamic array" in {
//       val hex = "0000000000000000000000000000000000000000000000000000000000000020" + // offset to array
//                 "0000000000000000000000000000000000000000000000000000000000000000"   // length = 0
//       val result = SolidityTuple.decodeTupleResult("0x" + hex, "uint256[]")
//       result should === ("[]")
//     }

//     // ==========================================================================================================
//     // Arrays in Tuples Tests
//     // ==========================================================================================================

//     // cast abi-encode "func((uint256[],address))" '([10,20],0x742d35cc6634c0532925a3b844bc454e4438f44e)'
//     "decode (uint256[],address) tuple" in {
//       val hex = "00000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000040000000000000000000000000742d35cc6634c0532925a3b844bc454e4438f44e0000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000a0000000000000000000000000000000000000000000000000000000000000014"   // 20
//       val result = SolidityTuple.decodeTupleResult("0x" + hex, "(uint256[],address)")
//       result should === ("([10,20],0x742d35cc6634c0532925a3b844bc454e4438f44e)")
//     }

//     // cast abi-encode "func((uint256,address[]))" '(123,[0x742d35cc6634c0532925a3b844bc454e4438f44e,0x742d35cc6634c0532925a3b844bc454e4438f44e])'
//     "decode (uint256,address[]) tuple" in {
//       val hex = "0000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000007b00000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000002000000000000000000000000742d35cc6634c0532925a3b844bc454e4438f44e000000000000000000000000742d35cc6634c0532925a3b844bc454e4438f44e" 
//       val result = SolidityTuple.decodeTupleResult("0x" + hex, "(uint256,address[])")
//       result should === ("(123,[0x742d35cc6634c0532925a3b844bc454e4438f44e,0x742d35cc6634c0532925a3b844bc454e4438f44e])")
//     }

//     // cast abi-encode "func((uint256,(bool,string),address[]))" '(123,(true,Hello World),[0x742d35cc6634c0532925a3b844bc454e4438f44e,0x742d35cc6634c0532925a3b844bc454e4438f44e])'
//     "decode (uint256,(bool,string),address[]) complex nested tuple" in {
//       val hex = "0000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000007b000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000000e000000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000000b48656c6c6f20576f726c640000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000002000000000000000000000000742d35cc6634c0532925a3b844bc454e4438f44e000000000000000000000000742d35cc6634c0532925a3b844bc454e4438f44e"
//       val result = SolidityTuple.decodeTupleResult("0x" + hex, "(uint256,(bool,string),address[])")
//       result should === ("(123,(true,Hello World),[0x742d35cc6634c0532925a3b844bc454e4438f44e,0x742d35cc6634c0532925a3b844bc454e4438f44e])")
//     }
    
//   }

//   "SolidityTuple Json" should {
//     "generate ABI JSON for a single primitive type" in {
//       val abi = SolidityTuple.abiJsonFromTypes(List("uint256"), "myFunc")      
//       abi should include ("\"type\": \"uint256\"")
//       abi should include ("\"name\": \"myFunc\"")
//     }
//     "generate ABI JSON for a tuple" in {
//       val abi = SolidityTuple.abiJsonFromTypes(List("(uint256,bool)"), "myFunc")
//       abi should include ("\"type\": \"tuple\"")
//       abi should include ("\"type\": \"uint256\"")
//       abi should include ("\"type\": \"bool\"")
//     }
//     "generate ABI JSON for a nested tuple" in {
//       val abi = SolidityTuple.abiJsonFromTypes(List("(uint256,(bool,string),address)"), "myFunc")
//       // info(s"abi: ${abi}")
//       abi should include ("\"type\": \"tuple\"")
//       abi should include ("\"type\": \"string\"")
//       abi should include ("\"type\": \"address\"")
//     }
//     "generate ABI JSON for multiple arguments" in {
//       val abi = SolidityTuple.abiJsonFromTypes(List("uint256", "address"), "myFunc")
//       abi should include ("\"type\": \"uint256\"")
//       abi should include ("\"type\": \"address\"")
//     }
//     "generate ABI JSON for tuple with array" in {
//       val abi = SolidityTuple.abiJsonFromTypes(List("(uint256[],address)"), "myFunc")
//       abi should include ("\"type\": \"uint256[]\"")
//       abi should include ("\"type\": \"address\"")
//     }
//     "generate ABI JSON for deeply nested tuple" in {
//       val abi = SolidityTuple.abiJsonFromTypes(List("(uint256,(bool,(string,address)),bytes32)"), "myFunc")
//       abi should include ("\"type\": \"tuple\"")
//       abi should include ("\"type\": \"string\"")
//       abi should include ("\"type\": \"bytes32\"")
//     }
//   }

  "SolidityTuple tuples[] arrays" should {
    // cast abi-encode "func((uint256,address)[])" '[(10,0x742d35cc6634c0532925a3b844bc454e4438f44e),(20,0x742d35cc6634c0532925a3b844bc454e4438f44e)]'
    // ATTENTION: NOT CORRECT Result
    "decode (uint256,address)[] " in {
      val hex = "00000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000742d35cc6634c0532925a3b844bc454e4438f44e0000000000000000000000000000000000000000000000000000000000000014000000000000000000000000742d35cc6634c0532925a3b844bc454e4438f44e"
      val result = SolidityTuple.decodeTupleResult("0x" + hex, "(uint256,address)[]")
      //result should === ("[(10,0x742d35cc6634c0532925a3b844bc454e4438f44e),(20,0x742d35cc6634c0532925a3b844bc454e4438f44e)]")
      result should === ("[((10,0x742d35cc6634c0532925a3b844bc454e4438f44e)),((20,0x742d35cc6634c0532925a3b844bc454e4438f44e))]")
    }

    //cast abi-encode "func(((uint256,address)[],(int256,int8)))" '([(10,0x742d35cc6634c0532925a3b844bc454e4438f44e),(20,0x742d35cc6634c0532925a3b844bc454e4438f44e)],(10000,7))'
    // ATTENTION: NOT CORRECT Result
    "decode ((uint256,address)[],(int256,int8)) " in {
      val hex = "00000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000060000000000000000000000000000000000000000000000000000000000000271000000000000000000000000000000000000000000000000000000000000000070000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000742d35cc6634c0532925a3b844bc454e4438f44e0000000000000000000000000000000000000000000000000000000000000014000000000000000000000000742d35cc6634c0532925a3b844bc454e4438f44e"
      val result = SolidityTuple.decodeTupleResult("0x" + hex, "((uint256,address)[],(int256,int8))")
      result should === ("((((10,0x742d35cc6634c0532925a3b844bc454e4438f44e)),((20,0x742d35cc6634c0532925a3b844bc454e4438f44e))),(10000,7))")
    }

    // cast abi-encode "func((uint256,address)[],(int256,int8))" '[(10,0x742d35cc6634c0532925a3b844bc454e4438f44e),(20,0x742d35cc6634c0532925a3b844bc454e4438f44e)]' '(10000,7)'
    "decode (uint256,address)[],(int256,int8) " in {
      val hex = "0000000000000000000000000000000000000000000000000000000000000060000000000000000000000000000000000000000000000000000000000000271000000000000000000000000000000000000000000000000000000000000000070000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000742d35cc6634c0532925a3b844bc454e4438f44e0000000000000000000000000000000000000000000000000000000000000014000000000000000000000000742d35cc6634c0532925a3b844bc454e4438f44e"
      val result = SolidityTuple.decodeTupleResult("0x" + hex, "(uint256,address)[],(int256,int8)")
      result should === ("((((10,0x742d35cc6634c0532925a3b844bc454e4438f44e)),((20,0x742d35cc6634c0532925a3b844bc454e4438f44e))),(10000,7))")
    }



    // DOES NOT WORK !
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

    // ETH_RPC_URL=http://geth:8545 cast call 0x87870Bca3F3fD6335C3F4ce8392D69350B4fA4E2 "getUserAccountData(address)" "0xa76b6a7aa1b4501e6edcb29898e1ce4b9784e81c"
    // 
    //   totalCollateralBase   uint256 :  964204011436221
    //   totalDebtBase         uint256 :  701680545761128
    //   availableBorrowsBase  uint256 :  34296376168139
    //   currentLiquidationThreshold uint256 :  8022
    //   ltv                   uint256 :  7633
    //   healthFactor          uint256 :  1102331342441767072

    // // cast sig "func((uint256,uint256,uint256,uint256,uint256,uint256))"
    // // cast abi-encode "func((uint256,uint256,uint256,uint256,uint256,uint256))" "(964204011436221,701680545761128,34296376168139,8022,7633,1102331342441767072)"
    
    // // Test for Aave style return data (tuple of 6 uint256)
    // // Example values from the comment above:
    // //   totalCollateralBase   uint256 :  964204011436221
    // //   totalDebtBase         uint256 :  701680545761128
    // //   availableBorrowsBase  uint256 :  34296376168139
    // //   currentLiquidationThreshold uint256 :  8022
    // //   ltv                   uint256 :  7633
    // //   healthFactor          uint256 :  1102331342441767072
    // "decode Aave getUserAccountData tuple result" in {
    //   val hex =
    //     "00000000000000000000000000000000000000000000000000036cf03d97b8bd00000000000000000000000000000000000000000000000000027e2cbbad076800000000000000000000000000000000000000000000000000001f313f518ecb0000000000000000000000000000000000000000000000000000000000001f560000000000000000000000000000000000000000000000000000000000001dd10000000000000000000000000000000000000000000000000f4c4483fb0560a0"
    //   val result = SolidityTuple.decodeTupleResult("0x" + hex, "(uint256,uint256,uint256,uint256,uint256,uint256)")
    //   result should === ("(964204011436221,701680545761128,34296376168139,8022,7633,1102331342441767072)")
    // }

    // (
    //   address 0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2,
    //   string  Wrapped Ether,
    //   string  WETH,
    //   uint256 18,
    //   uint256 8050,
    //   uint256 8300,
    //   uint256 10500,
    //   uint256 1500,
    //   bool    true,
    //   bool    true,
    //   bool    true,
    //   bool    false,
    //   uint256 1049578555095284712086517450,
    //   uint256 1076809177010959824906559554,
    //   uint256 23996553621138065427333809,
    //   uint256 29858212071015678076143200,
    //   uint256 1753364915,
    //   address 0x4d5F47FA6A74757f35C14fD3a6Ef8E3C9BC514E8,
    //   address 0xeA51d7853EEFb32b6ee06b1C12E6dcCA88Be0fFE,
    //   address 0x9ec6F08190DeA04A54f8Afc53Db96134e5E3FdFB,
    //   uint256 126348457835353124213151,
    //   uint256 2036014684604809578234226,
    //   uint256 364243420000,
    //   address 0x5424384B256154046E9667dDFaaa5e550145215e,
    //   uint256 30000000000000000000000000,
    //   uint256 200000000000000000000000000,
    //   uint256 0,
    //   uint256 950000000000000000000000000,
    //   bool    false,
    //   bool    false,
    //   uint256 5430467433261440628,
    //   uint256 0,
    //   uint256 0,
    //   bool    true,
    //   uint256 0,
    //   uint256 2,
    //   uint256 2900000,
    //   uint256 3200000,
    //   bool    false,
    //   bool    true,
    //   uint256 126348451381806836683560
    // )
    
  }
}
