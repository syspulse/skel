package io.syspulse.skel.util

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.{Duration,FiniteDuration}
import com.typesafe.scalalogging.Logger
import scala.util.{Try,Success,Failure}

/**
  * 
 * SUPPORTED SYNTAX:
 * 
 * 1. BASIC COMPARISON OPERATORS:
 *    = value      -> equals value (exact match)
 *    != value     -> not equals value (exact match)
 * 
 * 2. ABSOLUTE VALUE HANDLING:
 *    > 10         -> v > 10 OR v < -10 (absolute value handling)
 *    < 25         -> |v| < 25 (absolute value handling)
 *    >= 35        -> |v| >= 35 (absolute value handling)
 *    <= 50        -> |v| <= 50 (absolute value handling)
 * 
 * 3. EXPLICIT SIGN HANDLING (no absolute value):
 *    > +10        -> v > 10 (exact value, no absolute handling)
 *    > -15        -> v > -15 (exact value, no absolute handling)
 *    < +30        -> v < 30 (exact value, no absolute handling)
 *    < -20        -> v < -20 (exact value, no absolute handling)
 * 
 * 4. PERCENTAGE OPERATIONS:
 *    > 25%        -> percentage change > 25% (absolute value handling)
 *    < 10%        -> percentage change < 10% (absolute value handling)
 *    >= 50%       -> percentage change >= 50% (absolute value handling)
 *    <= 75%       -> percentage change <= 75% (absolute value handling)
 *    = 100%       -> percentage change = 100% (exact match)
 *    != 200%      -> percentage change != 200% (exact match)
 *    
 *    Note: This calculates the percentage change from previous value to current value.
 *    Formula: (v - v0) / v0 * 100. For example:
 *    - v0=1000, v=700: percentage change = (700-1000)/1000*100 = -30% (decreased by 30%)
 *    - v0=1000, v=1300: percentage change = (1300-1000)/1000*100 = 30% (increased by 30%)
 *    - v0=100, v=100: percentage change = (100-100)/100*100 = 0% (no change)
 * 
 * 5. DELTA OPERATIONS (change from previous value):
 *    > ++100      -> increase > 100 (absolute change)
 *    < --50       -> decrease > 50 (magnitude of decrease)
 *    >= ++30      -> increase >= 30 (absolute change)
 *    <= --25      -> decrease >= 25 (magnitude of decrease)
 *    = ++20       -> increase = 20 (exact change)
 *    != ++40      -> increase != 40 (exact change)
 * 
 * 6. PERCENTAGE DELTA OPERATIONS:
 *    > ++25%      -> increase of percentage change > 25%
 *    < --10%      -> increase of percentage change > 10%
 *    >= ++50%     -> increase of percentage change >= 50%
 *    <= --30%     -> increase of percentage change >= 30%
 *    = ++100%     -> increase of percentage change = 100%
 *    != ++200%    -> increase of percentage change != 200%
 * 
 * 7. SPACES SUPPORT:
 *    > ++ 100     -> increase > 100 (spaces are ignored)
 *    <= -- 25     -> decrease >= 25 (spaces are ignored)
 *    = ++ 30.0 %  -> increase = 30% (spaces are ignored)
 * 
 * 
 * NOTES:
 * - Equality (=) and not equality (!=) always use exact value matching
 * - Comparison operators (>, <, >=, <=) respect absolute value handling
 * - Delta operations (++, --) work with all comparison operators
 * - Percentage operations work with all comparison operators
 * - Spaces are automatically removed during parsing
 * - The abs parameter is automatically detected based on value prefix: + / - will not use absolute value handling
 */

abstract class Op {
  type T = BigDecimal
  def eval(v0:BigDecimal,v:BigDecimal):Boolean

  /**
   * Calculates the percentage change from the previous value (v0) to the current value (v)
   * 
   * @param v0 Previous value
   * @param v Current value  
   * @return Percentage change: (v - v0) / v0 * 100
   *         Returns Double.MaxValue if v0 is 0 to avoid division by zero
   * 
   * Examples:
   * - v0=1000, v=700: returns -30.0 (decreased by 30%)
   * - v0=1000, v=1300: returns 30.0 (increased by 30%)
   * - v0=100, v=100: returns 0.0 (no change)
   * - v0=100, v=50: returns -50.0 (decreased by 50%)
   */
  def perc(v0:BigDecimal,v:BigDecimal):BigDecimal = 
    if(v0 != BigDecimal(0)) 100.0.*((v - v0) / v0) else BigDecimal(Double.MaxValue)
}

