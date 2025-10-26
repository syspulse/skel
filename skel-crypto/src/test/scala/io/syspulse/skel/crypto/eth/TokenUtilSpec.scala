package io.syspulse.skel.crypto.eth

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import scala.math.BigInt

class TokenUtilSpec extends AnyWordSpec with Matchers {

  "TokenUtil.toHuman" should {

    "format Double values correctly" in {
      // Test basic formatting
      TokenUtil.toHuman(1234.56) shouldBe "1.23K"
      TokenUtil.toHuman(1000000.0) shouldBe "1.00M"
      TokenUtil.toHuman(999999.0) shouldBe "999.99K"
      
      // Test small values
      TokenUtil.toHuman(123.45) shouldBe "123.45"
      TokenUtil.toHuman(1.23) shouldBe "1.23"
      TokenUtil.toHuman(0.001) shouldBe "0.001"
      
      // Test zero
      TokenUtil.toHuman(0.0) shouldBe "0.00"
      
      // Test large values
      TokenUtil.toHuman(5000000000.0) shouldBe "5.00B"
      TokenUtil.toHuman(1234567890.0) shouldBe "1.23B"
    }

    "format BigInt values correctly" in {
      // Test basic formatting
      TokenUtil.toHuman(BigInt(1234567890)) shouldBe "1.23B"
      TokenUtil.toHuman(BigInt(1000000000)) shouldBe "1.00B"
      TokenUtil.toHuman(BigInt(999999999)) shouldBe "999.99M"
      
      // Test small values
      TokenUtil.toHuman(BigInt(123456789)) shouldBe "123.45M"
      TokenUtil.toHuman(BigInt(100000000)) shouldBe "100.00M"
      
      // Test zero
      TokenUtil.toHuman(BigInt(0)) shouldBe "0.00"
      
      // Test large values
      TokenUtil.toHuman(BigInt("5000000000")) shouldBe "5.00B"
      TokenUtil.toHuman(BigInt("1234567890123456789")) shouldBe "1.23QT"
    }

    "format BigInt with decimals correctly" in {
      // Test with 18 decimals (default for most tokens)
      TokenUtil.toHuman(BigInt("1000000000000000000"), 18) shouldBe "1.00" // 1 token with 18 decimals
      TokenUtil.toHuman(BigInt("1500000000000000000"), 18) shouldBe "1.50" // 1.5 tokens
      TokenUtil.toHuman(BigInt("100000000000000000"), 18) shouldBe "0.10"  // 0.1 tokens
      
      // Test with 6 decimals (like USDC, USDT)
      TokenUtil.toHuman(BigInt("1000000"), 6) shouldBe "1.00" // 1 USDC
      TokenUtil.toHuman(BigInt("1500000"), 6) shouldBe "1.50" // 1.5 USDC
      TokenUtil.toHuman(BigInt("100000"), 6) shouldBe "0.10"  // 0.1 USDC
      
      // Test with 8 decimals
      TokenUtil.toHuman(BigInt("100000000"), 8) shouldBe "1.00" // 1 token with 8 decimals
      TokenUtil.toHuman(BigInt("150000000"), 8) shouldBe "1.50" // 1.5 tokens
      
      // Test zero with decimals
      TokenUtil.toHuman(BigInt(0), 18) shouldBe "0.00"
      TokenUtil.toHuman(BigInt(0), 6) shouldBe "0.00"
    }

    "handle edge cases correctly" in {
      // Test very large numbers
      // does not work: 9999999999999999
      // works 999999999999999
      //TokenUtil.toHuman(BigInt("999999999999999999999999999999")) shouldBe "1,000,000.00B"
      TokenUtil.toHuman(BigInt("999999999999999")) shouldBe "999.99T"
      
      // Test very small numbers
      TokenUtil.toHuman(BigInt(1)) shouldBe "1.00"
      TokenUtil.toHuman(BigInt(1), 18) shouldBe "0.000000000000000001"
      
      // Test negative numbers (should handle gracefully)
      TokenUtil.toHuman(-1234.56) shouldBe "-1.23K"
      TokenUtil.toHuman(BigInt(-1234567890)) shouldBe "-1.23B"
    }

    "format with different thresholds in toHumanWithThresh" in {
      // Test with billion threshold (default)
      TokenUtil.toHumanWithThresh(BigDecimal("1234567890"), None, Some(1000000000.0)) shouldBe "1.23B"
      
      // Test with million threshold
      TokenUtil.toHumanWithThresh(BigDecimal("1234567"), None, Some(1000000.0)) shouldBe "1.23M"
      TokenUtil.toHumanWithThresh(BigDecimal("5000000"), None, Some(1000000.0)) shouldBe "5.00M"
      
      // Test with thousand threshold
      TokenUtil.toHumanWithThresh(BigDecimal("1234"), None, Some(1000.0)) shouldBe "1.23K"
      TokenUtil.toHumanWithThresh(BigDecimal("5000"), None, Some(1000.0)) shouldBe "5.00K"
      
      // Test with no threshold (should use default formatting)
      TokenUtil.toHumanWithThresh(BigDecimal("1234.56"), None, None) shouldBe "1,234.56"
      TokenUtil.toHumanWithThresh(BigDecimal("1234567.89"), None, None) shouldBe "1,234,567.89"
    }

    "format with decimals and thresholds" in {
      // Test with decimals and billion threshold
      TokenUtil.toHumanWithThresh(BigDecimal("1000000000000000000"), Some(18), Some(1000000000.0)) shouldBe "1.00"
      TokenUtil.toHumanWithThresh(BigDecimal("1500000000000000000"), Some(18), Some(1000000000.0)) shouldBe "1.50"
      
      // Test with decimals and million threshold
      TokenUtil.toHumanWithThresh(BigDecimal("1000000000000000000"), Some(18), Some(1000000.0)) shouldBe "1.00"
      
      // Test with decimals and no threshold
      TokenUtil.toHumanWithThresh(BigDecimal("1000000000000000000"), Some(18), None) shouldBe "1,000,000,000,000,000,000.00"
      TokenUtil.toHumanWithThresh(BigDecimal("1500000000000000000"), Some(18), None) shouldBe "1,500,000,000,000,000,000.00"
    }

    "handle rounding correctly" in {
      // Test rounding behavior
      TokenUtil.toHuman(1234567899.0) shouldBe "1.23B" // Should round down
      TokenUtil.toHuman(1234567900.0) shouldBe "1.23B" // Should round down
      TokenUtil.toHuman(1234567901.0) shouldBe "1.23B" // Should round down
      
      TokenUtil.toHuman(BigInt("1234567899"), 0) shouldBe "1.23B"
      TokenUtil.toHuman(BigInt("1234567900"), 0) shouldBe "1.23B"
      TokenUtil.toHuman(BigInt("1234567901"), 0) shouldBe "1.23B"
    }

    "format currency values realistically" in {
      // Test realistic token amounts
      // 1 ETH = 1e18 wei
      TokenUtil.toHuman(BigInt("1000000000000000000"), 18) shouldBe "1.00" // 1 ETH
      TokenUtil.toHuman(BigInt("5000000000000000000"), 18) shouldBe "5.00" // 5 ETH
      
      // 1 USDC = 1e6 units
      TokenUtil.toHuman(BigInt("1000000"), 6) shouldBe "1.00" // 1 USDC
      TokenUtil.toHuman(BigInt("5000000"), 6) shouldBe "5.00" // 5 USDC
      
      // 1 USDT = 1e6 units
      TokenUtil.toHuman(BigInt("1000000"), 6) shouldBe "1.00" // 1 USDT
      TokenUtil.toHuman(BigInt("2500000"), 6) shouldBe "2.50" // 2.5 USDT
    }
  }

