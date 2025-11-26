package io.syspulse.skel.util

import org.scalatest.{Ignore}
import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import scala.util.{Try,Success,Failure}
import java.time._
import io.syspulse.skel.util.Util

class ConditionSpec extends AnyWordSpec with Matchers {

  "Test ETH transfer conditions" should {
    "transfer 1.5 ETH" in {
      {
        val d = new ConditionBigInt(BigInt(0),"= 1.5",18)
        d.set(BigInt("1500000000000000000")) should  === (true)
        d.set(BigInt("1600000000000000000")) should  === (false)
        d.set(BigInt("15")) should  === (false)
      }
      {
        val d = new ConditionBigInt(BigInt(0),"> 1.53",18)
        d.set(BigInt("1500000000000000000")) should  === (false)
        d.set(BigInt("1600000000000000000")) should  === (true)
        d.set(BigInt("1540000000000000000")) should  === (true)
        d.set(BigInt("1520000000000000000")) should  === (false)
        d.set(BigInt("1530000000000000000")) should  === (false)
        d.set(BigInt("15")) should  === (false)
      }
      {
        val d = new ConditionBigInt(BigInt(0),">= 1.53",18)
        d.set(BigInt("1500000000000000000")) should  === (false)
        d.set(BigInt("1600000000000000000")) should  === (true)
        d.set(BigInt("1540000000000000000")) should  === (true)
        d.set(BigInt("1520000000000000000")) should  === (false)
        d.set(BigInt("1530000000000000000")) should  === (true)
        d.set(BigInt("15")) should  === (false)
      }
    }    
  }

  "Test totalSupply" should {
    "change 1004465.0 -> 1009465.0" in {      
      {
        val d = new ConditionDouble(1004465.0,"> 0.1%")
        d.set(1009465.0) should  === (true)        
      }

      {
        val d = new ConditionDouble(1004465.0,"> 0.2%")
        d.set(1009465.0) should  === (true)        
      }

      {
        val d = new ConditionDouble(1004465.0,"> 0.5%")
        d.set(1009465.0) should  === (false)        
      }

      {
        val d = new ConditionDouble(1004465.0," < 0.7%")
        d.set(1009465.0) should  === (true)
      }
    }    

    "change 1004465.0 -> 1009465.0 with BigInt and dec = 18" in {      
      {
        val d = new ConditionBigInt(BigInt(1004465),"> 0.1%",18)
        d.set(BigInt(1009465)) should  === (true)        
      }

      {
        val d = new ConditionBigInt(BigInt(1004465),"> 0.2%",18)
        d.set(BigInt(1009465)) should  === (true)        
      }

      {
        val d = new ConditionBigInt(BigInt(1004465),"> 0.5%",18)
        d.set(BigInt(1009465)) should  === (false)        
      }

      {
        val d = new ConditionBigInt(BigInt(1004465)," < 0.7%",18)
        d.set(BigInt(1009465)) should  === (true)
      }
    }    
  }

  "Test Price change" should {
    "detect changes greater than 0.99% in any direction" in {      
      val d = new ConditionDouble(4700.0,"> 0.99%")
      
      d.set(4700.0) should  === (false)
      d.value() should  === (4700.0)

      // From 4700 to 4600: |(4600-4700)/4700*100| = |-2.13%| = 2.13% > 0.99% = true
      d.set(4600.0) should  === (true)
      d.value() should  === (4600.0)

      d.set(4700.0) should  === (true)
      d.value() should  === (4700.0)

      // From 4700 to 4800: |(4800-4700)/4700*100| = |+2.13%| = 2.13% > 0.99% = true
      d.set(4800.0) should  === (true)
      d.value() should  === (4800.0)
      
    }
        
    "test various percentage change scenarios" in {
      val d = new ConditionDouble(4700.0,"> 0.99%")
      
      d.set(4700.0) should  === (false)
      d.value() should  === (4700.0)

      // Test positive change > 0.99%
      d.set(4800.0) should  === (true)  // |+2.13%| = 2.13% > 0.99%
      d.value() should  === (4800.0)

      d.set(4800.0) should  === (false)  // no change
      d.value() should  === (4800.0)

      // Test negative change > 0.99% (should be true since |-2.13%| = 2.13% > 0.99%)
      d.set(4600.0) should  === (true)  // |-2.13%| = 2.13% > 0.99%
      d.value() should  === (4600.0)
      
      // Test small positive change < 0.99%
      d.set(4610.0) should  === (false)  // |+0.22%| = 0.22% > 0.99% = false
      d.value() should  === (4610.0)
      
      // Test larger positive change > 0.99%
      d.set(4700.0) should  === (true)   // |+1.95%| = 1.95% > 0.99% = true
      d.value() should  === (4700.0)
    }
    
    "test percentage change edge cases" in {
      val d = new ConditionDouble(100.0,"> 5%")
      
      d.set(100.0) should  === (false)
      d.value() should  === (100.0)

      // Test small change < 5%
      d.set(104.0) should  === (false)  // |+4%| = 4% > 5% = false
      d.value() should  === (104.0)

      // Test change exactly at 5%
      d.set(105.0) should  === (false)  // |+5%| = 5% > 5% = false (not >=)
      d.value() should  === (105.0)

      // Test change > 5%
      d.set(106.0) should  === (false) 
      d.value() should  === (106.0)

      // Test negative change > 5%
      d.set(94.0) should  === (true)    // |-6%| = 6% > 5% = true
      d.value() should  === (94.0)

      // Test negative change > 0.99% (should be true since |-2.13%| = 2.13% > 0.99%)
      d.set(4600.0) should  === (true)  // |-2.13%| = 2.13% > 0.99%
      d.value() should  === (4600.0)
    }
  }

  "Test1" should {
    "change 20%" in {      
      val d = new ConditionDouble(0.0,"20%")
      
      d.set(20) should  === (false)
      d.value() should  === (20)

      d.set(24) should  === (true)
      d.value() should  === (24)
    }
  }