case class OpEq(expr:BigDecimal,perc:Boolean=false,delta:Int=0,abs:Boolean=true) extends Op {
  def eval(v0:BigDecimal,v:BigDecimal):Boolean = {
    if(delta == 0) {
      if(perc) perc(v0,v) == expr else v == expr
    } else {
      // For delta operations, calculate the actual change
      if(perc) {
        // For percentage delta operations, calculate percentage change
        if (delta > 0) {
          // For increase operations (++), calculate percentage increase
          val change = v - v0
          val percentageChange = if(v0 != BigDecimal(0)) 100.0.*(change / v0) else BigDecimal(Double.MaxValue)
          percentageChange == expr
        } else {
          // For decrease operations (--), calculate percentage decrease
          val change = v0 - v
          val percentageChange = if(v0 != BigDecimal(0)) 100.0.*(change / v0) else BigDecimal(Double.MaxValue)
          percentageChange == expr
        }
      } else {
        if (delta > 0) {
          // For increase operations (++), check if the actual change equals the Condition
          val change = v - v0
          change == expr
        } else {
          // For decrease operations (--), check if the magnitude of the change equals the Condition
          val change = v - v0
          change.abs == expr
        }
      }
    }
  }
}

abstract class OpMoreLike(expr:BigDecimal,perc:Boolean,delta:Int,abs:Boolean) extends Op {
  def eval(v0:BigDecimal,v:BigDecimal):Boolean = {
    if(delta == 0) {
      //if(perc) perc(v0,v) > expr else v > expr
      (perc,abs) match {
        case (true,true) => perc(v0,v).abs > expr
        case (true,false) => perc(v0,v) > expr
        case (false,true) => v.abs > expr
        case (false,false) => v > expr
      }
    } else {
      // For delta operations, calculate the actual change
      val change = v - v0
      if(perc) {
        // For percentage delta operations, calculate percentage change
        if (delta > 0) {
          // For increase operations (++), calculate percentage increase
          val percentageChange = if(v0 != BigDecimal(0)) 100.0.*(change / v0) else BigDecimal(Double.MaxValue)
          percentageChange > expr
        } else {
          // For decrease operations (--), calculate percentage decrease
          val percentageChange = if(v0 != BigDecimal(0)) 100.0.*((v0 - v) / v0) else BigDecimal(Double.MaxValue)
          percentageChange > expr
        }
      } else {
        if (delta > 0) {
          // For increase operations (++), check if the actual change is greater than the Condition
          change > expr
        } else {
          // For decrease operations (--), check if the magnitude of the decrease is greater than the Condition
          change.abs > expr
        }
      }
    }
  }
}

abstract class OpLessLike(expr:BigDecimal,perc:Boolean,delta:Int,abs:Boolean) extends Op {
  def eval(v0:BigDecimal,v:BigDecimal):Boolean = {
    if(delta == 0) {
      //if(perc) perc(v0,v) < expr else v < expr
      (perc,abs) match {
        case (true,true) => perc(v0,v).abs < expr
        case (true,false) => perc(v0,v) < expr
        case (false,true) => v.abs < expr
        case (false,false) => v < expr
      }
    } else {
      // For delta operations, calculate the actual change
      if(perc) {
        // For percentage delta operations, calculate percentage change
        if (delta > 0) {
          // For increase operations (++), calculate percentage increase
          val change = v - v0
          val percentageChange = if(v0 != BigDecimal(0)) 100.0.*(change / v0) else BigDecimal(Double.MaxValue)
          percentageChange < expr
        } else {
          // For decrease operations (--), calculate percentage decrease
          val change = v0 - v
          val percentageChange = if(v0 != BigDecimal(0)) 100.0.*(change / v0) else BigDecimal(Double.MaxValue)
          percentageChange > expr
        }
      } else {
        if (delta > 0) {
          // For increase operations (++), check if the actual change is less than the Condition
          val change = v - v0
          change < expr
        } else {
          // For decrease operations (--), check if the magnitude of the decrease is greater than the Condition
          (v0 - v).abs > expr
        }
      }
    }
  }
}

case class OpMoreOnce(expr:BigDecimal,perc:Boolean=false,delta:Int=0,abs:Boolean=true) extends OpMoreLike(expr,perc,delta,abs) {    
  var history = false
  override def eval(v0:BigDecimal,v:BigDecimal):Boolean = {
    val r = super.eval(v0,v)
    // Return true only when the result changes from false to true
    val output = r && !history
    history = r
    output
  }
}