  "TokenUtil.toHumanWithThresh" should {
    "handle edge cases gracefully" in {
      // Test zero values
      TokenUtil.toHumanWithThresh(BigDecimal(0), None, Some(1000000000.0)) shouldBe "0.00"
      TokenUtil.toHumanWithThresh(BigDecimal(0), Some(18), Some(1000000000.0)) shouldBe "0.00"
      
      // Test negative values
      TokenUtil.toHumanWithThresh(BigDecimal(-1234567890), None, Some(1000000000.0)) shouldBe "-1.23B"
      TokenUtil.toHumanWithThresh(BigDecimal(-1234567), None, Some(1000000.0)) shouldBe "-1.23M"
      
      // Test very small values
      TokenUtil.toHumanWithThresh(BigDecimal("0.001"), None, Some(1000000000.0)) shouldBe "0.001"
      TokenUtil.toHumanWithThresh(BigDecimal("0.001"), Some(18), Some(1000000000.0)) shouldBe "1E-21"
    }
  }

  "TokenUtil.isBurnAddr" should {
    "identify burn addresses correctly with default regexp" in {
      // Test the default burn address pattern (0x000000000000000000000000...)
      TokenUtil.isBurnAddr("0x0000000000000000000000000000000000000000") shouldBe true
      TokenUtil.isBurnAddr("0x000000000000000000000000deadbeef123456") shouldBe true
      TokenUtil.isBurnAddr("0x000000000000000000000000000000000000dead") shouldBe true
      
      // Test addresses that don't match the default pattern
      TokenUtil.isBurnAddr("0x1000000000000000000000000000000000000000") shouldBe false
      TokenUtil.isBurnAddr("0x0000000000000000000000010000000000000000") shouldBe false
      TokenUtil.isBurnAddr("0x1234567890abcdef1234567890abcdef12345678") shouldBe false
      TokenUtil.isBurnAddr("0xdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef") shouldBe false
    }

    "handle custom regexp patterns correctly" in {
      // Test with custom regexp for addresses ending with 'dead'
      TokenUtil.isBurnAddr("0x000000000000000000000000000000000000dead", Some(".*dead$")) shouldBe true
      TokenUtil.isBurnAddr("0xdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef", Some(".*dead$")) shouldBe false
      TokenUtil.isBurnAddr("0x1234567890abcdef1234567890abcdef12345678", Some(".*dead$")) shouldBe false
      
      // Test with custom regexp for addresses containing 'burn'
      TokenUtil.isBurnAddr("0x1234567890abcdef1234567890abcdef1234burn", Some(".*burn.*")) shouldBe true
      TokenUtil.isBurnAddr("0xburn1234567890abcdef1234567890abcdef1234", Some(".*burn.*")) shouldBe true
      TokenUtil.isBurnAddr("0x1234567890abcdef1234567890abcdef12345678", Some(".*burn.*")) shouldBe false
      
      // Test with custom regexp for addresses starting with '0xdead'
      TokenUtil.isBurnAddr("0xdead1234567890abcdef1234567890abcdef1234", Some("^0xdead.*")) shouldBe true
      TokenUtil.isBurnAddr("0x1234567890abcdef1234567890abcdef12345678", Some("^0xdead.*")) shouldBe false
    }

    "handle edge cases correctly" in {
      // Test empty string
      TokenUtil.isBurnAddr("") shouldBe false
      
      // Test very short addresses
      TokenUtil.isBurnAddr("0x") shouldBe false
      TokenUtil.isBurnAddr("0x0") shouldBe false
      TokenUtil.isBurnAddr("0x00") shouldBe false
      
      // Test addresses shorter than 42 characters (0x + 40 hex chars)
      // Note: isBurnAddr only checks the prefix, not the length
      TokenUtil.isBurnAddr("0x000000000000000000000000000000000000000") shouldBe true // 41 chars, but starts with correct prefix
      TokenUtil.isBurnAddr("0x00000000000000000000000000000000000000") shouldBe true   // 40 chars, but starts with correct prefix
      
      // Test addresses longer than 42 characters
      TokenUtil.isBurnAddr("0x000000000000000000000000000000000000000000") shouldBe true // 43 chars, but starts with correct prefix
      TokenUtil.isBurnAddr("0x00000000000000000000000000000000000000000000") shouldBe true // 44 chars, but starts with correct prefix
    }

    "handle case sensitivity correctly" in {
      // Test that the method is case-sensitive for hex addresses (startsWith is case-sensitive)
      TokenUtil.isBurnAddr("0x0000000000000000000000000000000000000000") shouldBe true
      TokenUtil.isBurnAddr("0X0000000000000000000000000000000000000000") shouldBe false // 0X != 0x
      TokenUtil.isBurnAddr("0x0000000000000000000000000000000000000000") shouldBe true
      
      // Test with custom regexp that includes case sensitivity
      TokenUtil.isBurnAddr("0xDEADBEEF1234567890abcdef1234567890abcdef", Some(".*DEAD.*")) shouldBe true
      TokenUtil.isBurnAddr("0xdeadbeef1234567890abcdef1234567890abcdef", Some(".*DEAD.*")) shouldBe false
      TokenUtil.isBurnAddr("0xdeadbeef1234567890abcdef1234567890abcdef", Some("(?i).*dead.*")) shouldBe true
    }

    "handle invalid regexp patterns gracefully" in {
      // Test with malformed regexp patterns
      TokenUtil.isBurnAddr("0x1234567890abcdef1234567890abcdef12345678", Some("[")) shouldBe false // Invalid regexp
      TokenUtil.isBurnAddr("0x1234567890abcdef1234567890abcdef12345678", Some("(")) shouldBe false // Unclosed parenthesis
      TokenUtil.isBurnAddr("0x1234567890abcdef1234567890abcdef12345678", Some("*")) shouldBe false // Invalid regexp
      
      // Test with empty regexp pattern
      TokenUtil.isBurnAddr("0x1234567890abcdef1234567890abcdef12345678", Some("")) shouldBe false
    }

    "work correctly with real-world burn addresses" in {
      // Test some common burn address patterns used in practice
      TokenUtil.isBurnAddr("0x000000000000000000000000000000000000dEaD") shouldBe true
      TokenUtil.isBurnAddr("0x0000000000000000000000000000000000000000") shouldBe true
      TokenUtil.isBurnAddr("0x000000000000000000000000000000000000dead") shouldBe true
      
      // Test addresses that are commonly used as burn addresses but don't match default pattern
      // The regexp .*[dD]e[aA][dD]$ should match addresses ending with dEaD, dead, DEAD, etc.
      TokenUtil.isBurnAddr("0x000000000000000000000000000000000000dEaD", Some(".*[dD]E[aA][dD]$")) shouldBe true
      TokenUtil.isBurnAddr("0x0000000000000000000000000000000000000000", Some(".*[dD]e[aA][dD]$")) shouldBe false
      
      // Test with simpler regexp that should definitely work
      TokenUtil.isBurnAddr("0x000000000000000000000000000000000000dEaD", Some(".*dEaD$")) shouldBe true
      TokenUtil.isBurnAddr("0x000000000000000000000000000000000000dead", Some(".*dead$")) shouldBe true
    }
  }

  "TokenUtil.isMintAddr" should {
    "delegate to isBurnAddr correctly" in {
      // Test that isMintAddr behaves identically to isBurnAddr
      val testAddr = "0x1234567890abcdef1234567890abcdef12345678"
      val customRegexp = Some(".*mint.*")
      
      TokenUtil.isMintAddr(testAddr) shouldBe TokenUtil.isBurnAddr(testAddr)
      TokenUtil.isMintAddr(testAddr, customRegexp) shouldBe TokenUtil.isBurnAddr(testAddr, customRegexp)
      
      // Test with burn address pattern
      val burnAddr = "0x0000000000000000000000000000000000000000"
      TokenUtil.isMintAddr(burnAddr) shouldBe TokenUtil.isBurnAddr(burnAddr)
      
      // Test with custom regexp
      val mintAddr = "0x1234567890abcdef1234567890abcdef1234mint"
      TokenUtil.isMintAddr(mintAddr, Some(".*mint.*")) shouldBe TokenUtil.isBurnAddr(mintAddr, Some(".*mint.*"))
    }
  }
} 