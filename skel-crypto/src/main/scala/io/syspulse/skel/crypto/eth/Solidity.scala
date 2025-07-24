package io.syspulse.skel.crypto.eth

import scala.util.{Try,Success,Failure}

import org.web3j.abi.TypeReference
import io.syspulse.skel.util.Util

import org.web3j.abi.datatypes
import java.math.BigInteger
import scala.jdk.CollectionConverters._
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.TypeDecoder
import org.web3j.abi.FunctionReturnDecoder
import scala.collection.mutable.Buffer
import org.web3j.abi.Utils

object Solidity {
  def parseFunction(input: String): (String, Vector[String], String) = {
    if(input.isBlank())
      throw new Exception(s"failed to parse function: '${input}'")

    // Find the function name and parameters by looking for the first '('
    val firstParenIndex = input.indexOf('(')
    if (firstParenIndex == -1) {
      // No parentheses - treat as function name only
      return (input, parseTypes(""), "")
    }

    val funcName = input.substring(0, firstParenIndex).trim
    
    // Find the matching closing parenthesis for the input parameters
    val (inputParams, remainingAfterInput) = extractBalancedParentheses(input, firstParenIndex)
    
    // Check if there's an output type (another set of parentheses)
    if (remainingAfterInput.trim.startsWith("(")) {
      val (outputParams, _) = extractBalancedParentheses(remainingAfterInput, 0)
      (funcName, parseTypes(inputParams), outputParams)
    } else {
      (funcName, parseTypes(inputParams), "")
    }
  }

