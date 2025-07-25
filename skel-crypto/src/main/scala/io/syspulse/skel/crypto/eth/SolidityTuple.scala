package io.syspulse.skel.crypto.eth

import scala.jdk.CollectionConverters._
import scala.util.{Try, Success, Failure}
import com.typesafe.scalalogging.Logger
import java.io.StringBufferInputStream
import java.math.BigInteger

import io.syspulse.skel.util.Util
import io.syspulse.skel.crypto.Hash
import ujson._

import net.osslabz.evm.abi.decoder.AbiDecoder
import net.osslabz.evm.abi.decoder.DecodedFunctionCall
import net.osslabz.evm.abi.definition.SolidityType.TupleType
import net.osslabz.evm.abi.definition.SolObject

object SolidityTuple {
  
  def valueToString(d:Int,name: String, value: SolObject, t: String): String = {    
    //println(s"${d}: ${name}: t=${t}, type=${value.getTypeName()}: value=${value.getValue()}")
    
    value.getTypeName() match {
      case null => "null"
      
      // address is a string interanlly
      case "address" => Util.hex(value.getValue().asInstanceOf[Array[Byte]])
      
      // this is when outisde of tuple (primitive)
      case "string" => s""""${value.getValue().asInstanceOf[String]}""""

      case "bool" => value.getValue().asInstanceOf[Boolean].toString

      case _ if value.getTypeName().endsWith("]") => 
        
        val s = value.getValue().asInstanceOf[Array[SolObject]].map(v => {
          valueToString(d + 1,name,v,t)

        }).mkString(",")

        s"[${s}]"

      case _ if value.getTypeName().startsWith("tuple") => 
        
        val s = value.getValue().asInstanceOf[Array[SolObject]].map(v => {
          valueToString(d + 1,name,v,t)

        }).mkString(",")

        s"(${s})"

      case _ if value.getTypeName().startsWith("bytes") => 
        Util.hex(value.getValue().asInstanceOf[Array[Byte]])
      
      
      // this is when inside tuple
      // case v if v.isInstanceOf[String] => s""""${v.asInstanceOf[String]}""""

      case _ => value.getValue().toString()
    }
  }

  def decodeTupleResult(hexData: String, tupleType: String): String = {
    //println(s"====================> tupleType: '${tupleType}'")

    if(hexData.isEmpty || hexData == "0x") return ""

    // Split the tupleType at top level (outside of tuples/arrays)
    val types = splitTypesAtTopLevel(tupleType)
    
    // if (types.length == 1) {
    //   // Single type - treat as before
    //   decode(hexData, List(tupleType))
    // } else {
    //   // Multiple types - decode each separately
    //   decode(hexData, types)
    // }
    decode(hexData, types)
  }

  // Splits a comma-separated type list only at top level (not inside tuples or arrays)
  def splitTypesAtTopLevel(s: String): List[String] = {
    val buf = new StringBuilder
    var depth = 0
    var bracketDepth = 0
    val out = collection.mutable.ListBuffer[String]()
    
    for (c <- s) {
      c match {
        case '(' => depth += 1; buf += c
        case ')' => depth -= 1; buf += c
        case '[' => bracketDepth += 1; buf += c
        case ']' => bracketDepth -= 1; buf += c
        case ',' if depth == 0 && bracketDepth == 0 => 
          out += buf.toString.trim; buf.clear()
        case _ => buf += c
      }
    }
    if (buf.nonEmpty) out += buf.toString.trim
    out.toList
  }
  
  def decode(hexData: String, tupleType: List[String]): String = {
    if(hexData.isEmpty || hexData == "0x") return ""

    val funcName = "func";
    
    // do not try to calculate signature - uint == unt256. Just take the first signature from AbiDecoder
    // val funcNameTypes = s"${funcName}(${tupleType})"
    // val funcSig = Util.hex(Hash.keccak256(funcNameTypes).take(4))
    
    val abiJson = abiJsonFromTypes(tupleType, funcName)
    println(s"====================> abiJson: \n${abiJson}")

    val decoder = new AbiDecoder(new StringBufferInputStream(abiJson));        
    //  println(s"====================> AbiDecoder: ${decoder.getMethodSignatures}")
    val funcSig = decoder.getMethodSignatures.asScala.keys.head
    
    val decode = decoder.decodeFunctionCall( funcSig + hexData.stripPrefix("0x"))
        
    // val param = decode.getParams().stream().findFirst().orElse(null);    
    // println(s">>>> param: ${param}")    

    val params = decode.getParams().asScala
    println(s">>>> params[${params.size}]: ${params}")
                
    val r = for (p <- params)
        yield valueToString(0,p.getName(),p.getValue(),p.getType())
    
    r.mkString(",")
  }

