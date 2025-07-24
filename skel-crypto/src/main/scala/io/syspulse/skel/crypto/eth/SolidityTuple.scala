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

object SolidityTuple {
  
  def valueToString(value: Any, t: String): String = {    
    // println(s"${t} -----> value: ${value}")

    var w1 = if(t == "tuple") '(' else '['
    var w2 = if(t == "tuple") ')' else ']'
    
    value match {
      case null => "null"
      
      // address is a string interanlly
      // case v if t == "address" => v.asInstanceOf[String]
      
      // this is when outisde of tuple (primitive)
      // case v if t == "string" => s""""${v.asInstanceOf[String]}""""      

      case _ if value.isInstanceOf[DecodedFunctionCall.Param] =>         
        val p = value.asInstanceOf[DecodedFunctionCall.Param]
        valueToString(p.getValue(),p.getType())

      case _ if value.isInstanceOf[Array[Byte]] => 
        Util.hex(value.asInstanceOf[Array[Byte]])

      case _ if value.isInstanceOf[Array[DecodedFunctionCall.Param]] =>         
        s"${w1}${value.asInstanceOf[Array[DecodedFunctionCall.Param]].map(v => valueToString(v,v.getType())).mkString(",")}${w2}"
           
      case _ if value.isInstanceOf[Array[Any]] => 
        val s = value.asInstanceOf[Array[Any]].map(v => {
          //val tNoArray = t.replaceAll("\\[\\d+\\]", "")
          val a = t.indexOf("[")
          val tNoArray = if(a > 0) t.substring(0,a) else t
         
          valueToString(v,tNoArray)

        }).mkString(",")

        s"${w1}${s}${w2}"

      // bytes are hex string    
      case v if t.startsWith("bytes") => v.asInstanceOf[String]

      // this is when inside tuple
      // case v if v.isInstanceOf[String] => s""""${v.asInstanceOf[String]}""""

      case _ => value.toString()
    }
  }

  def decodeTupleResult(hexData: String, tupleType: String): String = {
    println(s"====================> tupleType: '${tupleType}'")

    if(hexData.isEmpty || hexData == "0x") return ""

    // Split the tupleType at top level (outside of tuples/arrays)
    val types = splitTypesAtTopLevel(tupleType)
    
    if (types.length == 1) {
      // Single type - treat as before
      decode(hexData, List(tupleType))
    } else {
      // Multiple types - decode each separately
      decode(hexData, types)
    }
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
        
    val param = decode.getParams().stream().findFirst().orElse(null);
    // println(s">>>> param: ${param}")    
                
    val r = for (p <- decode.getParams().asScala)
        yield valueToString(p.getValue(),p.getType())
    
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
    def parseType(t: String, idx: Int = 0): Value = {
      val trimmed = t.trim
      
      // Check if it's an array of tuples (e.g., "(uint256,string)[]")
      if (trimmed.matches("\\(.*\\)\\[\\]")) {
        val tupleContent = trimmed.substring(1, trimmed.lastIndexOf(")"))
        val components = splitTypes(tupleContent).zipWithIndex.map { case (sub, i) => parseType(sub, i) }
        val tupleType = Obj("type" -> "tuple", "components" -> Arr(components: _*))
        Obj("name" -> s"_value$idx", "type" -> "tuple[]", "components" -> Arr(tupleType))
      }
      // Check if it's a static array of tuples (e.g., "(uint256,string)[2]")
      else if (trimmed.matches("\\(.*\\)\\[\\d+\\]")) {
        val tupleContent = trimmed.substring(1, trimmed.lastIndexOf(")"))
        val arraySize = trimmed.replaceAll(".*\\[(\\d+)\\]$", "$1").toInt
        val components = splitTypes(tupleContent).zipWithIndex.map { case (sub, i) => parseType(sub, i) }
        val tupleType = Obj("type" -> "tuple", "components" -> Arr(components: _*))
        Obj("name" -> s"_value$idx", "type" -> s"tuple[$arraySize]", "components" -> Arr(tupleType))
      }
      // Check if it's a dynamic array (ends with [])
      else if (trimmed.endsWith("[]")) {
        val baseType = trimmed.substring(0, trimmed.length - 2)
        val baseTypeObj = parseType(baseType, idx)
        Obj("name" -> s"_value$idx", "type" -> s"${baseTypeObj.obj("type").str}[]")
      }
      // Check if it's a static array (ends with [n])
      else if (trimmed.matches(".*\\[\\d+\\]$")) {
        val baseType = trimmed.replaceAll("\\[\\d+\\]$", "")
        val arraySize = trimmed.replaceAll(".*\\[(\\d+)\\]$", "$1").toInt
        val baseTypeObj = parseType(baseType, idx)
        Obj("name" -> s"_value$idx", "type" -> s"${baseTypeObj.obj("type").str}[$arraySize]")
      }
      // Check if it's a tuple
      else if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
        // Tuple: recursively parse components
        val inner = trimmed.substring(1, trimmed.length - 1)
        val components = splitTypes(inner).zipWithIndex.map { case (sub, i) => parseType(sub, i) }
        Obj("name" -> s"_value$idx", "type" -> "tuple", "components" -> Arr(components: _*))
      } 
      else {
        // Simple type
        Obj("name" -> s"_value$idx", "type" -> trimmed)
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

    val inputs = types.zipWithIndex.map { case (t, i) => parseType(t, i) }
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