case class OpLessOnce(expr:BigDecimal,perc:Boolean=false,delta:Int=0,abs:Boolean=true) extends OpLessLike(expr,perc,delta,abs) {    
  var history = false
  override def eval(v0:BigDecimal,v:BigDecimal):Boolean = {
    val r = super.eval(v0,v)
    // Return true only when the result changes from false to true
    val output = r && !history
    history = r
    output
  }
}

case class OpMore(expr:BigDecimal,perc:Boolean=false,delta:Int=0,abs:Boolean=true) extends OpMoreLike(expr,perc,delta,abs) {
  
}

case class OpLess(expr:BigDecimal,perc:Boolean=false,delta:Int=0,abs:Boolean=true) extends OpLessLike(expr,perc,delta,abs) {
  
}

case class OpMoreEq(expr:BigDecimal,perc:Boolean=false,delta:Int=0,abs:Boolean=true) extends Op {
  def eval(v0:BigDecimal,v:BigDecimal):Boolean = {
    if(delta == 0) {
      //if(perc) perc(v0,v) >= expr else v >= expr
      (perc,abs) match {
        case (true,true) => perc(v0,v).abs >= expr
        case (true,false) => perc(v0,v) >= expr
        case (false,true) => v.abs >= expr
        case (false,false) => v >= expr
      }
    } else {
      // For delta operations, calculate the actual change
      val change = v - v0
      if(perc) {
        // For percentage delta operations, calculate percentage change
        if (delta > 0) {
          // For increase operations (++), calculate percentage increase
          val percentageChange = if(v0 != BigDecimal(0)) 100.0.*(change / v0) else BigDecimal(Double.MaxValue)
          percentageChange >= expr
        } else {
          // For decrease operations (--), calculate percentage decrease
          val percentageChange = if(v0 != BigDecimal(0)) 100.0.*((v0 - v) / v0) else BigDecimal(Double.MaxValue)
          percentageChange >= expr
        }
      } else {
        if (delta > 0) {
          // For increase operations (++), check if the actual change is greater than or equal to the Condition
          change >= expr
        } else {
          // For decrease operations (--), check if the magnitude of the decrease is greater than or equal to the Condition
          change.abs >= expr
        }
      }
    }
  }
}

case class OpLessEq(expr:BigDecimal,perc:Boolean=false,delta:Int=0,abs:Boolean=true) extends Op {
  def eval(v0:BigDecimal,v:BigDecimal):Boolean = {
    if(delta == 0) {
      //if(perc) perc(v0,v) <= expr else v <= expr
      (perc,abs) match {
        case (true,true) => perc(v0,v).abs <= expr
        case (true,false) => perc(v0,v) <= expr
        case (false,true) => v.abs <= expr
        case (false,false) => v <= expr
      }
    } else {
      // For delta operations, calculate the actual change
      if(perc) {
        // For percentage delta operations, calculate percentage change
        if (delta > 0) {
          // For increase operations (++), calculate percentage increase
          val change = v - v0
          val percentageChange = if(v0 != BigDecimal(0)) 100.0.*(change / v0) else BigDecimal(Double.MaxValue)
          percentageChange <= expr
        } else {
          // For decrease operations (--), calculate percentage decrease
          val change = v0 - v
          val percentageChange = if(v0 != BigDecimal(0)) 100.0.*(change / v0) else BigDecimal(Double.MaxValue)
          percentageChange <= expr
        }
      } else {
        // For decrease operations (--), we want to check if the decrease is less than or equal to the Condition
        // So we check if |v0 - v| <= expr (the magnitude of the decrease is less than or equal to the Condition)
        (v0 - v).abs <= expr
      }
    }
  }
}

