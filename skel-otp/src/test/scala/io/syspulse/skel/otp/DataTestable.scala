package io.syspulse.skel.otp

import io.jvm.uuid._

trait DataTestable  {

  // assumes tests are not run in parallel
  var testId:UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")

  var otpId1:UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
  var otpId2:UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")

  var userId1:UUID = UUID.fromString("11111111-1111-1111-1111-000000000001")
  
  var userId2:UUID = UUID.fromString("22222222-2222-2222-2222-000000000002")
  var userId2Otp1:UUID = UUID.random
  var userId2Otp2:UUID = UUID.random

}
