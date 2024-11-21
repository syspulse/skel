package io.syspulse.skel.crypto.eth

import scala.util.{Try,Success,Failure}

import org.web3j.abi.TypeReference
import io.syspulse.skel.util.Util

import org.web3j.abi.datatypes
import java.math.BigInteger
import scala.jdk.CollectionConverters._
import org.web3j.abi.FunctionEncoder

object Solidity {
  def parseFunction(input: String): (String, Vector[String], String) = {
    val FunctionPatternWithOutput = """(\w+)\((.*?)\)\((.*?)\)""".r
    val FunctionPatternNoOutput = """(\w+)\((.*?)\)""".r
    
    input match {
      case FunctionPatternWithOutput(name, params, output) => 
        val outputType = parseTypes(output)
        if(outputType.isEmpty)
          (name, parseTypes(params), "")
        else
          (name, parseTypes(params), parseTypes(output)(0))
      case FunctionPatternNoOutput(name, params) => 
        (name, parseTypes(params), "")
      case _ => throw new Exception(s"Failed to parse function: '${input}'")
    }
  }

  def parseTypes(input: String): Vector[String] = {
    if(input.trim.isEmpty) return Vector.empty
        
    var depth = 0
    var current = new StringBuilder
    var result = Vector.empty[String]
    
    input.foreach {
      case '(' => 
        depth += 1
        current.append('(')
      case ')' => 
        depth -= 1
        current.append(')')
      case ',' if depth == 0 => 
        if (current.nonEmpty) {
          result = result :+ current.toString.trim
          current = new StringBuilder
        }
      case c => current.append(c)
    }
    
    if (current.nonEmpty) 
      result = result :+ current.toString.trim
      
    result    
  }
  
  def toWeb3Type(typeStr: String, value: String): datatypes.Type[_] = {
    
    typeStr.toLowerCase match {
      case "address" => new datatypes.Address(value){}
      case "bool" | "boolean" => new datatypes.Bool(value.toBoolean){}
      case "string" => new datatypes.Utf8String(value){}
      
      // Handle various integer types
      case t if t.startsWith("uint") => 
        val bits = if(t == "uint") 256 else t.substring(4).toInt
        bits match {
          case 256 => new datatypes.generated.Uint256(new BigInteger(value)){}
          case 128 => new datatypes.generated.Uint128(new BigInteger(value)){}
          case 64 => new datatypes.generated.Uint64(new BigInteger(value)){}
          case 32 => new datatypes.generated.Uint32(new BigInteger(value)){}
          case 8 => new datatypes.generated.Uint8(new BigInteger(value)){}
          case _ => new datatypes.Uint(new BigInteger(value)){}
        }
        
      case t if t.startsWith("int") =>
        val bits = if(t == "int") 256 else t.substring(3).toInt
        bits match {
          case 256 => new datatypes.generated.Int256(new BigInteger(value)){}
          case 128 => new datatypes.generated.Int128(new BigInteger(value)){}
          case 64 => new datatypes.generated.Int64(new BigInteger(value)){}
          case 32 => new datatypes.generated.Int32(new BigInteger(value)){}
          case 8 => new datatypes.generated.Int8(new BigInteger(value)){}
          case _ => new datatypes.Int(new BigInteger(value)){}
        }

      case "bytes32" => 
        new datatypes.generated.Bytes32(Util.fromHexString(value)){}
      case "bytes" => new datatypes.DynamicBytes(Util.fromHexString(value)){}
      
      // Handle arrays and tuples
      case t if t.endsWith("[]") =>
        val baseType = t.dropRight(2)
        val arrayValues = value.drop(1).dropRight(1).split(",").map(_.trim)
        new datatypes.DynamicArray(
          arrayValues.map(v => {
            toWeb3Type(baseType, v)
          }).toList.asJava
        ){}
        
      case t if t.startsWith("(") && t.endsWith(")") =>
        val tupleTypes = parseTupleTypes(t)
        val tupleValues = parseTupleValues(value)
        if(tupleTypes.length != tupleValues.length)
          throw new Exception(s"Tuple size mismatch: ${tupleTypes.length} types vs ${tupleValues.length} values")
        new datatypes.DynamicStruct(
          tupleTypes.zip(tupleValues).map { case (t, v) => toWeb3Type(t, v) }.toList.asJava
        ){}

      case t => throw new Exception(s"Unsupported type: ${t}")
    }
  }

  private def parseTupleTypes(t: String): Array[String] = {
    // Remove outer parentheses and split by comma
    t.drop(1).dropRight(1).split(",").map(_.trim)
  }

  private def parseTupleValues(v: String): Array[String] = {
    def splitGroups(str: String, delimiter: Char): Array[String] = {
      var depth = 0
      var current = new StringBuilder
      var result = Array.empty[String]
      
      str.foreach {
        case c @ ('(' | '[') => 
          depth += 1
          current.append(c)
        case c @ (')' | ']') => 
          depth -= 1
          current.append(c)
        case c if c == delimiter && depth == 0 => 
          if (current.nonEmpty) {
            result = result :+ current.toString.trim
            current = new StringBuilder
          }
        case c => current.append(c)
      }
      
      if (current.nonEmpty) 
        result = result :+ current.toString.trim
        
      result
    }

    val cleaned = v.trim match {
      case s if s.startsWith("[") && s.endsWith("]") => 
        // For arrays, split by comma
        splitGroups(s.drop(1).dropRight(1), ',')
      case s if s.startsWith("(") && s.endsWith(")") => 
        // For tuples, split by space
        splitGroups(s.drop(1).dropRight(1), ' ')
      case s => Array(s)
    }
    
    cleaned
  }

  def encodeFunction(func: String, params: Seq[String]) = {
    val (funcName,inputTypes,outputType) = parseFunction(func)
    val inputParameters = inputTypes.zipWithIndex.map { case (paramType, i) => toWeb3Type(paramType, params(i)) }
    val outputParameters = if(outputType.isEmpty) 
      Seq.empty[TypeReference[_]] 
    else 
      Seq(toWeb3Type(outputType, ""))
        
    val function = new datatypes.Function(
        funcName, 
        inputParameters.asJava,
        outputParameters.toList.asInstanceOf[List[TypeReference[_]]].asJava
    )
    val encodedData = FunctionEncoder.encode(function)
    encodedData
  }
}