case class OpEqNot(expr:BigDecimal,perc:Boolean=false,delta:Int=0,abs:Boolean=true) extends Op {
  def eval(v0:BigDecimal,v:BigDecimal):Boolean = {
    if(delta == 0) {
      if(perc) perc(v0,v) != expr else v != expr
    } else {
      // For delta operations, calculate the actual change
      val change = v - v0
      if(perc) {
        // For percentage delta operations, calculate percentage change
        if (delta > 0) {
          // For increase operations (++), calculate percentage increase
          val percentageChange = if(v0 != BigDecimal(0)) 100.0.*(change / v0) else BigDecimal(Double.MaxValue)
          percentageChange != expr
        } else {
          // For decrease operations (--), calculate percentage decrease
          val percentageChange = if(v0 != BigDecimal(0)) 100.0.*((v0 - v) / v0) else BigDecimal(Double.MaxValue)
          percentageChange != expr
        }
      } else {
        if (delta > 0) {
          // For increase operations (++), check if the actual change is not equal to the Condition
          change != expr
        } else {
          // For decrease operations (--), check if the magnitude of the change is not equal to the Condition
          change.abs != expr
        }
      }
    }
  }
}

case class OpOr(op:Seq[Op]) extends Op {
  def eval(v0:BigDecimal,v:BigDecimal):Boolean = op.foldLeft(false)((a,o) => a || o.eval(v0,v))
}

case class OpEmpty() extends Op {
  def eval(v0:BigDecimal,v:BigDecimal):Boolean = true
}

object Op {
  def compile(expr:String):Op = {
    val exrp1 = expr.replaceAll("\\s+","")

    if(exrp1.isEmpty)
      return OpEmpty()

    val perc = exrp1.endsWith("%")
    val expr2 = if(perc) exrp1.dropRight(1) else exrp1

    // Parse operator first, then check for delta operations
    val opExpr = expr2.takeWhile(c => c == '=' || c == '>' || c == '<' || c == '!')
    val remaining = expr2.drop(opExpr.size)
    
    // Check for delta operations after the operator
    val (delta, value) = remaining.take(2) match {
      case "++" => (+1, remaining.drop(2))
      case "--" => (-1, remaining.drop(2))
      case _ => (0, remaining)
    }

    // is absolute value ?
    val abs = value.take(1) match {
      case "-" => false
      case "+" => false
      case _ => true
    }
    
    val op = opExpr match {
      case "=" => OpEq(BigDecimal(value),perc,delta,abs)
      case ">" => OpMore(BigDecimal(value),perc,delta,abs)
      case "<" => OpLess(BigDecimal(value),perc,delta,abs)
      case ">=" => OpMoreEq(BigDecimal(value),perc,delta,abs)
      case "<=" => OpLessEq(BigDecimal(value),perc,delta,abs)
      case "!=" => OpEqNot(BigDecimal(value),perc,delta,abs)

      case ">>" => OpMoreOnce(BigDecimal(value),perc,delta,abs)
      case "<<" => OpLessOnce(BigDecimal(value),perc,delta,abs)

      case "" => {
        // Handle case where no operator is specified (default to equality)
        if (delta != 0) {
          // For delta operations without explicit operator, use appropriate default
          if (delta > 0) OpMore(BigDecimal(value), perc, delta,abs)
          else OpLess(BigDecimal(value), perc, delta,abs)
        } else {
          OpEq(BigDecimal(value), perc, delta,abs)
        }
      }
      case _ => OpEmpty()
    }
    op
  }
}

// =======================================================================================================
abstract class Condition[T](v0:BigDecimal,expr:String="") {
  var ts:Long = -1L
  var last:BigDecimal = v0
  var lastChanged:Boolean = false
  var Condition = expr
  var op = Op.compile(expr)

  override def toString = s"${this.getClass.getSimpleName}(${ts},${last},${Condition})"

  def getCondition = this.Condition
  def setCondition(expr:String) = { 
    Condition = expr 
    op = Op.compile(expr)
  }
  
  def value():T
  
  protected def set(v:BigDecimal,op:Op):Boolean = {
    val changed = op.eval(last,v)
          
    ts = System.currentTimeMillis()
    last = v
    lastChanged = changed
    changed
  }

  def set(v:BigDecimal):Boolean = set(v,op)
  def set(v:BigInt):Boolean = set(BigDecimal(v),op)
  def set(v:Long):Boolean = set(BigDecimal(v),op)
  def set(v:Double):Boolean = set(BigDecimal(v),op)
}

class ConditionDouble(v0:Double,Condition0:String="") extends Condition[Double](v0,Condition0) {
  def value():Double = last.toDouble
}

class ConditionBigInt(v0:BigInt,Condition0:String="",dec:Int = 0) extends Condition[BigInt](BigDecimal(v0),Condition0) {
  def value():BigInt = last.toBigInt
}

class ConditionLong(v0:Long,Condition0:String="") extends Condition[Long](v0,Condition0) {
  def value():Long = last.toLong
}