package io.syspulse.skel.crypto.eth

import scala.util.{Try, Success, Failure}
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Solidity ABI Tuple Decoder
 * 
 * This implementation follows the official Solidity ABI specification:
 * https://docs.soliditylang.org/en/latest/abi-spec.html#formal-specification-of-the-encoding
 * 
 * Key principles:
 * - All data is processed as byte arrays for efficiency
 * - Offsets are byte indices into the same array (no copying)
 * - Static types are encoded in-place
 * - Dynamic types are encoded at separately allocated locations
 * - Tuples are dynamic if any component is dynamic
 * - Arrays are dynamic if the base type is dynamic
 */
object SolidityTuple {
  
  // ==========================================================================================================
  // Public API - Entry Points
  // ==========================================================================================================

  /**
   * Decode a tuple result from hexadecimal data according to Solidity ABI format
   * Supports recursive tuples like "(uint256,string,(int,address))"
   */
  def decodeTupleResult(hexData: String, tupleType: String): String = {
    if (hexData.isEmpty || hexData == "0x") return ""
    
    // Remove 0x prefix if present
    val cleanHex = if (hexData.startsWith("0x")) hexData.substring(2) else hexData
    
    // Convert hex string to byte array
    val data = hexStringToByteArray(cleanHex)
    
    // Check if it's a single primitive type (not a tuple)
    if (!tupleType.trim.startsWith("(") || !tupleType.trim.endsWith(")")) {
      val (decodedValue, _) = decodeValue(data, 0, parseType(tupleType.trim))
      decodedValue
    } else {
      // For tuples, check if the first 32 bytes look like an offset to the tuple
      val tupleTypeNode = parseType(tupleType)
      val first32Bytes = if (data.length >= 32) data.take(32) else Array.empty[Byte]
      val firstValue = if (first32Bytes.nonEmpty) bytesToUint256(first32Bytes) else 0
      val tupleDataStart = if (firstValue > 0 && firstValue < data.length) firstValue.toInt else 0
      val (decodedValue, _) = decodeValue(data, tupleDataStart, tupleTypeNode)
      decodedValue
    }
  }

  /**
   * Enhanced decodeData method that handles tuples properly
   */
  def decodeDataEnhanced(dataType: String, data: String): String = {
    if (data.isEmpty || data == "0x") return ""
    
    // Check if it's a tuple type
    if (dataType.trim.startsWith("(") && dataType.trim.endsWith(")")) {
      decodeTupleResult(data, dataType)
    } else {
      // Use existing method for non-tuple types
      Solidity.decodeData(dataType, data).get
    }
  }

  // ==========================================================================================================
  // Type System and Parsing
  // ==========================================================================================================

  /**
   * Represents a Solidity type in the ABI
   */
  sealed trait Type {
    def isDynamic: Boolean
    def staticSize: Int // Size in bytes for static types, 0 for dynamic
  }

  case class PrimitiveType(name: String) extends Type {
    def isDynamic: Boolean = name match {
      case "string" | "bytes" => true
      case _ => false
    }
    
    def staticSize: Int = name match {
      case "bool" | "boolean" => 32
      case "address" => 32
      case "bytes32" => 32
      case t if t.startsWith("uint") || t.startsWith("int") => 32
      case t if t.startsWith("bytes") && t != "bytes" => 32
      case "string" | "bytes" => 0 // Dynamic
      case _ => 32 // Default to 32 for unknown types
    }
  }

  case class ArrayType(baseType: Type, size: Option[Int]) extends Type {
    def isDynamic: Boolean = size.isEmpty || baseType.isDynamic
    
    def staticSize: Int = size match {
      case Some(n) if !baseType.isDynamic => n * baseType.staticSize
      case _ => 0 // Dynamic array or dynamic base type
    }
  }

  case class TupleType(components: List[Type]) extends Type {
    def isDynamic: Boolean = components.exists(_.isDynamic)
    
    def staticSize: Int = if (isDynamic) 0 else components.map(_.staticSize).sum
  }

