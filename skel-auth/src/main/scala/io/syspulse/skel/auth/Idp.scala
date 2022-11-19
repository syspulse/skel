package io.syspulse.skel.auth

import io.syspulse.skel.auth.oauth2.OAuth2
import io.syspulse.skel.auth.jwt.Jwks
import io.syspulse.skel.auth.oauth2.GoogleOAuth2
import io.syspulse.skel.auth.oauth2.TwitterOAuth2

trait Idp extends OAuth2 with Jwks

// object IdpStore {
//   val idps = Map(
//     GoogleOAuth2.id -> (new GoogleOAuth2(redirectUri)).withJWKS(),
//     TwitterOAuth2.id -> (new TwitterOAuth2(redirectUri))
//   )
//   log.info(s"idps: ${idps}")
// }

