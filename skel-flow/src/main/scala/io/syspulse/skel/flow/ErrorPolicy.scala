package io.syspulse.skel.flow

import com.typesafe.scalalogging.Logger

trait ErrorPolicy {
  def repeat:Boolean
}

class FailFastErrorPolicy extends ErrorPolicy {
  def repeat:Boolean = false
}

class RepeatErrorPolicy(delay:Long = 10000L,retries:Long = Long.MaxValue) extends ErrorPolicy {
  var count = retries
  def repeat:Boolean = {
    if(count != 0) {
      Thread.sleep(delay)
      count = count - 1
      true
    } else false
  }

  override def toString = s"${this.getClass.getSimpleName}(${delay},${retries},count=${count})"
}