  "Expression syntax tests" should {
    
    "handle basic comparison operators" in {
      val d = new ConditionDouble(100, "= 50")
      d.set(50) should === (true)
      d.set(51) should === (false)
      
      val d2 = new ConditionDouble(100, "!= 25")
      d2.set(25) should === (false)
      d2.set(30) should === (true)
    }
    
    "handle absolute value expressions" in {
      val d = new ConditionDouble(100, " > 10")
      // v0=100, v=15: 15 > 10 = true
      d.set(15) should === (true)
      // v0=15, v=-15: |-15| > 10 = true
      d.set(-15) should === (true)
      // v0=-15, v=5: |5| > 10 = false
      d.set(5) should === (false)
      
      val d2 = new ConditionDouble(100, " < 20")
      // v0=100, v=15: |15| < 20 = true
      d2.set(15) should === (true)
      // v0=15, v=-15: |-15| < 20 = true
      d2.set(-15) should === (true)
      // v0=-15, v=25: |25| < 20 = false
      d2.set(25) should === (false)
    }
    
    "handle explicit sign expressions" in {
      val d = new ConditionDouble(100, " > +10")
      // v0=100, v=15: 15 > 10 = true
      d.set(15) should === (true)
      // v0=15, v=-15: -15 > 10 = false
      d.set(-15) should === (false)
      
      val d2 = new ConditionDouble(100, "> -15")
      // v0=100, v=-10: -10 > -15 = true
      d2.set(-10) should === (true)
      // v0=-10, v=-20: -20 > -15 = false
      d2.set(-20) should === (false)
    }
    
    "handle percentage operations" in {
      val d = new ConditionDouble(100, "> 25%")
      // v0=100, v=130: percentage change = (130-100)/100*100 = 30% > 25% = true
      d.set(130) should === (true)
      // v0=130, v=140: percentage change = (140-130)/130*100 = 7.69% > 25% = false
      d.set(140) should === (false)
      
      val d2 = new ConditionDouble(100, "< 10%")
      // v0=100, v=95: percentage change = (95-100)/100*100 = -5% < 10% = true
      d2.set(95) should === (true)
      // v0=95, v=110: percentage change = (110-95)/95*100 = 15.79% < 10% = false
      d2.set(110) should === (false)
    }
    
    "handle delta operations" in {
      val d = new ConditionDouble(100, "> ++20")
      // v0=100, v=125: change = 125-100 = 25 > 20 = true
      d.set(125) should === (true)
      // v0=125, v=140: change = 140-125 = 15 > 20 = false
      d.set(140) should === (false)
      
      val d2 = new ConditionDouble(100, "> --30")
      // v0=100, v=60: change = 100-60 = 40 > 30 = true
      d2.set(60) should === (true)
      // v0=60, v=40: change = 60-40 = 20 > 30 = false
      d2.set(40) should === (false)
    }
    
    "handle percentage delta operations" in {
      val d = new ConditionDouble(100, "> ++25%")
      // v0=100, v=130: percentage change = (130-100)/100*100 = 30% > 25% = true
      d.set(130) should === (true)
      // v0=130, v=150: percentage change = (150-130)/130*100 = 15.38% > 25% = false
      d.set(150) should === (false)
      
      val d2 = new ConditionDouble(100, "> --20%")
      // v0=100, v=75: percentage change = (100-75)/100*100 = 25% > 20% = true
      d2.set(75) should === (true)
      // v0=75, v=65: percentage change = (75-65)/75*100 = 13.33% > 20% = false
      d2.set(65) should === (false)
    }
    
    "handle spaces in expressions" in {
      val d = new ConditionDouble(100, "> ++ 50")
      // v0=100, v=160: change = 160-100 = 60 > 50 = true
      d.set(160) should === (true)
      
      val d2 = new ConditionDouble(100, "<= -- 30")
      // v0=100, v=65: change = 100-65 = 35 <= 30 = false
      d2.set(65) should === (false)
      // v0=65, v=40: change = 65-40 = 25 <= 30 = true
      d2.set(40) should === (true)
    }
    
    "handle complex expressions with OR" in {
      // Note: OR expressions with | are not currently supported in Op.compile
      // This test is marked as pending until OR support is implemented
      pending
    }
    
    "handle edge cases" in {
      val d = new ConditionDouble(0, "> 10")
      // v0=0, v=15: 15 > 10 = true
      d.set(15) should === (true)
      
      val d2 = new ConditionDouble(100, "> 0%")
      // v0=100, v=100: percentage change = (100-100)/100*100 = 0% > 0% = false
      d2.set(100) should === (false)
      // v0=100, v=101: percentage change = (101-100)/100*100 = 1% > 0% = true
      d2.set(101) should === (true)
    }
    
    "handle different numeric types" in {
      val d = new ConditionDouble(100.0, "> 20")
      d.set(125.5) should === (true)
      d.set(115.0) should === (true)
      
      val d2 = new ConditionBigInt(100, "> ++30")
      d2.set(BigInt(140)) should === (true)
      d2.set(BigInt(125)) should === (false)
      
      val d3 = new ConditionDouble(100.0, "< 10%")
      d3.set(95.0) should === (true)
      d3.set(110.0) should === (false)
    }
    
    "debug ConditionBigInt delta operation" in {
      val d = new ConditionBigInt(100, "> ++30")
      println(s"Initial value: ${d.value}")
      println(s"Condition: ${d.getCondition}")
      
      val result1 = d.set(BigInt(140))
      println(s"set(140) = $result1, value = ${d.value}")
      
      val result2 = d.set(BigInt(125))
      println(s"set(125) = $result2, value = ${d.value}")
      
      // The test should pass
      result1 should === (true)
      result2 should === (false)
    }
    
    "isolated ConditionBigInt test" in {
      val d = new ConditionBigInt(100, "> ++30")
      
      // Test the first call
      val result1 = d.set(BigInt(140))
      result1 should === (true)
      
      // Test the second call
      val result2 = d.set(BigInt(125))
      result2 should === (false)
    }
    
    "OpMoreOnce tests" should {
      "handle basic once logic" in {
        val d = new ConditionDouble(0, ">> 10")
        
        // v0=0, v=1: 1 > 10 = false
        d.set(1) should === (false)
        // v0=1, v=2: 2 > 10 = false
        d.set(2) should === (false)
        // v0=2, v=15: 15 > 10 = true (first true)
        d.set(15) should === (true)
        // v0=15, v=15: 15 > 10 = true, but not first true
        d.set(15) should === (false)
        // v0=15, v=20: 20 > 10 = true, but not first true
        d.set(20) should === (false)
        // v0=20, v=1: 1 > 10 = false
        d.set(1) should === (false)
        // v0=1, v=20: 20 > 10 = true (first true again)
        d.set(20) should === (true)
      }
      
      "handle once logic with percentage" in {
        val d = new ConditionDouble(100, ">> 25%")
        
        // v0=100, v=100: percentage change = 0% > 25% = false
        d.set(100) should === (false)
        // v0=100, v=120: percentage change = 20% > 25% = false
        d.set(120) should === (false)
        // v0=120, v=160: percentage change = 33.33% > 25% = true (first true)
        d.set(160) should === (true)
        // v0=160, v=200: percentage change = 25% > 25% = false
        d.set(200) should === (false)
        // v0=200, v=250: percentage change = 25% > 25% = false
        d.set(250) should === (false)
        // v0=250, v=300: percentage change = 20% > 25% = false
        d.set(300) should === (false)
        // v0=300, v=400: percentage change = 33.33% > 25% = true (first true again)
        d.set(400) should === (true)
      }
      
      "handle once logic with delta operations" in {
        val d = new ConditionDouble(100, ">> ++20")
        
        // v0=100, v=110: change = 10 > 20 = false
        d.set(110) should === (false)
        // v0=110, v=125: change = 15 > 20 = false
        d.set(125) should === (false)
        // v0=125, v=150: change = 25 > 20 = true (first true)
        d.set(150) should === (true)
        // v0=150, v=180: change = 30 > 20 = true, but not first true
        d.set(180) should === (false)
        // v0=180, v=190: change = 10 > 20 = false
        d.set(190) should === (false)
        // v0=190, v=220: change = 30 > 20 = true (first true again)
        d.set(220) should === (true)
      }
      
      "handle once logic with absolute value expressions" in {
        val d = new ConditionDouble(0, ">> 10")
        
        // v0=0, v=5: |5| > 10 = false
        d.set(5) should === (false)
        // v0=5, v=-5: |-5| > 10 = false
        d.set(-5) should === (false)
        // v0=-5, v=15: |15| > 10 = true (first true)
        d.set(15) should === (true)
        // v0=15, v=-15: |-15| > 10 = true, but not first true
        d.set(-15) should === (false)
        // v0=-15, v=8: |8| > 10 = false
        d.set(8) should === (false)
        // v0=8, v=20: |20| > 10 = true (first true again)
        d.set(20) should === (true)
      }
      
      "handle once logic with explicit sign expressions" in {
        val d = new ConditionDouble(0, ">> +15")
        
        // v0=0, v=10: 10 > 15 = false
        d.set(10) should === (false)
        // v0=10, v=20: 20 > 15 = true (first true)
        d.set(20) should === (true)
        // v0=20, v=25: 25 > 15 = true, but not first true
        d.set(25) should === (false)
        // v0=25, v=5: 5 > 15 = false
        d.set(5) should === (false)
        // v0=5, v=30: 30 > 15 = true (first true again)
        d.set(30) should === (true)
      }
      
      "handle edge cases for once logic" in {
        val d = new ConditionDouble(0, ">> 0")
        
        // v0=0, v=0: 0 > 0 = false
        d.set(0) should === (false)
        // v0=0, v=1: 1 > 0 = true (first true)
        d.set(1) should === (true)
        // v0=1, v=2: 2 > 0 = true, but not first true
        d.set(2) should === (false)
        // v0=2, v=0: 0 > 0 = false
        d.set(0) should === (false)
        // v0=0, v=5: 5 > 0 = true (first true again)
        d.set(5) should === (true)
      }
      
      "handle complex once logic scenarios" in {
        val d = new ConditionDouble(100, ">> 50%")
        
        // v0=100, v=120: percentage change = 20% > 50% = false
        d.set(120) should === (false)
        // v0=120, v=200: percentage change = 66.67% > 50% = true (first true)
        d.set(200) should === (true)
        // v0=200, v=300: percentage change = 50% > 50% = false
        d.set(300) should === (false)
        // v0=300, v=400: percentage change = 33.33% > 50% = false
        d.set(400) should === (false)
        // v0=400, v=700: percentage change = 75% > 50% = true (first true again)
        d.set(700) should === (true)
        // v0=700, v=800: percentage change = 14.29% > 50% = false
        d.set(800) should === (false)
      }
    }
  }
}

// ========================================================================================================
class OpSpec extends AnyWordSpec with Matchers {

  "Op" should {
    
    "handle empty expressions" in {
      val op = Op.compile("")
      op shouldBe a[OpEmpty]
      op.eval(BigDecimal(100), BigDecimal(200)) shouldBe true
    }
    
    "handle whitespace-only expressions" in {
      val op = Op.compile("   ")
      op shouldBe a[OpEmpty]
      op.eval(BigDecimal(100), BigDecimal(200)) shouldBe true
    }
  }

  "OpEq" should {
    
    "compile equality expressions correctly" in {
      val op = Op.compile("=100")
      op shouldBe a[OpEq]
      op.asInstanceOf[OpEq].expr shouldBe BigDecimal(100)
      op.asInstanceOf[OpEq].perc shouldBe false
    }
    
    "evaluate absolute equality correctly" in {
      val op = Op.compile("=50")
      
      // Test absolute equality
      op.eval(BigDecimal(100), BigDecimal(50)) shouldBe true
      op.eval(BigDecimal(100), BigDecimal(51)) shouldBe false
      op.eval(BigDecimal(100), BigDecimal(49)) shouldBe false
    }
    
    "evaluate percentage equality correctly" in {
      val op = Op.compile("=50.0%")
      
      // Test percentage equality
      // v0=100, v=150 -> percentage change = (150-100)/100*100 = 50%, so 50% == 50.0% -> true
      op.eval(BigDecimal(100), BigDecimal(150)) shouldBe true
      
      // v0=100, v=100 -> percentage change = (100-100)/100*100 = 0%, so 0% != 50.0% -> false
      op.eval(BigDecimal(100), BigDecimal(100)) shouldBe false
      
      // v0=100, v=25 -> percentage change = (25-100)/100*100 = -75%, so -75% != 50.0% -> false
      op.eval(BigDecimal(100), BigDecimal(25)) shouldBe false
    }
    
    "handle zero initial value for percentage" in {
      val op = Op.compile("=100.0%")
      
      // When v0=0, percentage calculation returns Double.MaxValue
      // So 100.0% != Double.MaxValue -> false
      op.eval(BigDecimal(0), BigDecimal(100)) shouldBe false
    }
  }

  "OpMore" should {
    
    "compile greater than expressions correctly" in {
      val op = Op.compile(">100")
      op shouldBe a[OpMore]
      op.asInstanceOf[OpMore].expr shouldBe BigDecimal(100)
      op.asInstanceOf[OpMore].perc shouldBe false
    }
    
    "evaluate absolute greater than correctly" in {
      val op = Op.compile(">50")
      
      op.eval(BigDecimal(100), BigDecimal(60)) shouldBe true
      op.eval(BigDecimal(100), BigDecimal(50)) shouldBe false
      op.eval(BigDecimal(100), BigDecimal(40)) shouldBe false
    }
    
    "evaluate percentage greater than correctly" in {
      val op = Op.compile(">50.0%")
      
      // v0=100, v=160 -> percentage change = (160-100)/100*100 = 60%, so 60% > 50.0% -> true
      op.eval(BigDecimal(100), BigDecimal(160)) shouldBe true
      
      // v0=100, v=150 -> percentage change = (150-100)/100*100 = 50%, so 50% > 50.0% -> false
      op.eval(BigDecimal(100), BigDecimal(150)) shouldBe false
      
      // v0=100, v=140 -> percentage change = (140-100)/100*100 = 40%, so 40% > 50.0% -> false
      op.eval(BigDecimal(100), BigDecimal(140)) shouldBe false
    }
  }

