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
      case _ => throw new Exception(s"failed to parse function: '${input}'")
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
    def splitArrayElements(str: String): Array[String] = {
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

    typeStr.toLowerCase match {
      case t if t.endsWith("[]") =>
        val baseType = t.dropRight(2)
        val cleanValue = value.trim
        if (!cleanValue.startsWith("[") || !cleanValue.endsWith("]"))
          throw new Exception(s"Invalid array format: ${value}")
          
        val arrayValues = splitArrayElements(cleanValue.drop(1).dropRight(1))
        new datatypes.DynamicArray(
          arrayValues.map(v => toWeb3Type(baseType, v)).toList.asJava
        ){}

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
              
      case t if t.startsWith("(") && t.endsWith(")") =>
        val tupleTypes = parseTupleTypes(t)
        val tupleValues = parseTupleValues(value)
        if(tupleTypes.size != tupleValues.size)
          throw new Exception(s"Tuple size mismatch: types=${tupleTypes.size}: values=${tupleValues.size}")
        new datatypes.DynamicStruct(
          tupleTypes.zip(tupleValues).map { case (t, v) => toWeb3Type(t, v) }.toList.asJava
        ){}

      case t => throw new Exception(s"Unsupported type: ${t}")
    }
  }

  private def parseTupleTypes(t: String): Array[String] = {
    def splitTypes(str: String): Array[String] = {
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

    // Remove outer parentheses and split
    if (!t.startsWith("(") || !t.endsWith(")")) 
      throw new Exception(s"Invalid tuple format: ${t}")
    splitTypes(t.drop(1).dropRight(1))
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
        splitGroups(s.drop(1).dropRight(1), ',')
      case s => Array(s)
    }
        
    cleaned
  }

  def toTypeReference(typeStr: String): TypeReference[_] = {
    typeStr.toLowerCase match {
      case t if t.endsWith("[]") =>
        val baseType = t.dropRight(2)
        baseType match {
          case "address" => new TypeReference[datatypes.DynamicArray[datatypes.Address]]() {}
          case "bool" | "boolean" => new TypeReference[datatypes.DynamicArray[datatypes.Bool]]() {}
          case "string" => new TypeReference[datatypes.DynamicArray[datatypes.Utf8String]]() {}
          case "bytes32" => new TypeReference[datatypes.DynamicArray[datatypes.generated.Bytes32]]() {}
          case "bytes" => new TypeReference[datatypes.DynamicArray[datatypes.DynamicBytes]]() {}
          
          case t if t.startsWith("uint") =>
            val bits = if(t == "uint") 256 else t.substring(4).toInt
            bits match {
              case 256 => new TypeReference[datatypes.DynamicArray[datatypes.generated.Uint256]]() {}
              case 128 => new TypeReference[datatypes.DynamicArray[datatypes.generated.Uint128]]() {}
              case 64 => new TypeReference[datatypes.DynamicArray[datatypes.generated.Uint64]]() {}
              case 32 => new TypeReference[datatypes.DynamicArray[datatypes.generated.Uint32]]() {}
              case 8 => new TypeReference[datatypes.DynamicArray[datatypes.generated.Uint8]]() {}
              case _ => new TypeReference[datatypes.DynamicArray[datatypes.Uint]]() {}
            }
            
          case t if t.startsWith("int") =>
            val bits = if(t == "int") 256 else t.substring(3).toInt
            bits match {
              case 256 => new TypeReference[datatypes.DynamicArray[datatypes.generated.Int256]]() {}
              case 128 => new TypeReference[datatypes.DynamicArray[datatypes.generated.Int128]]() {}
              case 64 => new TypeReference[datatypes.DynamicArray[datatypes.generated.Int64]]() {}
              case 32 => new TypeReference[datatypes.DynamicArray[datatypes.generated.Int32]]() {}
              case 8 => new TypeReference[datatypes.DynamicArray[datatypes.generated.Int8]]() {}
              case _ => new TypeReference[datatypes.DynamicArray[datatypes.Int]]() {}
            }
            
          case t => throw new Exception(s"Unsupported array element type: ${t}")
        }

      case t if t.startsWith("(") && t.endsWith(")") =>
        new TypeReference[datatypes.DynamicStruct]() {}
        
      case "address" => new TypeReference[datatypes.Address]() {}
      case "bool" | "boolean" => new TypeReference[datatypes.Bool]() {}
      case "string" => new TypeReference[datatypes.Utf8String]() {}
      
      case t if t.startsWith("uint") => 
        val bits = if(t == "uint") 256 else t.substring(4).toInt
        bits match {
          case 256 => new TypeReference[datatypes.generated.Uint256]() {}
          case 128 => new TypeReference[datatypes.generated.Uint128]() {}
          case 64 => new TypeReference[datatypes.generated.Uint64]() {}
          case 32 => new TypeReference[datatypes.generated.Uint32]() {}
          case 8 => new TypeReference[datatypes.generated.Uint8]() {}
          case _ => new TypeReference[datatypes.Uint]() {}
        }
        
      case t if t.startsWith("int") =>
        val bits = if(t == "int") 256 else t.substring(3).toInt
        bits match {
          case 256 => new TypeReference[datatypes.generated.Int256]() {}
          case 128 => new TypeReference[datatypes.generated.Int128]() {}
          case 64 => new TypeReference[datatypes.generated.Int64]() {}
          case 32 => new TypeReference[datatypes.generated.Int32]() {}
          case 8 => new TypeReference[datatypes.generated.Int8]() {}
          case _ => new TypeReference[datatypes.Int]() {}
        }

      case "bytes32" => new TypeReference[datatypes.generated.Bytes32]() {}
      case "bytes" => new TypeReference[datatypes.DynamicBytes]() {}
      
      case t => throw new Exception(s"Unsupported type: ${t}")
    }
  }

  def encodeFunctionWithOutputType(func: String, params: Seq[String]): (String, String) = {
    val (funcName,inputTypes,outputType) = parseFunction(func)
        
    if(inputTypes.size != params.size)
      throw new Exception(s"Invalid parameters count: types=${inputTypes.size}, params=${params.size}: ${params}")

    val inputParameters = inputTypes.zipWithIndex.map { case (paramType, i) => toWeb3Type(paramType, params(i)) }
    val outputParameters = if(outputType.isEmpty) 
      Seq.empty[TypeReference[_]] 
    else 
      Seq(toTypeReference(outputType))
        
    val function = new datatypes.Function(
        funcName, 
        inputParameters.asJava,
        outputParameters.toList.asInstanceOf[List[TypeReference[_]]].asJava
    )
    val encodedData = FunctionEncoder.encode(function)
    (encodedData,outputType)
  }

  def decodeParams(input: String): Seq[String] = {
    if(input.trim.isEmpty) return Seq.empty
    
    var depth = 0
    var current = new StringBuilder
    var result = Seq.empty[String]
    
    input.foreach {
      case c @ ('[' | '(') => 
        depth += 1
        current.append(c)
      case c @ (']' | ')') => 
        depth -= 1
        current.append(c)
      case ' ' if depth == 0 => 
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

  def encodeFunction(func: String, params: Seq[String]):String = {
    val (encodedData,outputType) = encodeFunctionWithOutputType(func,params)
    encodedData
  }

  def encodeFunction(func: String, params: String): String = {    
    val paramsSeq = decodeParams(params.replaceAll("\\n", " "))
    encodeFunction(func, paramsSeq)
  }
}