  /**
   * Parse a type string into a Type object
   */
  private def parseType(typeStr: String): Type = {
    val trimmed = typeStr.trim
    
    // Handle arrays
    if (trimmed.endsWith("[]")) {
      val baseTypeStr = trimmed.dropRight(2)
      ArrayType(parseType(baseTypeStr), None) // Dynamic array
    } else if (trimmed.contains("[") && trimmed.contains("]")) {
      // Static array
      val arrayMatch = """(.+)\[(\d+)\]""".r
      trimmed match {
        case arrayMatch(baseTypeStr, sizeStr) =>
          ArrayType(parseType(baseTypeStr), Some(sizeStr.toInt))
        case _ =>
          throw new IllegalArgumentException(s"Invalid array format: $trimmed")
      }
    } else if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
      // Tuple type
      parseTupleType(trimmed)
    } else {
      // Primitive type
      PrimitiveType(trimmed)
    }
  }

  /**
   * Parse a tuple type string into a TupleType
   */
  private def parseTupleType(tupleStr: String): TupleType = {
    // Remove outer parentheses
    val content = tupleStr.substring(1, tupleStr.length - 1)
    
    if (content.trim.isEmpty) {
      TupleType(Nil) // Empty tuple
    } else {
      val components = parseTupleComponents(content)
      TupleType(components)
    }
  }

  /**
   * Parse tuple components, handling nested tuples and arrays
   */
  private def parseTupleComponents(content: String): List[Type] = {
    var components = List.empty[Type]
    var current = ""
    var parenDepth = 0
    var bracketDepth = 0
    
    for (char <- content) {
      char match {
        case '(' =>
          parenDepth += 1
          current += char
        case ')' =>
          parenDepth -= 1
          current += char
        case '[' =>
          bracketDepth += 1
          current += char
        case ']' =>
          bracketDepth -= 1
          current += char
        case ',' if parenDepth == 0 && bracketDepth == 0 =>
          // End of component
          if (current.trim.nonEmpty) {
            components = components :+ parseType(current.trim)
          }
          current = ""
        case _ =>
          current += char
      }
    }
    
    // Add the last component
    if (current.trim.nonEmpty) {
      components = components :+ parseType(current.trim)
    }
    
    components
  }

  // ==========================================================================================================
  // Core Decoding Logic
  // ==========================================================================================================

  /**
   * Decode a value of the given type from the byte array starting at the given offset
   * Returns (decoded_value, next_offset)
   */
  private def decodeValue(data: Array[Byte], offset: Int, valueType: Type): (String, Int) = {
    valueType match {
      case PrimitiveType(name) => decodePrimitive(data, offset, name)
      case ArrayType(baseType, size) => decodeArray(data, offset, baseType, size)
      case TupleType(components) => decodeTuple(data, offset, components)
    }
  }

  /**
   * Decode a primitive type
   */
  private def decodePrimitive(data: Array[Byte], offset: Int, typeName: String): (String, Int) = {
    typeName match {
      case "bool" | "boolean" =>
        val value = bytesToUint256(data, offset) != 0
        (value.toString, offset + 32)
        
      case "address" =>
        val addressBytes = data.slice(offset + 12, offset + 32) // Address is 20 bytes, padded to 32
        val address = "0x" + bytesToHex(addressBytes)
        (address, offset + 32)
        
      case "string" =>
        decodeDynamicString(data, offset)
        
      case "bytes" =>
        decodeDynamicBytes(data, offset)
        
      case "bytes32" =>
        val bytes = data.slice(offset, offset + 32)
        (s"0x${bytesToHex(bytes)}", offset + 32)
        
      case t if t.startsWith("uint") =>
        val value = bytesToUint256(data, offset)
        (value.toString, offset + 32)
        
      case t if t.startsWith("int") =>
        val value = bytesToInt256(data, offset)
        (value.toString, offset + 32)
        
      case _ =>
        throw new IllegalArgumentException(s"Unsupported primitive type: $typeName")
    }
  }

  /**
   * Decode a dynamic string
   */
  private def decodeDynamicString(data: Array[Byte], offset: Int): (String, Int) = {
    // Read offset to string data
    val stringOffset = bytesToUint256(data, offset).toInt
    
    // Read string length
    val stringLength = bytesToUint256(data, stringOffset).toInt
    
    // Read string data
    val stringData = data.slice(stringOffset + 32, stringOffset + 32 + stringLength)
    val stringValue = new String(stringData, "UTF-8")
    
    (s"\"$stringValue\"", offset + 32)
  }

  /**
   * Decode dynamic bytes
   */
  private def decodeDynamicBytes(data: Array[Byte], offset: Int): (String, Int) = {
    // Read offset to bytes data
    val bytesOffset = bytesToUint256(data, offset).toInt
    
    // Read bytes length
    val bytesLength = bytesToUint256(data, bytesOffset).toInt
    
    // Read bytes data
    val bytesData = data.slice(bytesOffset + 32, bytesOffset + 32 + bytesLength)
    
    (s"0x${bytesToHex(bytesData)}", offset + 32)
  }

  /**
   * Decode an array
   */
  private def decodeArray(data: Array[Byte], offset: Int, baseType: Type, size: Option[Int]): (String, Int) = {
    size match {
      case Some(n) =>
        // Static array
        decodeStaticArray(data, offset, baseType, n)
      case None =>
        // Dynamic array
        decodeDynamicArray(data, offset, baseType)
    }
  }

  /**
   * Decode a static array
   */
  private def decodeStaticArray(data: Array[Byte], offset: Int, baseType: Type, size: Int): (String, Int) = {
    var currentOffset = offset
    var elements = List.empty[String]
    
    for (_ <- 0 until size) {
      val (element, newOffset) = decodeValue(data, currentOffset, baseType)
      elements = elements :+ element
      currentOffset = newOffset
    }
    
    (s"[${elements.mkString(",")}]", currentOffset)
  }

  /**
   * Decode a dynamic array
   */
  private def decodeDynamicArray(data: Array[Byte], offset: Int, baseType: Type): (String, Int) = {
    // Read offset to array data
    val arrayOffset = bytesToUint256(data, offset).toInt
    
    // Read array length
    val arrayLength = bytesToUint256(data, arrayOffset).toInt
    
    var elements = List.empty[String]
    
    if (baseType.isDynamic) {
      // Dynamic base type: read offset table, then decode each element
      var currentOffset = arrayOffset + 32 // start after length
      
      for (_ <- 0 until arrayLength) {
        // Read offset to element
        val elementOffset = bytesToUint256(data, currentOffset).toInt
        
        // Decode element at its offset
        val (element, _) = decodeValue(data, elementOffset, baseType)
        elements = elements :+ element
        currentOffset += 32 // move to next offset
      }
    } else {
      // Static base type: elements follow directly after length
      var currentOffset = arrayOffset + 32 // start after length
      
      for (_ <- 0 until arrayLength) {
        val (element, newOffset) = decodeValue(data, currentOffset, baseType)
        elements = elements :+ element
        currentOffset = newOffset
      }
    }
    
    (s"[${elements.mkString(",")}]", offset + 32)
  }

  /**
   * Decode a tuple
   */
  private def decodeTuple(data: Array[Byte], offset: Int, components: List[Type]): (String, Int) = {
    if (components.isEmpty) {
      ("()", offset)
    } else {
      var currentOffset = offset
      var elements = List.empty[String]
      
      for (component <- components) {
        if (component.isDynamic) {
          // Dynamic component: read offset from static section
          val elementOffset = bytesToUint256(data, currentOffset).toInt
          val (element, _) = decodeValue(data, elementOffset, component)
          elements = elements :+ element
          currentOffset += 32
        } else {
          // Static component: decode in place
          val (element, newOffset) = decodeValue(data, currentOffset, component)
          elements = elements :+ element
          currentOffset = newOffset
        }
      }
      
      (s"(${elements.mkString(",")})", currentOffset)
    }
  }

  // ==========================================================================================================
  // Utility Functions
  // ==========================================================================================================

  /**
   * Convert hex string to byte array
   */
  private def hexStringToByteArray(hex: String): Array[Byte] = {
    if (hex.length % 2 != 0) {
      throw new IllegalArgumentException("Hex string must have even length")
    }
    
    hex.sliding(2, 2).map(Integer.parseInt(_, 16).toByte).toArray
  }

  /**
   * Convert byte array to hex string
   */
  private def bytesToHex(bytes: Array[Byte]): String = {
    bytes.map(b => f"${b & 0xff}%02x").mkString
  }

  /**
   * Convert byte array to hex string with optional length limit for debugging
   */
  private def bytesToHexDebug(bytes: Array[Byte], limit: Option[Int] = None): String = {
    val limitedBytes = limit.map(bytes.take).getOrElse(bytes)
    val hex = bytesToHex(limitedBytes)
    if (limit.isDefined && bytes.length > limit.get) {
      s"${hex}... (${bytes.length} bytes total)"
    } else {
      hex
    }
  }

  /**
   * Convert 32 bytes to uint256 (unsigned 256-bit integer)
   */
  private def bytesToUint256(bytes: Array[Byte], offset: Int = 0): BigInt = {
    val slice = bytes.slice(offset, offset + 32)
    BigInt(1, slice) // Positive big integer
  }

  /**
   * Convert 32 bytes to int256 (signed 256-bit integer)
   */
  private def bytesToInt256(bytes: Array[Byte], offset: Int = 0): BigInt = {
    val slice = bytes.slice(offset, offset + 32)
    val unsigned = BigInt(1, slice)
    
    // Handle negative numbers (two's complement)
    if (unsigned.testBit(255)) {
      // Negative number - convert from two's complement
      unsigned - BigInt(2).pow(256)
    } else {
      unsigned
    }
  }

  /**
   * Debug helper: print byte array as hex with optional limit
   */
  private def debugBytes(bytes: Array[Byte], limit: Option[Int] = None): String = {
    bytesToHexDebug(bytes, limit)
  }
} 