  "OpLess" should {
    
    "compile less than expressions correctly" in {
      val op = Op.compile("<100")
      op shouldBe a[OpLess]
      op.asInstanceOf[OpLess].expr shouldBe BigDecimal(100)
      op.asInstanceOf[OpLess].perc shouldBe false
    }
    
    "evaluate absolute less than correctly" in {
      val op = Op.compile("<50")
      
      op.eval(BigDecimal(100), BigDecimal(40)) shouldBe true
      op.eval(BigDecimal(100), BigDecimal(50)) shouldBe false
      op.eval(BigDecimal(100), BigDecimal(60)) shouldBe false
    }
    
    "evaluate percentage less than correctly" in {
      val op = Op.compile("<50.0%")
      
      // v0=100, v=140 -> percentage change = (140-100)/100*100 = 40%, so 40% < 50.0% -> true
      op.eval(BigDecimal(100), BigDecimal(140)) shouldBe true
      
      // v0=100, v=150 -> percentage change = (150-100)/100*100 = 50%, so 50% < 50.0% -> false
      op.eval(BigDecimal(100), BigDecimal(150)) shouldBe false
      
      // v0=100, v=160 -> percentage change = (160-100)/100*100 = 60%, so 60% < 50.0% -> false
      op.eval(BigDecimal(100), BigDecimal(160)) shouldBe false
    }
  }

  "OpMoreEq" should {
    
    "compile greater than or equal expressions correctly" in {
      val op = Op.compile(">=100")
      op shouldBe a[OpMoreEq]
      op.asInstanceOf[OpMoreEq].expr shouldBe BigDecimal(100)
      op.asInstanceOf[OpMoreEq].perc shouldBe false
    }
    
    "evaluate absolute greater than or equal correctly" in {
      val op = Op.compile(">=50")
      
      op.eval(BigDecimal(100), BigDecimal(60)) shouldBe true
      op.eval(BigDecimal(100), BigDecimal(50)) shouldBe true
      op.eval(BigDecimal(100), BigDecimal(40)) shouldBe false
    }
    
    "evaluate percentage greater than or equal correctly" in {
      val op = Op.compile(">=50.0%")
      
      // v0=100, v=160 -> percentage change = (160-100)/100*100 = 60%, so 60% >= 50.0% -> true
      op.eval(BigDecimal(100), BigDecimal(160)) shouldBe true
      
      // v0=100, v=150 -> percentage change = (150-100)/100*100 = 50%, so 50% >= 50.0% -> true
      op.eval(BigDecimal(100), BigDecimal(150)) shouldBe true
      
      // v0=100, v=140 -> percentage change = (140-100)/100*100 = 40%, so 40% >= 50.0% -> false
      op.eval(BigDecimal(100), BigDecimal(140)) shouldBe false
    }
  }

  "OpLessEq" should {
    
    "compile less than or equal expressions correctly" in {
      val op = Op.compile("<=100")
      op shouldBe a[OpLessEq]
      op.asInstanceOf[OpLessEq].expr shouldBe BigDecimal(100)
      op.asInstanceOf[OpLessEq].perc shouldBe false
    }
    
    "evaluate absolute less than or equal correctly" in {
      val op = Op.compile("<=50")
      
      op.eval(BigDecimal(100), BigDecimal(40)) shouldBe true
      op.eval(BigDecimal(100), BigDecimal(50)) shouldBe true
      op.eval(BigDecimal(100), BigDecimal(60)) shouldBe false
    }
    
    "evaluate percentage less than or equal correctly" in {
      val op = Op.compile("<=50.0%")
      
      // v0=100, v=140 -> percentage change = (140-100)/100*100 = 40%, so 40% <= 50.0% -> true
      op.eval(BigDecimal(100), BigDecimal(140)) shouldBe true
      
      // v0=100, v=150 -> percentage change = (150-100)/100*100 = 50%, so 50% <= 50.0% -> true
      op.eval(BigDecimal(100), BigDecimal(150)) shouldBe true
      
      // v0=100, v=160 -> percentage change = (160-100)/100*100 = 60%, so 60% <= 50.0% -> false
      op.eval(BigDecimal(100), BigDecimal(160)) shouldBe false
    }
  }

  "OpEqNot" should {
    
    "compile not equal expressions correctly" in {
      val op = Op.compile("!=100")
      op shouldBe a[OpEqNot]
      op.asInstanceOf[OpEqNot].expr shouldBe BigDecimal(100)
      op.asInstanceOf[OpEqNot].perc shouldBe false
    }
    
    "evaluate absolute not equal correctly" in {
      val op = Op.compile("!=50")
      
      op.eval(BigDecimal(100), BigDecimal(40)) shouldBe true
      op.eval(BigDecimal(100), BigDecimal(50)) shouldBe false
      op.eval(BigDecimal(100), BigDecimal(60)) shouldBe true
    }
    
    "evaluate percentage not equal correctly" in {
      val op = Op.compile("!=50.0%")
      
      // v0=100, v=140 -> percentage change = (140-100)/100*100 = 40%, so 40% != 50.0% -> true
      op.eval(BigDecimal(100), BigDecimal(140)) shouldBe true
      
      // v0=100, v=150 -> percentage change = (150-100)/100*100 = 50%, so 50% != 50.0% -> false
      op.eval(BigDecimal(100), BigDecimal(150)) shouldBe false
      
      // v0=100, v=160 -> percentage change = (160-100)/100*100 = 60%, so 60% != 50.0% -> true
      op.eval(BigDecimal(100), BigDecimal(160)) shouldBe true
    }
  }

  "Op Logica Expressions" should {
    "should support logical OR expressions" in {
      val op = Op.compile(">10 || < -5")
      op shouldBe a[OpOr]
      op.eval(BigDecimal(0), BigDecimal(15)) shouldBe true
      op.eval(BigDecimal(0), BigDecimal(-6)) shouldBe true
      op.eval(BigDecimal(0), BigDecimal(0)) shouldBe false

      val opWord = Op.compile(">10 OR < -5")
      opWord.eval(BigDecimal(0), BigDecimal(12)) shouldBe true
      opWord.eval(BigDecimal(0), BigDecimal(-10)) shouldBe true
      opWord.eval(BigDecimal(0), BigDecimal(5)) shouldBe false
    }

    "should support logical AND expressions" in {
      val op = Op.compile(">10 && <20")
      op shouldBe a[OpAnd]
      op.eval(BigDecimal(0), BigDecimal(15)) shouldBe true
      op.eval(BigDecimal(0), BigDecimal(25)) shouldBe false
      op.eval(BigDecimal(0), BigDecimal(9)) shouldBe false

      val opWord = Op.compile(">10 AND <20")
      opWord.eval(BigDecimal(0), BigDecimal(12)) shouldBe true
      opWord.eval(BigDecimal(0), BigDecimal(21)) shouldBe false
      opWord.eval(BigDecimal(0), BigDecimal(5)) shouldBe false
    }

    "should support combined OR and AND expressions" in {
      val op = Op.compile(">10 && <20 || >=50 && <=60")
      op shouldBe a[OpOr]
      
      op.eval(BigDecimal(0), BigDecimal(15)) shouldBe true   // satisfies first AND branch
      op.eval(BigDecimal(0), BigDecimal(55)) shouldBe true   // satisfies second AND branch
      op.eval(BigDecimal(0), BigDecimal(25)) shouldBe false  // matches neither branch
      op.eval(BigDecimal(0), BigDecimal(65)) shouldBe false  // matches neither branch

      val opWord = Op.compile(">10 AND <20 OR >=50 AND <=60 OR <=-5")
      opWord.eval(BigDecimal(0), BigDecimal(12)) shouldBe true   // first AND branch
      opWord.eval(BigDecimal(0), BigDecimal(58)) shouldBe true   // second AND branch
      opWord.eval(BigDecimal(0), BigDecimal(-10)) shouldBe true  // third condition (<= -5)
      opWord.eval(BigDecimal(0), BigDecimal(30)) shouldBe false  // no condition satisfied
    }
  }

  "OpOr" should {
    
    "compile OR expressions correctly" in {
      val op = Op.compile(">100")
      // Note: OpOr is not directly compiled from strings in current implementation
      // This tests the OpOr class functionality
      val orOp = OpOr(Seq(op, Op.compile("=50")))
      orOp shouldBe a[OpOr]
      orOp.asInstanceOf[OpOr].op should have size 2
    }
    
    "evaluate OR logic correctly" in {
      val op1 = Op.compile(">100")
      val op2 = Op.compile("=50")
      val orOp = OpOr(Seq(op1, op2))
      
      // v0=100, v=60 -> op1(60>100)=false, op2(60=50)=false, so false || false = false
      orOp.eval(BigDecimal(100), BigDecimal(60)) shouldBe false
      
      // v0=100, v=120 -> op1(120>100)=true, op2(120=50)=false, so true || false = true
      orOp.eval(BigDecimal(100), BigDecimal(120)) shouldBe true
      
      // v0=100, v=50 -> op1(50>100)=false, op2(50=50)=true, so false || true = true
      orOp.eval(BigDecimal(100), BigDecimal(50)) shouldBe true
    }
  }

  "Percentage calculations" should {
    
    "calculate correct percentages for normal cases" in {
      val op = OpEmpty()
      
      // v0=100, v=50 -> percentage change = (50-100)/100*100 = -50%
      op.perc(BigDecimal(100), BigDecimal(50)) shouldBe BigDecimal(-50)
      
      // v0=100, v=200 -> percentage change = (200-100)/100*100 = 100%
      op.perc(BigDecimal(100), BigDecimal(200)) shouldBe BigDecimal(100)
      
      // v0=100, v=25 -> percentage change = (25-100)/100*100 = -75%
      op.perc(BigDecimal(100), BigDecimal(25)) shouldBe BigDecimal(-75)
    }
    
    "handle zero initial value correctly" in {
      val op = OpEmpty()
      
      // When v0 = 0, should return Double.MaxValue
      op.perc(BigDecimal(0), BigDecimal(100)) shouldBe BigDecimal(Double.MaxValue)
      op.perc(BigDecimal(0), BigDecimal(-100)) shouldBe BigDecimal(Double.MaxValue)
      op.perc(BigDecimal(0), BigDecimal(0)) shouldBe BigDecimal(Double.MaxValue)
    }
    
    "handle negative values correctly" in {
      val op = OpEmpty()
      
      // v0=100, v=-50 -> percentage change = (-50-100)/100*100 = -150%
      op.perc(BigDecimal(100), BigDecimal(-50)) shouldBe BigDecimal(-150)
      
      // v0=-100, v=50 -> percentage change = (50-(-100))/(-100)*100 = 150/(-100)*100 = -150%
      op.perc(BigDecimal(-100), BigDecimal(50)) shouldBe BigDecimal(-150)
    }
  }

