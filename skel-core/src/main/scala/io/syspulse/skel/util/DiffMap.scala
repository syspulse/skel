package io.syspulse.skel.util

class DiffMap[K,V](initial:Map[K,V]) {
  var current = initial

  // return (new,old,out)
  def diff(next:Map[K,V]):(Set[K],Set[K],Set[K]) = {
    val newKeys = next.keySet.diff(current.keySet)
    val outKeys = current.keySet.diff(next.keySet)
    val oldKeys = current.keySet -- outKeys

    current = next
    (newKeys,oldKeys,outKeys)
  }
}

class DiffSet[K](initial:Set[K]) {
  var current = initial

  // return (new,old,out)
  def diff(next:Set[K]):(Set[K],Set[K],Set[K]) = {
    val newKeys = next.diff(current)
    val outKeys = current.diff(next)
    val oldKeys = current -- outKeys

    current = next
    (newKeys,oldKeys,outKeys)
  }
}