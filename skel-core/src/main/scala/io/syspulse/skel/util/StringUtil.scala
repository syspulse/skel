package io.syspulse.skel.util

import scala.collection.immutable
import scala.util.{Try,Success,Failure}
import scala.util.Random

object StringUtil {
  
  /**
   * Implicit class to add === operator to String for case-insensitive comparison.
   * Usage: "Hello" === "hello" // returns true
   */
  implicit class StringCaseInsensitiveEquals(val self: String) extends AnyVal {
    /**
     * Case-insensitive string comparison.
     * @param that The string to compare with
     * @return true if strings are equal ignoring case, false otherwise
     */
    def ===(that: String): Boolean = {
      if (self == null && that == null) return true
      if (self == null || that == null) return false
      self.equalsIgnoreCase(that)
    }
  }
}