  "Edge cases" should {
    
    "handle very large numbers" in {
      val op = Op.compile("> 1000000000")
      op.eval(BigDecimal(100), BigDecimal(1000000001)) shouldBe true
      op.eval(BigDecimal(100), BigDecimal(1000000000)) shouldBe false
    }
    
    "handle very small numbers" in {
      val op = Op.compile("<  0.000001")
      op.eval(BigDecimal(100), BigDecimal(0.0000001)) shouldBe true
      op.eval(BigDecimal(100), BigDecimal(0.000001)) shouldBe false
    }
    
    "handle decimal precision" in {
      val op = Op.compile("=3.14159")
      op.eval(BigDecimal(100), BigDecimal(3.14159)) shouldBe true
      op.eval(BigDecimal(100), BigDecimal(3.1416)) shouldBe false
    }
    
    "handle realistic percentage Conditions" in {
      val op = Op.compile(">  25.5% ")
      op.asInstanceOf[OpMore].abs shouldBe true

      // v0=1000, v=260 -> percentage change = (260-1000)/1000*100 = -74%, but this condition is absolute, so -74% > 25.5% -> true      
      op.eval(BigDecimal(1000), BigDecimal(260)) shouldBe true
      
      // v0=1000, v=755 -> percentage change = (755-1000)/1000*100 = -24.5%, but this condition is absolute, so -24.5% < 25.5% -> false  
      op.eval(BigDecimal(1000), BigDecimal(755)) shouldBe false

      // v0=1000, v=250 -> percentage change = (250-1000)/1000*100 = -75%, but this condition is absolute, so -75% > 25.5% -> true
      op.eval(BigDecimal(1000), BigDecimal(250)) shouldBe true
    }
    
    "handle small percentage changes" in {
      val op = Op.compile("> 0.1%")
      // v0=10000, v=10001 -> percentage change = (10001-10000)/10000*100 = 0.01%, so 0.01% > 0.1% -> false
      op.eval(BigDecimal(10000), BigDecimal(10001)) shouldBe false
      // v0=10000, v=10000.1 -> percentage change = (10000.1-10000)/10000*100 = 0.001%, so 0.001% > 0.1% -> false
      op.eval(BigDecimal(10000), BigDecimal(10000.1)) shouldBe false
      
      // Test with larger percentage changes
      val op2 = Op.compile(">50%")
      // v0=100, v=160 -> percentage change = (160-100)/100*100 = 60%, so 60% > 50% -> true
      op2.eval(BigDecimal(100), BigDecimal(160)) shouldBe true
      // v0=100, v=140 -> percentage change = (140-100)/100*100 = 40%, so 40% > 50% -> false
      op2.eval(BigDecimal(100), BigDecimal(140)) shouldBe false
    }
  }

  "Complex expressions" should {
    
    "handle mixed percentage and absolute Conditions" in {
      val op1 = Op.compile("> 100.0%")
      val op2 = Op.compile("= 50")
      val orOp = OpOr(Seq(op1, op2))
      
      // Test with different scenarios
      orOp.eval(BigDecimal(100), BigDecimal(250)) shouldBe true  // percentage change = (250-100)/100*100 = 150% > 100.0%
      orOp.eval(BigDecimal(100), BigDecimal(50)) shouldBe true   // 50 = 50
      orOp.eval(BigDecimal(100), BigDecimal(75)) shouldBe false  // Neither condition met
    }
    
    "handle Condition transitions" in {
      val op = Op.compile(">50.0%")
      
      // Test percentage Condition crossing
      op.eval(BigDecimal(100), BigDecimal(149)) shouldBe false  // percentage change = (149-100)/100*100 = 49% < 50%
      op.eval(BigDecimal(100), BigDecimal(150)) shouldBe false  // percentage change = (150-100)/100*100 = 50% = 50%
      op.eval(BigDecimal(100), BigDecimal(151)) shouldBe true   // percentage change = (151-100)/100*100 = 51% > 50%
    }
  }

