package io.syspulse.skel.blockchain.tron

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.{Duration,FiniteDuration}
import com.typesafe.scalalogging.Logger

import java.security.MessageDigest

import io.syspulse.skel.util.Util

object TronUtil {
  private val Base58Alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

  private def base58CheckEncode(input: Array[Byte]): String = {
    // Double SHA-256 for checksum
    val checksum = Util.SHA256(Util.SHA256(input)).take(4)
    val dataWithChecksum = input ++ checksum

    val bigInt = BigInt(1, dataWithChecksum)
    val base58 = new StringBuilder
    var value = bigInt
    while (value > 0) {
      val mod = (value % 58).toInt
      base58.insert(0, Base58Alphabet(mod))
      value /= 58
    }

    // Preserve leading zeros in Base58 format
    val leadingZeros = input.takeWhile(_ == 0.toByte).length
    ("1" * leadingZeros) + base58.toString()
  }

  private def base58CheckDecode(input: String): Array[Byte] = {
    // Convert Base58 to BigInt
    val bigInt = input.foldLeft(BigInt(0)) { (acc, char) =>
      acc * 58 + Base58Alphabet.indexOf(char)
    }

    val bytes = bigInt.toByteArray.dropWhile(_ == 0)

    // Add leading zeros stripped during decoding
    val leadingZeros = input.takeWhile(_ == '1').length
    val decodedWithChecksum = Array.fill(leadingZeros)(0.toByte) ++ bytes

    // Verify checksum
    val payload = decodedWithChecksum.dropRight(4)
    val checksum = decodedWithChecksum.takeRight(4)
    val expectedChecksum = Util.SHA256(Util.SHA256(payload)).take(4)
    require(checksum sameElements expectedChecksum, "Invalid Base58Check checksum")

    payload
  }

  def ethToTron(ethAddress: String): String = {
    require(ethAddress.startsWith("0x") && ethAddress.length == 42, "Invalid Ethereum address format")

    //val ethAddressBytes = BigInt(ethAddress.substring(2), 16).toByteArray.dropWhile(_ == 0)
    val ethAddressBytes = Util.fromHexString(ethAddress)

    // Add Tron MainNet identifier (0x41)
    //val tronAddressBytes = Array(0x41.toByte) ++ ethAddressBytes
    val tronAddressBytes = 0x41.toByte +: ethAddressBytes
    base58CheckEncode(tronAddressBytes)
  }
  
  def tronToEth(tronAddress: String): String = {    
    val tronAddressBytes = base58CheckDecode(tronAddress)
    require(tronAddressBytes.head == 0x41.toByte, "Invalid Tron address format")

    // Remove the first byte and convert to Hex with "0x" prefix
    //"0x" + tronAddressBytes.tail.map("%02x".format(_)).mkString
    Util.hex(tronAddressBytes.drop(1))
  }  
}
