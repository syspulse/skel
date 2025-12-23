package io.syspulse.skel.crypto.eth

import scala.jdk.CollectionConverters._
import com.typesafe.scalalogging.Logger
import scala.util.{Try,Success,Failure}
import scala.collection.mutable

import io.syspulse.skel.util.Util
import io.syspulse.skel.crypto.Hash
import net.osslabz.evm.abi.decoder.AbiDecoder
import java.io.StringBufferInputStream
import net.osslabz.evm.abi.definition.AbiDefinition
import org.web3j.abi.datatypes.Type

object SolidityParser {
  val DEFAULT = """

event Transfer(address indexed from, address indexed to, uint256 value);
error Unauthorized();
"""
  
  val EVENT_NAME = "event"
  val ERROR_NAME = "error"

  def parseEvents(events:String):Seq[SolidityEvent] = {
    events.split("\n")
      .filter(s => s.trim().nonEmpty && s.trim().startsWith(EVENT_NAME))
      .map(s => {
        val sig = parseSignature(s,EVENT_NAME)
        new SolidityEvent(sig)
      })
      .toSeq
  }

  def parseErrors(errors:String):Seq[SolidityError] = {
    errors.split("\n")
      .filter(s => s.trim().nonEmpty && s.trim().startsWith(ERROR_NAME))
      .map(s => {        
        val sig = parseSignature(s,ERROR_NAME)
        new SolidityError(sig)
      })
      .toSeq
  }
  
  private def parseSignature(line: String,prefix:String): String = {
    // Remove prefix
    val without = line.trim().substring(prefix.size).trim()
    
    // Find the opening parenthesis
    val parenIndex = without.indexOf('(')
    if (parenIndex == -1) {
      // No parameters, just return the event name with empty parentheses
      return without.replace(";", "").trim() + "()"
    }
    
    val name = without.substring(0, parenIndex).trim()
    val paramsPart = without.substring(parenIndex + 1, without.lastIndexOf(')'))
    
    if (paramsPart.trim().isEmpty) {
      // No parameters
      return name + "()"
    }
    
    // Parse parameters
    val params = parseParameters(paramsPart)
    val paramTypes = params.map(cleanParameter).filter(_.nonEmpty)
    
    if (paramTypes.isEmpty) {
      name + "()"
    } else {
      s"$name(${paramTypes.mkString(",")})"
    }
  }
  
  private def parseParameters(paramsString: String): Array[String] = {
    // Split by comma, but be careful about nested parentheses and brackets
    val params = mutable.ArrayBuffer[String]()
    var current = ""
    var parenCount = 0
    var bracketCount = 0
    
    for (char <- paramsString) {
      char match {
        case '(' => 
          parenCount += 1
          current += char
        case ')' => 
          parenCount -= 1
          current += char
        case '[' => 
          bracketCount += 1
          current += char
        case ']' => 
          bracketCount -= 1
          current += char
        case ',' if parenCount == 0 && bracketCount == 0 =>
          params += current.trim()
          current = ""
        case _ => 
          current += char
      }
    }
    
    if (current.trim().nonEmpty) {
      params += current.trim()
    }
    
    params.toArray
  }
  
  private def cleanParameter(param: String): String = {
    // Remove parameter name and "indexed" keyword
    val parts = param.trim().split("\\s+")
    
    // Filter out "indexed" keyword and parameter names
    val filteredParts = parts.filter { part =>
      part != "indexed" && 
      // Keep only type parts (not parameter names)
      !isParameterName(part, parts)
    }
    
    filteredParts.mkString(" ").trim()
  }
  
  private def isParameterName(part: String, allParts: Array[String]): Boolean = {
    // In Solidity parameter syntax, the last part is always the parameter name
    // Everything before it is the type
    // But only if there are multiple parts (more than just the type)
    allParts.length > 1 && allParts.last == part
  }

  // --- Functions ---------------------------------------------------------------------
  def parseFunctionsFromAbi(abi:String):Seq[SolidityFunc] = {
    val decoder = new AbiDecoder(new StringBufferInputStream(abi))
    
    // Get all method signatures and examine the func parameter
    val methodSignatures = decoder.getMethodSignatures.asScala
    
    methodSignatures
      .filter { case(sig, func) =>
        func.getType().name() == "function"
      }
      .map { case(sig, func) =>
        Try {
          val name = func.getName()
          val inputTypes = func.getInputs.asScala.map(_.getType)
          // Build clean signature
          val signature = func.formatSignature()
          val sigHex = Util.hex(func.encodeSignature())

          new SolidityFunc(signature, decoder, Some(sigHex))
        }
      }
      .filter(_.isSuccess)
      .map(_.get)
      .toSeq
  }
}