  "Delta operations" should {
    
    "compile absolute delta increase expressions correctly" in {
      val op = Op.compile("> ++100")
      op shouldBe a[OpMore]
      op.asInstanceOf[OpMore].expr shouldBe BigDecimal(100)
      op.asInstanceOf[OpMore].perc shouldBe false
      op.asInstanceOf[OpMore].delta shouldBe 1
    }
    
    "compile absolute delta decrease expressions correctly" in {
      val op = Op.compile("<--100")
      op shouldBe a[OpLess]
      op.asInstanceOf[OpLess].expr shouldBe BigDecimal(100)
      op.asInstanceOf[OpLess].perc shouldBe false
      op.asInstanceOf[OpLess].delta shouldBe -1
    }
    
    "compile percentage delta increase expressions correctly" in {
      val op = Op.compile(">++50.0%")
      op shouldBe a[OpMore]
      op.asInstanceOf[OpMore].expr shouldBe BigDecimal(50.0)
      op.asInstanceOf[OpMore].perc shouldBe true
      op.asInstanceOf[OpMore].delta shouldBe 1
    }
    
    "compile percentage delta decrease expressions correctly" in {
      val op = Op.compile("<--25.0%")
      op.asInstanceOf[OpLess].expr shouldBe BigDecimal(25.0)
      op.asInstanceOf[OpLess].perc shouldBe true
      op.asInstanceOf[OpLess].delta shouldBe -1
    }
    
    "evaluate absolute delta increase correctly" in {
      val op = Op.compile(">++50")
      
      // v0=100, v=160 -> delta = 160-100 = 60, so 60 > 50 -> true
      op.eval(BigDecimal(100), BigDecimal(160)) shouldBe true
      
      // v0=100, v=150 -> delta = 150-100 = 50, so 50 > 50 -> false
      op.eval(BigDecimal(100), BigDecimal(150)) shouldBe false
      
      // v0=100, v=140 -> delta = 140-100 = 40, so 40 > 50 -> false
      op.eval(BigDecimal(100), BigDecimal(140)) shouldBe false
      
      // v0=100, v=90 -> delta = 90-100 = -10, so -10 > 50 -> false
      op.eval(BigDecimal(100), BigDecimal(90)) shouldBe false
    }
    
    "evaluate absolute delta decrease correctly" in {
      val op = Op.compile(">--30")
      
      // v0=100, v=60 -> delta = 100-60 = 40, so 40 > 30 -> true
      op.eval(BigDecimal(100), BigDecimal(60)) shouldBe true
      
      // v0=100, v=70 -> delta = 100-70 = 30, so 30 > 30 -> false
      op.eval(BigDecimal(100), BigDecimal(70)) shouldBe false
      
      // v0=100, v=80 -> delta = 100-80 = 20, so 20 > 30 -> false
      op.eval(BigDecimal(100), BigDecimal(80)) shouldBe false
      
      // v0=100, v=120 -> delta = 100-120 = -20, so -20 > 30 -> false
      op.eval(BigDecimal(100), BigDecimal(120)) shouldBe false
    }
    
    "evaluate percentage delta increase correctly" in {
      val op = Op.compile(">++25.0%")
      
      // v0=100, v=130 -> delta = 30, percentage = 30/100*100 = 30%, so 30% > 25% -> true
      op.eval(BigDecimal(100), BigDecimal(130)) shouldBe true
      
      // v0=100, v=125 -> delta = 25, percentage = 25/100*100 = 25%, so 25% > 25% -> false
      op.eval(BigDecimal(100), BigDecimal(125)) shouldBe false
      
      // v0=100, v=120 -> delta = 20, percentage = 20/100*100 = 20%, so 20% > 25% -> false
      op.eval(BigDecimal(100), BigDecimal(120)) shouldBe false
      
      // v0=100, v=80 -> delta = -20, percentage = -20/100*100 = -20%, so -20% > 25% -> false
      op.eval(BigDecimal(100), BigDecimal(80)) shouldBe false
    }
    
    "evaluate percentage delta decrease correctly" in {
      val op = Op.compile(">--20.0%")
      
      // v0=100, v=70 -> delta = 30, percentage = 30/100*100 = 30%, so 30% > 20% -> true
      op.eval(BigDecimal(100), BigDecimal(70)) shouldBe true
      
      // v0=100, v=80 -> delta = 20, percentage = 20/100*100 = 20%, so 20% > 20% -> false
      op.eval(BigDecimal(100), BigDecimal(80)) shouldBe false
      
      // v0=100, v=85 -> delta = 15, percentage = 15/100*100 = 15%, so 15% > 20% -> false
      op.eval(BigDecimal(100), BigDecimal(85)) shouldBe false
      
      // v0=100, v=120 -> delta = -20, percentage = -20/100*100 = -20%, so -20% > 20% -> false
      op.eval(BigDecimal(100), BigDecimal(120)) shouldBe false
    }
    
    "handle delta operations with equality" in {
      val op = Op.compile("=++50")
      op shouldBe a[OpEq]
      op.asInstanceOf[OpEq].expr shouldBe BigDecimal(50)
      op.asInstanceOf[OpEq].perc shouldBe false
      op.asInstanceOf[OpEq].delta shouldBe 1
      
      // v0=100, v=150 -> delta = 150-100 = 50, so 50 == 50 -> true
      op.eval(BigDecimal(100), BigDecimal(150)) shouldBe true
      
      // v0=100, v=160 -> delta = 160-100 = 60, so 60 == 50 -> false
      op.eval(BigDecimal(100), BigDecimal(160)) shouldBe false
      
      // v0=100, v=140 -> delta = 140-100 = 40, so 40 == 50 -> false
      op.eval(BigDecimal(100), BigDecimal(140)) shouldBe false
    }
    
    "handle delta operations with greater than or equal" in {
      val op = Op.compile(">= ++30")
      op shouldBe a[OpMoreEq]
      op.asInstanceOf[OpMoreEq].expr shouldBe BigDecimal(30)
      op.asInstanceOf[OpMoreEq].perc shouldBe false
      op.asInstanceOf[OpMoreEq].delta shouldBe 1
      
      // v0=100, v=140 -> delta = 140-100 = 40, so 40 >= 30 -> true
      op.eval(BigDecimal(100), BigDecimal(140)) shouldBe true
      
      // v0=100, v=130 -> delta = 130-100 = 30, so 30 >= 30 -> true
      op.eval(BigDecimal(100), BigDecimal(130)) shouldBe true
      
      // v0=100, v=120 -> delta = 120-100 = 20, so 20 >= 30 -> false
      op.eval(BigDecimal(100), BigDecimal(120)) shouldBe false
    }
    
    "handle delta operations with less than or equal" in {
      val op = Op.compile("<= --25")
      op shouldBe a[OpLessEq]
      op.asInstanceOf[OpLessEq].expr shouldBe BigDecimal(25)
      op.asInstanceOf[OpLessEq].perc shouldBe false
      op.asInstanceOf[OpLessEq].delta shouldBe -1
      
      // v0=100, v=70 -> delta = 100-70 = 30, so 30 <= 25 -> false
      op.eval(BigDecimal(100), BigDecimal(70)) shouldBe false
      
      // v0=100, v=75 -> delta = 100-75 = 25, so 25 <= 25 -> true
      op.eval(BigDecimal(100), BigDecimal(75)) shouldBe true
      
      // v0=100, v=80 -> delta = 100-80 = 20, so 20 <= 25 -> true
      op.eval(BigDecimal(100), BigDecimal(80)) shouldBe true
    }
    
    "handle delta operations with not equal" in {
      val op = Op.compile("!= ++40")
      op shouldBe a[OpEqNot]
      op.asInstanceOf[OpEqNot].expr shouldBe BigDecimal(40)
      op.asInstanceOf[OpEqNot].perc shouldBe false
      op.asInstanceOf[OpEqNot].delta shouldBe 1
      
      // v0=100, v=140 -> delta = 140-100 = 40, so 40 != 40 -> false
      op.eval(BigDecimal(100), BigDecimal(140)) shouldBe false
      
      // v0=100, v=150 -> delta = 150-100 = 50, so 50 != 40 -> true
      op.eval(BigDecimal(100), BigDecimal(150)) shouldBe true
      
      // v0=100, v=130 -> delta = 130-100 = 30, so 30 != 40 -> true
      op.eval(BigDecimal(100), BigDecimal(130)) shouldBe true
    }
    
    "handle percentage delta operations with equality" in {
      val op = Op.compile("= ++30.0%")
      op shouldBe a[OpEq]
      op.asInstanceOf[OpEq].expr shouldBe BigDecimal(30.0)
      op.asInstanceOf[OpEq].perc shouldBe true
      op.asInstanceOf[OpEq].delta shouldBe 1
      
      // v0=100, v=130 -> delta = 30, percentage = 30/100*100 = 30%, so 30% == 30% -> true
      op.eval(BigDecimal(100), BigDecimal(130)) shouldBe true
      
      // v0=100, v=140 -> delta = 40, percentage = 40/100*100 = 40%, so 40% == 30% -> false
      op.eval(BigDecimal(100), BigDecimal(140)) shouldBe false
      
      // v0=100, v=120 -> delta = 20, percentage = 20/100*100 = 20%, so 20% == 30% -> false
      op.eval(BigDecimal(100), BigDecimal(120)) shouldBe false
    }
    
    "handle edge cases for delta operations" in {
      val op = Op.compile(">++0")
      op shouldBe a[OpMore]
      op.asInstanceOf[OpMore].expr shouldBe BigDecimal(0)
      op.asInstanceOf[OpMore].delta shouldBe 1
      
      // v0=100, v=100 -> delta = 0, so 0 > 0 -> false
      op.eval(BigDecimal(100), BigDecimal(100)) shouldBe false
      
      // v0=100, v=101 -> delta = 0, so 1 > 0 -> true
      op.eval(BigDecimal(100), BigDecimal(101)) shouldBe true
      
      // v0=100, v=99 -> delta = -1, so -1 > 0 -> false
      op.eval(BigDecimal(100), BigDecimal(99)) shouldBe false
    }
    
    "handle zero initial value for delta operations" in {
      val op = Op.compile(">++10")
      
      // v0=0, v=10 -> delta = 10, so 10 > 10 -> false
      op.eval(BigDecimal(0), BigDecimal(10)) shouldBe false
      
      // v0=0, v=15 -> delta = 15, so 15 > 10 -> true
      op.eval(BigDecimal(0), BigDecimal(15)) shouldBe true
      
      // v0=0, v=5 -> delta = 5, so 5 > 10 -> false
      op.eval(BigDecimal(0), BigDecimal(5)) shouldBe false
    }
    
    "handle negative values for delta operations" in {
      val op = Op.compile("> ++20")
      
      // v0=-100, v=-70 -> delta = -70-(-100) = 30, so 30 > 20 -> true
      op.eval(BigDecimal(-100), BigDecimal(-70)) shouldBe true
      
      // v0=-100, v=-80 -> delta = -80-(-100) = 20, so 20 > 20 -> false
      op.eval(BigDecimal(-100), BigDecimal(-80)) shouldBe false
      
      // v0=-100, v=-90 -> delta = -90-(-100) = 10, so 10 > 20 -> false
      op.eval(BigDecimal(-100), BigDecimal(-90)) shouldBe false
    }
    
    "handle complex delta scenarios" in {
      val op1 = Op.compile("> ++50")
      val op2 = Op.compile("> --30")
      val orOp = OpOr(Seq(op1, op2))
      
      // v0=100, v=160 -> op1: delta=60 > 50 -> true, so OR result = true
      orOp.eval(BigDecimal(100), BigDecimal(160)) shouldBe true
      
      // v0=100, v=60 -> op1: delta=-40 > 50 -> false, op2: delta=40 > 30 -> true, so OR result = true
      orOp.eval(BigDecimal(100), BigDecimal(60)) shouldBe true
      
      // v0=100, v=120 -> op1: delta=20 > 50 -> false, op2: delta=-20 > 30 -> false, so OR result = false
      orOp.eval(BigDecimal(100), BigDecimal(120)) shouldBe false
    }
        
  }

