package io.syspulse.skel.auth.permit

import scala.concurrent.Future
import scala.collection.immutable

import com.typesafe.scalalogging.Logger
import io.syspulse.skel.auth.permit.PermitStoreMem

class PermitStoreCache extends PermitStoreMem