  // Helper function to extract balanced parentheses content
  private def extractBalancedParentheses(input: String, startIndex: Int): (String, String) = {
    var depth = 0
    var i = startIndex
    var content = new StringBuilder
    var foundOpening = false
    
    while (i < input.length) {
      val char = input.charAt(i)
      
      if (char == '(') {
        if (!foundOpening) {
          foundOpening = true
        } else {
          content.append(char)
        }
        depth += 1
      } else if (char == ')') {
        depth -= 1
        if (depth == 0) {
          // Found the closing parenthesis for the opening one
          return (content.toString, input.substring(i + 1))
        } else {
          content.append(char)
        }
      } 
      else if(char == ' ' || char == '\n') {
        //
      }
      else if (foundOpening) {
        content.append(char)
      }
      
      i += 1
    }
    
    // If we get here, parentheses are not balanced
    throw new Exception(s"Unbalanced parentheses in function: '${input}'")
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

  def toWeb3Type(typeStr: String, value: String): datatypes.Type[_] = {    
    
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
      
      case "bool" | "boolean" => 
        new datatypes.Bool(value.toBoolean){}

      case "string" => 
        new datatypes.Utf8String(value){}

      // Handle various integer types
      case t if t.startsWith("uint") => 
        val bits = if(t == "uint") 256 else t.substring(4).toInt
        val bigInt = if (value.startsWith("0x")) 
          new BigInteger(value.substring(2), 16) 
        else 
          new BigInteger(value)
        bits match {
          case 256 => new datatypes.generated.Uint256(bigInt){}
          case 128 => new datatypes.generated.Uint128(bigInt){}
          case 64 => new datatypes.generated.Uint64(bigInt){}
          case 32 => new datatypes.generated.Uint32(bigInt){}
          case 8 => new datatypes.generated.Uint8(bigInt){}
          case _ => new datatypes.Uint(bigInt){}
        }
        
      case t if t.startsWith("int") =>
        val bits = if(t == "int") 256 else t.substring(3).toInt
        val bigInt = if (value.startsWith("0x")) 
          new BigInteger(value.substring(2), 16) 
        else 
          new BigInteger(value)
        bits match {
          case 256 => new datatypes.generated.Int256(bigInt){}
          case 128 => new datatypes.generated.Int128(bigInt){}
          case 64 => new datatypes.generated.Int64(bigInt){}
          case 32 => new datatypes.generated.Int32(bigInt){}
          case 8 => new datatypes.generated.Int8(bigInt){}
          case _ => new datatypes.Int(bigInt){}
        }

      case "bytes32" => 
        new datatypes.generated.Bytes32(Util.fromHexString(value)){}
      case "bytes" => 
        new datatypes.DynamicBytes(Util.fromHexString(value)){}
              
      case t if t.startsWith("(") && t.endsWith(")") =>
        val tupleTypes = parseTupleTypes(t)
        val tupleValues = parseTupleValues(value)
        if(tupleTypes.size != tupleValues.size)
          throw new Exception(s"Tuple size mismatch: types=${tupleTypes.size}: values=${tupleValues.size}")

        new datatypes.DynamicStruct(
          tupleTypes.zip(tupleValues).map { case (t, v) => {
            toWeb3Type(t, v)
          }}.toList.asJava
        ){
          override def toString(): String = {
            s"DynamicStruct(${tupleTypes.mkString(",")})"
          }
          override def getTypeAsString(): String = {
            s"""(${tupleTypes.mkString(",")})"""
          }
        }

      case t => throw new Exception(s"Unsupported type: ${t}")
    }
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
        // ATTENTION! Not working !

        val tupleTypes = parseTupleTypes(t)
        val tupleRefs = tupleTypes.map(t => {
          
          val tr = TypeReference.makeTypeReference(t)
          // println(s"==============> ${t}: ${tr.getType()}")
          tr
        })        
        // val tupleTr: TypeReference[datatypes.DynamicStruct] =
          // new TypeReference[datatypes.DynamicStruct](
          //         false,
          //         tupleRefs.toList.asJava.asInstanceOf[java.util.List[TypeReference[_]]]
          //         //List(TypeReference.makeTypeReference("uint256"),TypeReference.makeTypeReference("address")).asJava.asInstanceOf[java.util.List[TypeReference[_]]]
          //         ) {};
        
        // ************************************************************************************
        // ATTENTION: NOT WORKING, need to upgrade web3j to 4.13.0 
        // ************************************************************************************
        val tupleTr: TypeReference[datatypes.DynamicStruct] =
          new TypeReference[datatypes.DynamicStruct](
                  false
                  ) {};
        tupleTr

        // val tupleTypesList = tupleTypes.map { t => toTypeReference(t).getType() }
        //   .toList
        //   .asJava
        //   .asInstanceOf[java.util.List[org.web3j.abi.datatypes.Type[_]]]
        
        // // val struct = new datatypes.DynamicStruct(tupleTypesList)
        // new TypeReference[datatypes.DynamicStruct]() {
        //   override def getType: java.lang.reflect.Type = 
        //     new datatypes.DynamicStruct(tupleTypesList).getClass
        // }
        //throw new Exception(s"Tuples are not supported because web3j is retarded")
      
        // ATTENTION: Tuples are not supported because web3j is retarded
        // TypeReference.makeTypeReference(t)

        
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

  def encodeFunctionWithoutOutputType(func: String, params: Seq[String]): (String,String) = { 
    encodeFunc(func,params,output = false)
  }

  def encodeFunctionWithOutputType(func: String, params: Seq[String]): (String, String) = {    
    encodeFunc(func,params,output = true)
  }

  def encodeFunc(func: String, params: Seq[String], output:Boolean = true): (String, String) = {    
    val (funcName,inputTypes,outputType) = parseFunction(func)
        
    if(inputTypes.size != params.size)
      throw new Exception(s"Invalid parameters count: types=${inputTypes.size}, params=${params.size}: ${params}")

    val inputParameters = inputTypes.zipWithIndex.map { case (paramType, i) => toWeb3Type(paramType, params(i)) }
    
    // val outputParameters = if(!output || outputType.isEmpty) 
    //   Seq.empty[TypeReference[_]] 
    // else 
    //   Seq(toTypeReference(outputType))
    val outputParameters = Seq.empty[TypeReference[_]] 
        
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


  // ==========================================================================================================
  def fromWeb3Type(value: String, typeStr: String): Buffer[datatypes.Type[_]] = { 
    val typeReference = toTypeReference(typeStr)
    
    val decoded = FunctionReturnDecoder.decode(
      value, 
      //List(typeReference).asJava.asInstanceOf[java.util.List[TypeReference[datatypes.Type[_]]]]
      Utils.convert(List(typeReference).asJava.asInstanceOf[java.util.List[TypeReference[_]]])
    )
    
    def processValue(value: datatypes.Type[_]): datatypes.Type[_] = {
      value match {
        case struct: datatypes.DynamicStruct =>
          // Recursively process each component in the tuple
          val processedComponents = struct.getValue.asScala.map(processValue)
          new datatypes.DynamicStruct(processedComponents.toList.asJava) {}

        case array: datatypes.DynamicArray[_] =>
          // Recursively process each element in the array
          val processedElements = array.getValue.asScala.map(processValue)
          new datatypes.DynamicArray(processedElements.toList.asJava) {}
        
        case bytes: datatypes.DynamicBytes => bytes
        case bytes32: datatypes.generated.Bytes32 => bytes32
        case other => other
      }
    }

    decoded.asScala.map(processValue)
  }

  def decodeData(dataType: String, data: String): Try[String] = {    
    Try {
      if(data.isEmpty || data == "0x") return Success("")
      if(dataType.isEmpty || dataType == "()") return Success(data)

      def typeToString(t: datatypes.Type[_]): String = t match {
        case struct: datatypes.DynamicStruct =>
          val components = struct.getValue.asScala.map(typeToString)
          s"(${components.mkString(",")})"

        case array: datatypes.DynamicArray[_] =>
          val elements = array.getValue.asScala.map(typeToString)
          s"[${elements.mkString(",")}]"
        
        case bytes: datatypes.DynamicBytes => 
          Util.hex(bytes.getValue)
        case bytes32: datatypes.generated.Bytes32 => 
          Util.hex(bytes32.getValue)
        case other => 
          other.getValue.toString
      }

      val decoded = Solidity.fromWeb3Type(data, dataType)    
      decoded.map(typeToString).mkString(",")
    }
  }

  def decodeResult(result: String, outputType: String): Try[String] = 
    decodeData(outputType,result)

  def encodeData(input: String, params: Seq[String]): Try[String] = {
    Try(
      // function signate size is 10: 0x9630f389
      encodeFunction(s"FUN(${input})",params)//.drop(10)
    )
  }
  

}