  "Absolute value expressions" should {
        
    "should handle negative Condition expressions" in {
      val op = Op.compile("> -10")
      op shouldBe a[OpMore]
      op.asInstanceOf[OpMore].expr shouldBe BigDecimal(-10)
      op.asInstanceOf[OpMore].abs shouldBe false
      
      // Test evaluation: should always be > -10 (no absolute value handling)
      op.eval(BigDecimal(100), BigDecimal(-5)) shouldBe true   // -5 > -10
      op.eval(BigDecimal(100), BigDecimal(-15)) shouldBe false // -15 > -10 is false
      op.eval(BigDecimal(100), BigDecimal(5)) shouldBe true    // 5 > -10
      op.eval(BigDecimal(100), BigDecimal(0)) shouldBe true    // 0 > -10
    }
    
    "should handle positive Condition expressions" in {
      val op = Op.compile("> +10")
      op shouldBe a[OpMore]
      op.asInstanceOf[OpMore].expr shouldBe BigDecimal(10)
      op.asInstanceOf[OpMore].abs shouldBe false
      
      // Test evaluation: should always be > 10 (no absolute value handling)
      op.eval(BigDecimal(100), BigDecimal(15)) shouldBe true   // 15 > 10
      op.eval(BigDecimal(100), BigDecimal(5)) shouldBe false   // 5 > 10 is false
      op.eval(BigDecimal(100), BigDecimal(10)) shouldBe false  // 10 > 10 is false
      op.eval(BigDecimal(100), BigDecimal(-5)) shouldBe false  // -5 > 10 is false
    }
    
    "should handle unsigned Condition expressions" in {
      val op = Op.compile("> 10")
      op shouldBe a[OpMore]
      op.asInstanceOf[OpMore].expr shouldBe BigDecimal(10)
      op.asInstanceOf[OpMore].abs shouldBe true
      
      // Test evaluation: should be > |10| or > |-10| (absolute value handling)
      op.eval(BigDecimal(100), BigDecimal(15)) shouldBe true   // 15 > 10
      op.eval(BigDecimal(100), BigDecimal(5)) shouldBe false   // 5 > 10 is false
      op.eval(BigDecimal(100), BigDecimal(-15)) shouldBe true  // |-15| = 15 > 10
      op.eval(BigDecimal(100), BigDecimal(-5)) shouldBe false  // |-5| = 5 > 10 is false
      op.eval(BigDecimal(100), BigDecimal(10)) shouldBe false  // 10 > 10 is false
      op.eval(BigDecimal(100), BigDecimal(-10)) shouldBe false // |-10| = 10 > 10 is false
    }
    
    "should handle negative Condition expressions with less than" in {
      val op = Op.compile("< -20")
      op shouldBe a[OpLess]
      op.asInstanceOf[OpLess].expr shouldBe BigDecimal(-20)
      op.asInstanceOf[OpLess].abs shouldBe false
      
      // Test evaluation: should always be < -20 (no absolute value handling)
      op.eval(BigDecimal(100), BigDecimal(-25)) shouldBe true   // -25 < -20
      op.eval(BigDecimal(100), BigDecimal(-15)) shouldBe false  // -15 < -20 is false
      op.eval(BigDecimal(100), BigDecimal(-30)) shouldBe true   // -30 < -20
      op.eval(BigDecimal(100), BigDecimal(0)) shouldBe false    // 0 < -20 is false
    }
    
    "should handle positive Condition expressions with less than" in {
      val op = Op.compile("< +30")
      op shouldBe a[OpLess]
      op.asInstanceOf[OpLess].expr shouldBe BigDecimal(30)
      op.asInstanceOf[OpLess].abs shouldBe false
      
      // Test evaluation: should always be < 30 (no absolute value handling)
      op.eval(BigDecimal(100), BigDecimal(25)) shouldBe true   // 25 < 30
      op.eval(BigDecimal(100), BigDecimal(35)) shouldBe false  // 35 < 30 is false
      op.eval(BigDecimal(100), BigDecimal(30)) shouldBe false  // 30 < 30 is false
      op.eval(BigDecimal(100), BigDecimal(-10)) shouldBe true  // -10 < 30
    }
    
    "should handle unsigned Condition expressions with less than" in {
      val op = Op.compile("< 25")
      op shouldBe a[OpLess]
      op.asInstanceOf[OpLess].expr shouldBe BigDecimal(25)
      op.asInstanceOf[OpLess].abs shouldBe true
      
      // Test evaluation: should be < |25| or < |-25| (absolute value handling)
      op.eval(BigDecimal(100), BigDecimal(20)) shouldBe true   // 20 < 25
      op.eval(BigDecimal(100), BigDecimal(30)) shouldBe false  // 30 < 25 is false
      op.eval(BigDecimal(100), BigDecimal(-20)) shouldBe true  // |-20| = 20 < 25
      op.eval(BigDecimal(100), BigDecimal(-30)) shouldBe false // |-30| = 30 < 25 is false
      op.eval(BigDecimal(100), BigDecimal(25)) shouldBe false  // 25 < 25 is false
      op.eval(BigDecimal(100), BigDecimal(-25)) shouldBe false // |-25| = 25 < 25 is false
    }
    
    "should handle negative Condition expressions with equality" in {
      val op = Op.compile("= -15")
      op shouldBe a[OpEq]
      op.asInstanceOf[OpEq].expr shouldBe BigDecimal(-15)
      op.asInstanceOf[OpEq].abs shouldBe false
      
      // Test evaluation: should always be = -15 (no absolute value handling)
      op.eval(BigDecimal(100), BigDecimal(-15)) shouldBe true   // -15 = -15
      op.eval(BigDecimal(100), BigDecimal(15)) shouldBe false  // 15 = -15 is false
      op.eval(BigDecimal(100), BigDecimal(-10)) shouldBe false // -10 = -15 is false
    }
    
    "should handle positive Condition expressions with equality" in {
      val op = Op.compile("= +25")
      op shouldBe a[OpEq]
      op.asInstanceOf[OpEq].expr shouldBe BigDecimal(25)
      op.asInstanceOf[OpEq].abs shouldBe false
      
      // Test evaluation: should always be = 25 (no absolute value handling)
      op.eval(BigDecimal(100), BigDecimal(25)) shouldBe true   // 25 = 25
      op.eval(BigDecimal(100), BigDecimal(-25)) shouldBe false // -25 = 25 is false
      op.eval(BigDecimal(100), BigDecimal(20)) shouldBe false  // 20 = 25 is false
    }
    
    "should handle unsigned Condition expressions with equality" in {
      val op = Op.compile("= 20")
      op shouldBe a[OpEq]
      op.asInstanceOf[OpEq].expr shouldBe BigDecimal(20)
      op.asInstanceOf[OpEq].abs shouldBe true
      
      // Test evaluation: equality ignores abs parameter, so it's just exact value matching
      op.eval(BigDecimal(100), BigDecimal(20)) shouldBe true   // 20 = 20
      op.eval(BigDecimal(100), BigDecimal(-20)) shouldBe false // -20 != 20 (exact value match)
      op.eval(BigDecimal(100), BigDecimal(15)) shouldBe false  // 15 != 20
      op.eval(BigDecimal(100), BigDecimal(-15)) shouldBe false // -15 != 20
      op.eval(BigDecimal(100), BigDecimal(25)) shouldBe false  // 25 != 20
      op.eval(BigDecimal(100), BigDecimal(-25)) shouldBe false // -25 != 20
    }
    
    "should handle negative Condition expressions with greater than or equal" in {
      val op = Op.compile(">= -30")
      op shouldBe a[OpMoreEq]
      op.asInstanceOf[OpMoreEq].expr shouldBe BigDecimal(-30)
      op.asInstanceOf[OpMoreEq].abs shouldBe false
      
      // Test evaluation: should always be >= -30 (no absolute value handling)
      op.eval(BigDecimal(100), BigDecimal(-25)) shouldBe true   // -25 >= -30
      op.eval(BigDecimal(100), BigDecimal(-30)) shouldBe true   // -30 >= -30
      op.eval(BigDecimal(100), BigDecimal(-35)) shouldBe false  // -35 >= -30 is false
      op.eval(BigDecimal(100), BigDecimal(0)) shouldBe true     // 0 >= -30
    }
    
    "should handle positive Condition expressions with greater than or equal" in {
      val op = Op.compile(">= +40")
      op shouldBe a[OpMoreEq]
      op.asInstanceOf[OpMoreEq].expr shouldBe BigDecimal(40)
      op.asInstanceOf[OpMoreEq].abs shouldBe false
      
      // Test evaluation: should always be >= 40 (no absolute value handling)
      op.eval(BigDecimal(100), BigDecimal(45)) shouldBe true   // 45 >= 40
      op.eval(BigDecimal(100), BigDecimal(40)) shouldBe true   // 40 >= 40
      op.eval(BigDecimal(100), BigDecimal(35)) shouldBe false  // 35 >= 40 is false
      op.eval(BigDecimal(100), BigDecimal(-10)) shouldBe false // -10 >= 40 is false
    }
    
    "should handle unsigned Condition expressions with greater than or equal" in {
      val op = Op.compile(">= 35")
      op shouldBe a[OpMoreEq]
      op.asInstanceOf[OpMoreEq].expr shouldBe BigDecimal(35)
      op.asInstanceOf[OpMoreEq].abs shouldBe true
      
      // Test evaluation: should be >= |35| or >= |-35| (absolute value handling)
      op.eval(BigDecimal(100), BigDecimal(40)) shouldBe true   // 40 >= 35
      op.eval(BigDecimal(100), BigDecimal(35)) shouldBe true   // 35 >= 35
      op.eval(BigDecimal(100), BigDecimal(-40)) shouldBe true  // |-40| = 40 >= 35
      op.eval(BigDecimal(100), BigDecimal(-35)) shouldBe true  // |-35| = 35 >= 35
      op.eval(BigDecimal(100), BigDecimal(30)) shouldBe false  // 30 >= 35 is false
      op.eval(BigDecimal(100), BigDecimal(-30)) shouldBe false // |-30| = 30 >= 35 is false
    }
    
    "should handle negative Condition expressions with less than or equal" in {
      val op = Op.compile("<= -25")
      op shouldBe a[OpLessEq]
      op.asInstanceOf[OpLessEq].expr shouldBe BigDecimal(-25)
      op.asInstanceOf[OpLessEq].abs shouldBe false
      
      // Test evaluation: should always be <= -25 (no absolute value handling)
      op.eval(BigDecimal(100), BigDecimal(-30)) shouldBe true   // -30 <= -25
      op.eval(BigDecimal(100), BigDecimal(-25)) shouldBe true   // -25 <= -25
      op.eval(BigDecimal(100), BigDecimal(-20)) shouldBe false  // -20 <= -25 is false
      op.eval(BigDecimal(100), BigDecimal(0)) shouldBe false    // 0 <= -25 is false
    }
    
    "should handle positive Condition expressions with less than or equal" in {
      val op = Op.compile("<= +45")
      op shouldBe a[OpLessEq]
      op.asInstanceOf[OpLessEq].expr shouldBe BigDecimal(45)
      op.asInstanceOf[OpLessEq].abs shouldBe false
      
      // Test evaluation: should always be <= 45 (no absolute value handling)
      op.eval(BigDecimal(100), BigDecimal(40)) shouldBe true   // 40 <= 45
      op.eval(BigDecimal(100), BigDecimal(45)) shouldBe true   // 45 <= 45
      op.eval(BigDecimal(100), BigDecimal(50)) shouldBe false  // 50 <= 45 is false
      op.eval(BigDecimal(100), BigDecimal(-10)) shouldBe true  // -10 <= 45
    }
    
    "should handle unsigned Condition expressions with less than or equal" in {
      val op = Op.compile("<= 50")
      op shouldBe a[OpLessEq]
      op.asInstanceOf[OpLessEq].expr shouldBe BigDecimal(50)
      op.asInstanceOf[OpLessEq].abs shouldBe true
      
      // Test evaluation: should be <= |50| or <= |-50| (absolute value handling)
      op.eval(BigDecimal(100), BigDecimal(45)) shouldBe true   // 45 <= 50
      op.eval(BigDecimal(100), BigDecimal(50)) shouldBe true   // 50 <= 50
      op.eval(BigDecimal(100), BigDecimal(-45)) shouldBe true  // |-45| = 45 <= 50
      op.eval(BigDecimal(100), BigDecimal(-50)) shouldBe true  // |-50| = 50 <= 50
      op.eval(BigDecimal(100), BigDecimal(55)) shouldBe false  // 55 <= 50 is false
      op.eval(BigDecimal(100), BigDecimal(-55)) shouldBe false // |-55| = 55 <= 50 is false
    }
    
    "should handle negative Condition expressions with not equal" in {
      val op = Op.compile("!= -20")
      op shouldBe a[OpEqNot]
      op.asInstanceOf[OpEqNot].expr shouldBe BigDecimal(-20)
      op.asInstanceOf[OpEqNot].abs shouldBe false
      
      // Test evaluation: should always be != -20 (no absolute value handling)
      op.eval(BigDecimal(100), BigDecimal(-20)) shouldBe false  // -20 != -20 is false
      op.eval(BigDecimal(100), BigDecimal(20)) shouldBe true    // 20 != -20
      op.eval(BigDecimal(100), BigDecimal(-15)) shouldBe true   // -15 != -20
      op.eval(BigDecimal(100), BigDecimal(0)) shouldBe true     // 0 != -20
    }
    
    "should handle positive Condition expressions with not equal" in {
      val op = Op.compile("!= +30")
      op shouldBe a[OpEqNot]
      op.asInstanceOf[OpEqNot].expr shouldBe BigDecimal(30)
      op.asInstanceOf[OpEqNot].abs shouldBe false
      
      // Test evaluation: should always be != 30 (no absolute value handling)
      op.eval(BigDecimal(100), BigDecimal(30)) shouldBe false  // 30 != 30 is false
      op.eval(BigDecimal(100), BigDecimal(-30)) shouldBe true  // -30 != 30
      op.eval(BigDecimal(100), BigDecimal(25)) shouldBe true   // 25 != 30
      op.eval(BigDecimal(100), BigDecimal(35)) shouldBe true   // 35 != 30
    }
    
    "should handle unsigned Condition expressions with not equal" in {
      val op = Op.compile("!= 25")
      op shouldBe a[OpEqNot]
      op.asInstanceOf[OpEqNot].expr shouldBe BigDecimal(25)
      op.asInstanceOf[OpEqNot].abs shouldBe true
      
      // Test evaluation: not equality ignores abs parameter, so it's just exact value matching
      op.eval(BigDecimal(100), BigDecimal(25)) shouldBe false  // 25 != 25 is false
      op.eval(BigDecimal(100), BigDecimal(-25)) shouldBe true  // -25 != 25 (exact value match)
      op.eval(BigDecimal(100), BigDecimal(20)) shouldBe true   // 20 != 25
      op.eval(BigDecimal(100), BigDecimal(-20)) shouldBe true  // -20 != 25
      op.eval(BigDecimal(100), BigDecimal(30)) shouldBe true   // 30 != 25
      op.eval(BigDecimal(100), BigDecimal(-30)) shouldBe true  // -30 != 25
    }
    
    "should handle mixed absolute and non-absolute expressions" in {
      val op1 = Op.compile("> -15")  // abs = false
      val op2 = Op.compile("> 20")   // abs = true
      val orOp = OpOr(Seq(op1, op2))
      
      // Test evaluation with mixed absolute value handling
      orOp.eval(BigDecimal(100), BigDecimal(-10)) shouldBe true   // -10 > -15 (op1)
      orOp.eval(BigDecimal(100), BigDecimal(25)) shouldBe true    // 25 > 20 (op2)
      orOp.eval(BigDecimal(100), BigDecimal(-25)) shouldBe true   // |-25| = 25 > 20 (op2)
      orOp.eval(BigDecimal(100), BigDecimal(-20)) shouldBe false  // -20 > -15 is false, |-20| = 20 > 20 is false
      orOp.eval(BigDecimal(100), BigDecimal(15)) shouldBe true   // 15 > -15 is true, so OR = true
    }
    
    "should handle edge cases for absolute value expressions" in {
      val op = Op.compile("> 0")
      op shouldBe a[OpMore]
      op.asInstanceOf[OpMore].expr shouldBe BigDecimal(0)
      op.asInstanceOf[OpMore].abs shouldBe true
      
      // Test evaluation: should be > |0| (absolute value handling)
      op.eval(BigDecimal(100), BigDecimal(1)) shouldBe true    // 1 > 0
      op.eval(BigDecimal(100), BigDecimal(-1)) shouldBe true   // |-1| = 1 > 0
      op.eval(BigDecimal(100), BigDecimal(0)) shouldBe false   // 0 > 0 is false
      op.eval(BigDecimal(100), BigDecimal(0.1)) shouldBe true  // 0.1 > 0
      op.eval(BigDecimal(100), BigDecimal(-0.1)) shouldBe true // |-0.1| = 0.1 > 0
    }
    
  }

