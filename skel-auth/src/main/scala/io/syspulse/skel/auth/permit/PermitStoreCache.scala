package io.syspulse.skel.auth.permit

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import com.typesafe.scalalogging.Logger
import io.syspulse.skel.auth.permit.PermitStoreMem

class PermitStoreCache extends PermitStoreMem

