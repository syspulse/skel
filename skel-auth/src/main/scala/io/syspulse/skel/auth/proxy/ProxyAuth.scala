package io.syspulse.skel.auth.proxy

import io.syspulse.skel.auth.Authenticated

case class BasicAuthResult(token: String) extends Authenticated
case class ProxyAuthResult(token: String) extends Authenticated