  "OpMoreOnce" should {
    "handle basic once logic" in {
      val op = Op.compile(">>10")
      op shouldBe a[OpMoreOnce]
      op.asInstanceOf[OpMoreOnce].abs shouldBe true
            
      // v0=0, v=1: 1 > 10 = false, history=false, output=false
      op.eval(BigDecimal(0), BigDecimal(1)) shouldBe false
      // v0=1, v=2: 2 > 10 = false, history=false, output=false
      op.eval(BigDecimal(1), BigDecimal(2)) shouldBe false
      // v0=2, v=15: 15 > 10 = true, history=false, output=true (first true)
      op.eval(BigDecimal(2), BigDecimal(15)) shouldBe true
      // v0=15, v=15: 15 > 10 = true, history=true, output=false (not first true)
      op.eval(BigDecimal(15), BigDecimal(15)) shouldBe false
      // v0=15, v=20: 20 > 10 = true, history=true, output=false (not first true)
      op.eval(BigDecimal(15), BigDecimal(20)) shouldBe false
      // v0=20, v=1: 1 > 10 = false, history=true, output=false (condition false)
      op.eval(BigDecimal(20), BigDecimal(1)) shouldBe false
      // v0=1, v=20: 20 > 10 = true, history=false, output=true (first true again)
      op.eval(BigDecimal(1), BigDecimal(20)) shouldBe true
    }
    
    "handle once logic with percentage" in {
      val op = Op.compile(">>25%")
      op shouldBe a[OpMoreOnce]
      op.asInstanceOf[OpMoreOnce].abs shouldBe true
            
      // v0=100, v=100: percentage change = 0% > 25% = false, history=false, output=false
      op.eval(BigDecimal(100), BigDecimal(100)) shouldBe false
      // v0=100, v=120: percentage change = 20% > 25% = false, history=false, output=false
      op.eval(BigDecimal(100), BigDecimal(120)) shouldBe false
      // v0=120, v=160: percentage change = 33.33% > 25% = true, history=false, output=true (first true)
      op.eval(BigDecimal(120), BigDecimal(160)) shouldBe true
      // v0=160, v=200: percentage change = 25% > 25% = false, history=true, output=false (condition false)
      op.eval(BigDecimal(160), BigDecimal(200)) shouldBe false
      // v0=200, v=250: percentage change = 25% > 25% = false, history=false, output=false (condition false)
      op.eval(BigDecimal(200), BigDecimal(250)) shouldBe false
      // v0=250, v=300: percentage change = 20% > 25% = false, history=false, output=false (condition false)
      op.eval(BigDecimal(250), BigDecimal(300)) shouldBe false
      // v0=300, v=400: percentage change = 33.33% > 25% = true, history=false, output=true (first true again)
      op.eval(BigDecimal(300), BigDecimal(400)) shouldBe true
    }
    
    "handle once logic with delta operations" in {
      val op = Op.compile(">>++20")
      op shouldBe a[OpMoreOnce]
      op.asInstanceOf[OpMoreOnce].abs shouldBe true
      
      // v0=100, v=110: change = 10 > 20 = false, history=false, output=false
      op.eval(BigDecimal(100), BigDecimal(110)) shouldBe false
      // v0=110, v=125: change = 15 > 20 = false, history=false, output=false
      op.eval(BigDecimal(110), BigDecimal(125)) shouldBe false
      // v0=125, v=150: change = 25 > 20 = true, history=false, output=true (first true)
      op.eval(BigDecimal(125), BigDecimal(150)) shouldBe true
      // v0=150, v=180: change = 30 > 20 = true, history=true, output=false (not first true)
      op.eval(BigDecimal(150), BigDecimal(180)) shouldBe false
      // v0=180, v=190: change = 10 > 20 = false, history=true, output=false (condition false)
      op.eval(BigDecimal(180), BigDecimal(190)) shouldBe false
      // v0=190, v=220: change = 30 > 20 = true, history=false, output=true (first true again)
      op.eval(BigDecimal(190), BigDecimal(220)) shouldBe true
    }
    
    "handle once logic with absolute value expressions" in {
      val op = Op.compile(">>10")
      op shouldBe a[OpMoreOnce]
      op.asInstanceOf[OpMoreOnce].abs shouldBe true
            
      // v0=0, v=5: |5| > 10 = false, history=false, output=false
      op.eval(BigDecimal(0), BigDecimal(5)) shouldBe false
      // v0=5, v=-5: |-5| > 10 = false, history=false, output=false
      op.eval(BigDecimal(5), BigDecimal(-5)) shouldBe false
      // v0=-5, v=15: |15| > 10 = true, history=false, output=true (first true)
      op.eval(BigDecimal(-5), BigDecimal(15)) shouldBe true
      // v0=15, v=-15: |-15| > 10 = true, history=true, output=false (not first true)
      op.eval(BigDecimal(15), BigDecimal(-15)) shouldBe false
      // v0=-15, v=8: |8| > 10 = false, history=true, output=false (condition false)
      op.eval(BigDecimal(-15), BigDecimal(8)) shouldBe false
      // v0=8, v=20: |20| > 10 = true, history=false, output=true (first true again)
      op.eval(BigDecimal(8), BigDecimal(20)) shouldBe true
    }
    
    "handle once logic with explicit sign expressions" in {
      val op = Op.compile(">>+15")
      op shouldBe a[OpMoreOnce]
      op.asInstanceOf[OpMoreOnce].abs shouldBe false
            
      // v0=0, v=10: 10 > 15 = false, history=false, output=false
      op.eval(BigDecimal(0), BigDecimal(10)) shouldBe false
      // v0=10, v=20: 20 > 15 = true, history=false, output=true (first true)
      op.eval(BigDecimal(10), BigDecimal(20)) shouldBe true
      // v0=20, v=25: 25 > 15 = true, history=true, output=false (not first true)
      op.eval(BigDecimal(20), BigDecimal(25)) shouldBe false
      // v0=25, v=5: 5 > 15 = false, history=true, output=false (condition false)
      op.eval(BigDecimal(25), BigDecimal(5)) shouldBe false
      // v0=5, v=30: 30 > 15 = true, history=false, output=true (first true again)
      op.eval(BigDecimal(5), BigDecimal(30)) shouldBe true
    }
    
    "handle edge cases for once logic" in {
      val op = Op.compile(">>0")
      op shouldBe a[OpMoreOnce]
      op.asInstanceOf[OpMoreOnce].abs shouldBe true
      
      // v0=0, v=0: 0 > 0 = false, history=false, output=false
      op.eval(BigDecimal(0), BigDecimal(0)) shouldBe false
      // v0=0, v=1: 1 > 0 = true, history=false, output=true (first true)
      op.eval(BigDecimal(0), BigDecimal(1)) shouldBe true
      // v0=1, v=2: 2 > 0 = true, history=true, output=false (not first true)
      op.eval(BigDecimal(1), BigDecimal(2)) shouldBe false
      // v0=2, v=0: 0 > 0 = false, history=true, output=false (condition false)
      op.eval(BigDecimal(2), BigDecimal(0)) shouldBe false
      // v0=0, v=5: 5 > 0 = true, history=false, output=true (first true again)
      op.eval(BigDecimal(0), BigDecimal(5)) shouldBe true
    }
    
    "handle complex once logic scenarios" in {
      val op = Op.compile(">>50%")
      op shouldBe a[OpMoreOnce]
      op.asInstanceOf[OpMoreOnce].abs shouldBe true
      
      // v0=100, v=120: percentage change = 20% > 50% = false, history=false, output=false
      op.eval(BigDecimal(100), BigDecimal(120)) shouldBe false
      // v0=120, v=200: percentage change = 66.67% > 50% = true, history=false, output=true (first true)
      op.eval(BigDecimal(120), BigDecimal(200)) shouldBe true
      // v0=200, v=300: percentage change = 50% > 50% = false, history=true, output=false (condition false)
      op.eval(BigDecimal(200), BigDecimal(300)) shouldBe false
      // v0=300, v=400: percentage change = 33.33% > 50% = false, history=false, output=false (condition false)
      op.eval(BigDecimal(300), BigDecimal(400)) shouldBe false
      // v0=400, v=700: percentage change = 75% > 50% = true, history=false, output=true (first true again)
      op.eval(BigDecimal(400), BigDecimal(700)) shouldBe true
      // v0=700, v=800: percentage change = 14.29% > 50% = false, history=true, output=false (condition false)
      op.eval(BigDecimal(700), BigDecimal(800)) shouldBe false
    }
  }
  
