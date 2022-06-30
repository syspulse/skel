# skel-auth

Authentication and Authorization

1. OAuth2 __Google__
2. OAuth2 __Twitter__
3. OAuth2 __Web3__ (Metamask)
4. Proxy M2M (Federated trusted flow)

Basic OAuth2 Flow

<img src="doc/oauth2-flow.jpg" width="750">

## Service

skel-auth is both IDP and Authentication/Auhtorization Service. It provides IDP for web3 accounts

```
source ./auth-cred.sh
./run-auth.sh
```

2. Open Quick Test page: [http://localhost:8080/api/v1/auth/login](http://localhost:8080/api/v1/auth/login)


----

## Google

Client credentials: [https://console.cloud.google.com/apis/credentials/oauthclient](https://console.cloud.google.com/apis/credentials/oauthclient)


```
export GOOGLE_AUTH_CLIENT_ID="XXXX-XXXX.apps.googleusercontent.com"
export GOOGLE_AUTH_CLIENT_SECRET="XXXX"
```

## Twitter

Client credentials: [https://developer.twitter.com/en/portal/projects/$PROJECT/apps/$APP/auth-settings](https://developer.twitter.com/en/portal/projects/$PROJECT/apps/$APP/auth-settings)

```
export TWITTER_AUTH_CLIENT_ID="XXXX-XXXX.apps.googleusercontent.com"
export TWITTER_AUTH_CLIENT_SECRET="XXXX"
```

__NOTE__: Twitter does not support profile scope and does not return email in v2 userprofile request !


## Web3 (Metamask)

OAuth2 flow for login with Ethereum Signing

<img src="doc/oauth2-web3.png">

Simple command line cli for testing:
```
./auth-web3.sh

HTTP/1.1 201 Created
Content-Length: 484
Content-Type: application/json
Date: Wed, 29 Jun 2022 17:23:06 GMT
Server: akka-http/10.2.9

{
    "accesToken": "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzUxMiJ9.eyJleHAiOjE2NTY1MjY5ODYsImlhdCI6MTY1NjUyMzM4NiwidWlkIjoweDcxQ0IwNUVFMWIxRjUwNmZGMzIxRGEzZGFjMzhmMjVjMGM5Y2U2RTF9.2g8CfvVh34XnaKMZYjvQEl1k6qqTaQh9FUUmCw6LEjNIb7y3RGsKBpdyL_OV8wIIioPN1_7uxbtBAEZu_NcDAg",
    "avatar": "profile.avatar",
    "eid": "0x71CB05EE1b1F506fF321Da3dac38f25c0c9ce6E1",
    "email": "profile.email",
    "idToken": "",
    "locale": "universe",
    "name": "0x71CB05EE1b1F506fF321Da3dac38f25c0c9ce6E1",
    "uid": "57f59a60-efc7-4b3b-8613-14b0ddd3282c"
}

```

AccessToken embeds User clam with address


### JWKS

WIP to add JWT support

```
http http://localhost:8080/api/v1/auth/jwks
HTTP/1.1 200 OK
Accept-Ranges: bytes
Content-Length: 547
Content-Type: application/json
Date: Thu, 30 Jun 2022 14:59:25 GMT
ETag: "c4400181b4f00dc0"
Last-Modified: Thu, 30 Jun 2022 14:07:20 GMT
Server: akka-http/10.2.9

{
    "keys": [
        {
            "alg": "RS512",
            "e": "AQAB",
            "kid": "sig-1656596770",
            "kty": "RSA",
            "n": "iRCkJ_ReXPL_GyMBAtINFX4_spByAfOPK5AEdg21UpZqN7qxY7ROTo2uw_8LjiufjSexFIQIDUkA6RVIDZkExHSgQH6hYnlOLx45zfcWx5Cm3dbpAYO5SHmo-Mp7wsS0dnnH8bdPo2uZVrsIKD0aoLkON9xyr1_2rePjZjYjZGvqX0wUbbe_RKIlocyDTjr9uA2tdGaFb_KjSZ4nMIDoqxXhrVQv4Hfe7WTugIM6UlfCAwGeH8f4l3Yg9gTdBQBC5uX852IGqpf5Kp6xo-2L3s69vfM8l6dwqWs07gLMknfAw3aw3UKkwvQOHn5iR7TkPdsJlmEzQcOGpKE9lxxAFQ",
            "use": "sig"
        }
    ]
}
```

----

## Proxy M2M

The flow for (Service -> skel-auth -> Service) authentication.

This modes federates authentication to configured external IDP with configurable mapping for Headers and Body.

---

## auth-login frontend

<img src="doc/auth-login.jpg">

Modes:
1. Client - Redirection to Client
2. Server - Redirection to skel-auth (test mode)

---


## Investigation Mode


Redirect URI: [http://localhost:3001/callback](http://localhost:3001/callback)

1. Save credentials to __auth-cred-idp.sh__

```
export AUTH_CLIENT_ID="XXXX-XXXX.apps.googleusercontent.com"
export AUTH_CLIENT_SECRET="XXXX"
```

2. Run investigator

```
SITE=idp ./run-investigate.sh
```

3. Open [http://localhost:3001](http://localhost:3001)