  def decodeResult(hexData: String, tupleType: String): Try[String] = {
    Try {
      decodeTupleResult(hexData,tupleType)
    }
  }

  def decodeData(dataType: String, data: String): Try[String] = {    
    Try {
      decodeTupleResult(data,dataType)
    }
  }

  def abiJsonFromTypes(types: List[String], funcName: String = "func"): String = {

    def parseType(d:Int, t: String, idx: Int = 0): Value = {
      val trimmed = t.trim
      
      // Check if it's an array of tuples (e.g., "(uint256,string)[]")
      if (trimmed.matches("\\(.*\\)\\[\\]")) {
        val tupleContent = trimmed.substring(1, trimmed.lastIndexOf(")"))
        val components = splitTypes(tupleContent).zipWithIndex.map { case (sub, i) => parseType(d + 1,sub, i) }
        val tupleType = Arr(components: _*)
        Obj("name" -> s"_value${d}_$idx", "type" -> "tuple[]", "components" -> tupleType)
      }
      // Check if it's a static array of tuples (e.g., "(uint256,string)[2]")
      else if (trimmed.matches("\\(.*\\)\\[\\d+\\]")) {
        val tupleContent = trimmed.substring(1, trimmed.lastIndexOf(")"))
        val arraySize = trimmed.replaceAll(".*\\[(\\d+)\\]$", "$1").toInt
        val components = splitTypes(tupleContent).zipWithIndex.map { case (sub, i) => parseType(d + 1,sub, i) }
        // val tupleType = Obj("type" -> "tuple", "components" -> Arr(components: _*))
        // Obj("name" -> s"_value$idx", "type" -> s"tuple[$arraySize]", "components" -> Arr(tupleType))
        val tupleType = Arr(components: _*)
        Obj("name" -> s"_value${d}_$idx", "type" -> s"tuple[$arraySize]", "components" -> tupleType)
      }
      // Check if it's a dynamic array (ends with [])
      else if (trimmed.endsWith("[]")) {
        val baseType = trimmed.substring(0, trimmed.length - 2)
        val baseTypeObj = parseType(d + 1,baseType, idx)
        Obj("name" -> s"_value${d}_$idx", "type" -> s"${baseTypeObj.obj("type").str}[]")
      }
      // Check if it's a static array (ends with [n])
      else if (trimmed.matches(".*\\[\\d+\\]$")) {
        val baseType = trimmed.replaceAll("\\[\\d+\\]$", "")
        val arraySize = trimmed.replaceAll(".*\\[(\\d+)\\]$", "$1").toInt
        val baseTypeObj = parseType(d + 1,baseType, idx)
        Obj("name" -> s"_value${d}_$idx", "type" -> s"${baseTypeObj.obj("type").str}[$arraySize]")
      }
      // Check if it's a tuple
      else if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
        // Tuple: recursively parse components
        val inner = trimmed.substring(1, trimmed.length - 1)
        val components = splitTypes(inner).zipWithIndex.map { case (sub, i) => parseType(d + 1,sub, i) }
        Obj("name" -> s"_value${d}_$idx", "type" -> "tuple", "components" -> Arr(components: _*))
      } 
      else {
        // Simple type
        Obj("name" -> s"_value${d}_$idx", "type" -> trimmed)
      }
    }

    // Splits a comma-separated type list, handling nested tuples/arrays
    def splitTypes(s: String): List[String] = {
      val buf = new StringBuilder
      var depth = 0
      val out = collection.mutable.ListBuffer[String]()
      for (c <- s) {
        c match {
          case '(' => depth += 1; buf += c
          case ')' => depth -= 1; buf += c
          case ',' if depth == 0 => out += buf.toString.trim; buf.clear()
          case _ => buf += c
        }
      }
      if (buf.nonEmpty) out += buf.toString.trim
      out.toList
    }

    val inputs = types.zipWithIndex.map { case (t, i) => parseType(0,t, i) }
    val abi = Arr(
      Obj(
        "inputs" -> Arr(inputs: _*),
        "outputs" -> Arr(),
        "name" -> funcName,
        "type" -> "function"
      )
    )
    ujson.write(abi, indent = 2)
  }

} 