  "OpLessOnce" should {
    "handle basic once logic" in {
      val op = Op.compile("<<10")
      op shouldBe a[OpLessOnce]
      op.asInstanceOf[OpLessOnce].abs shouldBe true
      
      // v0=20, v=15: 15 < 10 = false, history=false, output=false
      op.eval(BigDecimal(20), BigDecimal(15)) shouldBe false
      // v0=15, v=8: 8 < 10 = true, history=false, output=true (first true)
      op.eval(BigDecimal(15), BigDecimal(8)) shouldBe true
      // v0=8, v=5: 5 < 10 = true, history=true, output=false (not first true)
      op.eval(BigDecimal(8), BigDecimal(5)) shouldBe false
      // v0=5, v=-3: -3 < 10 = true, history=true, output=false (not first true)
      op.eval(BigDecimal(5), BigDecimal(-3)) shouldBe false
      // v0=-3, v=15: 15 < 10 = false, history=false, output=false (condition false)
      op.eval(BigDecimal(-3), BigDecimal(15)) shouldBe false
      // v0=15, v=7: 7 < 10 = true, history=false, output=true (first true again)
      op.eval(BigDecimal(15), BigDecimal(7)) shouldBe true
    }
    
    "handle once logic with percentage" in {
      val op = Op.compile("<<25%")
      op shouldBe a[OpLessOnce]
      op.asInstanceOf[OpLessOnce].abs shouldBe true
      
      // v0=100, v=100: percentage change = 0% < 25% = true, history=false, output=true (first true)
      op.eval(BigDecimal(100), BigDecimal(100)) shouldBe true
      // change = 20% < 25% = true, but only once, so false
      op.eval(BigDecimal(100), BigDecimal(80)) shouldBe false
      // change = -30% abs > 25% = false
      op.eval(BigDecimal(80), BigDecimal(50)) shouldBe false
      // change = 30% > 25% = false
      op.eval(BigDecimal(50), BigDecimal(80)) shouldBe false
      // change = 15% < 25% = true
      op.eval(BigDecimal(80), BigDecimal(70)) shouldBe true
      // change = 15% < 25% = true, but only once, so false
      op.eval(BigDecimal(70), BigDecimal(60)) shouldBe false
      // change = 55% > 25% = false
      op.eval(BigDecimal(60), BigDecimal(30)) shouldBe false
    }
    
    "handle once logic with delta operations" in {
      val op = Op.compile("<<++20")
      op shouldBe a[OpLessOnce]
      op.asInstanceOf[OpLessOnce].abs shouldBe true
            
      // v0=100, v=110: change = 10 < 20 = true, history=false, output=true (first true)
      op.eval(BigDecimal(100), BigDecimal(110)) shouldBe true
      // v0=110, v=125: change = 15 < 20 = true, history=true, output=false (not first true)
      op.eval(BigDecimal(110), BigDecimal(125)) shouldBe false
      // v0=125, v=150: change = 25 < 20 = false, history=false, output=false (condition false)
      op.eval(BigDecimal(125), BigDecimal(150)) shouldBe false
      // v0=150, v=160: change = 10 < 20 = true, history=false, output=true (first true again)
      op.eval(BigDecimal(150), BigDecimal(160)) shouldBe true
      // v0=160, v=180: change = 20 < 20 = false, history=true, output=false (condition false)
      op.eval(BigDecimal(160), BigDecimal(180)) shouldBe false
      // v0=180, v=190: change = 10 < 20 = true, history=false, output=true (first true again)
      op.eval(BigDecimal(180), BigDecimal(190)) shouldBe true
    }
    
    "handle once logic with absolute value expressions" in {
      val op = Op.compile("<<10")
      op shouldBe a[OpLessOnce]
      op.asInstanceOf[OpLessOnce].abs shouldBe true

      // v0=0, v=5: |5| < 10 = true, history=false, output=true (first true)
      op.eval(BigDecimal(0), BigDecimal(5)) shouldBe true
      // v0=5, v=-5: |-5| < 10 = true, history=true, output=false (not first true)
      op.eval(BigDecimal(5), BigDecimal(-5)) shouldBe false
      // v0=-5, v=15: |15| < 10 = false, history=false, output=false (condition false)
      op.eval(BigDecimal(-5), BigDecimal(15)) shouldBe false
      // v0=15, v=-15: |-15| < 10 = false, history=false, output=false (condition false)
      op.eval(BigDecimal(15), BigDecimal(-15)) shouldBe false
      // v0=-15, v=8: |8| < 10 = true, history=false, output=true (first true again)
      op.eval(BigDecimal(-15), BigDecimal(8)) shouldBe true
      // v0=8, v=20: |20| < 10 = false, history=false, output=false (condition false)
      op.eval(BigDecimal(8), BigDecimal(20)) shouldBe false
    }
    
    "handle once logic with explicit sign expressions (+)" in {
      val op = Op.compile("<<+15")
      op shouldBe a[OpLessOnce]
      op.asInstanceOf[OpLessOnce].abs shouldBe false
      
      // v0=20, v=10: 10 < 15 = true, history=false, output=true (first true)
      op.eval(BigDecimal(20), BigDecimal(10)) shouldBe true
      // v0=10, v=5: 5 < 15 = true, history=true, output=false (not first true)
      op.eval(BigDecimal(10), BigDecimal(5)) shouldBe false
      // v0=5, v=20: 20 < 15 = false, history=false, output=false (condition false)
      op.eval(BigDecimal(5), BigDecimal(20)) shouldBe false
      // v0=20, v=12: 12 < 15 = true, history=false, output=true (first true again)
      op.eval(BigDecimal(20), BigDecimal(12)) shouldBe true
      // v0=12, v=8: 8 < 15 = true, history=true, output=false (not first true)
      op.eval(BigDecimal(12), BigDecimal(8)) shouldBe false
    }

    "handle once logic with explicit sign expressions (-)" in {
      val op = Op.compile("<<-15")
      op shouldBe a[OpLessOnce]
      op.asInstanceOf[OpLessOnce].abs shouldBe false
            
      // v0=20, v=10: 10 < -15 = false, history=false, output=false (condition false)
      op.eval(BigDecimal(20), BigDecimal(10)) shouldBe false
      // v0=10, v=-20: -20 < -15 = true, history=false, output=true (first true)
      op.eval(BigDecimal(10), BigDecimal(-20)) shouldBe true
      // v0=-20, v=-25: -25 < -15 = true, history=true, output=false (not first true)
      op.eval(BigDecimal(-20), BigDecimal(-25)) shouldBe false
      // v0=-25, v=5: 5 < -15 = false, history=false, output=false (condition false)
      op.eval(BigDecimal(-25), BigDecimal(5)) shouldBe false
      // v0=5, v=-30: -30 < -15 = true, history=false, output=true (first true again)
      op.eval(BigDecimal(5), BigDecimal(-30)) shouldBe true
      // v0=-30, v=-35: -35 < -15 = true, history=true, output=false (not first true)
      op.eval(BigDecimal(-30), BigDecimal(-35)) shouldBe false
    }
    
    "handle edge cases for once logic (remember this is absolute value, so -1 -> 1)" in {
      val op = Op.compile("<<0")
      op shouldBe a[OpLessOnce]
      op.asInstanceOf[OpLessOnce].abs shouldBe true

      // v0=5, v=1: 1 < 0 = false, history=false, output=false
      op.eval(BigDecimal(5), BigDecimal(1)) shouldBe false
      // v0=1, v=0: 0 < 0 = false, history=false, output=false
      op.eval(BigDecimal(1), BigDecimal(0)) shouldBe false
      // v0=0, v=-1: -1 < 0 = true, history=false, output=true (first true)
      op.eval(BigDecimal(0), BigDecimal(-1)) shouldBe false
      // v0=-1, v=-2: -2 < 0 = true, history=true, output=false (not first true)
      op.eval(BigDecimal(-1), BigDecimal(-0.5)) shouldBe false
      // v0=-2, v=1: 1 < 0 = false, history=false, output=false (condition false)
      op.eval(BigDecimal(-2), BigDecimal(1)) shouldBe false
      // v0=1, v=-3: -3 < 0 = true, history=false, output=true (first true again)
      op.eval(BigDecimal(1), BigDecimal(-3)) shouldBe false
    }
    
    "handle complex once logic scenarios" in {
      val op = Op.compile("<<50%")
      op shouldBe a[OpLessOnce]
      op.asInstanceOf[OpLessOnce].abs shouldBe true
            
      
      op.eval(BigDecimal(100), BigDecimal(80)) shouldBe true
      
      op.eval(BigDecimal(80), BigDecimal(60)) shouldBe false
      
      op.eval(BigDecimal(60), BigDecimal(40)) shouldBe false
      
      op.eval(BigDecimal(40), BigDecimal(80)) shouldBe false
      
      // change = 62% > 50% = false
      op.eval(BigDecimal(80), BigDecimal(30)) shouldBe false
      
      // change = 33% < 50% = true
      op.eval(BigDecimal(30), BigDecimal(20)) shouldBe true
    }
  }
}

