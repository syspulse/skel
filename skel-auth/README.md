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

Running skel-auth with Authentication and Authorization disabled (it still goes through all flows):

```
OPT=-Dgod ./run-auth.sh
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


## Simple scenario to test Web2 Authentication and Authorization:

### Generate Admin Token (like prod)

Generate Admin token. Admin user id (with admin permissions) must be in: [conf/permissions-policy-rbac.csv](conf/permissions-policy-rbac.csv)

The token will be saved to __ACCESS_TOKEN_ADMIN__

```
./run-auth.sh jwt admin | tail -1 > ACCESS_TOKEN_ADMIN
```

### Enable all OAuth2 credentials (client/secrets)

```
source auth-IDP-ALL.sh
```

### Start skel-auth

Can be with user emulation and without:

```
./run-auth.sh demo
```
or
```
./run-auth.sh server
```


### Login Wallet

```
./auth-web3-login.sh
```

`ACCESS_TOKEN` file will have JWT (if user is not registered it will be short lived token)

### Verify user access to its own resources:

```
TOKEN=`cat ACCESS_TOKEN` ./skel-user/user-get.sh 00000000-0000-0000-1000-000000000001
```

Verify admin access to all resources:

```
TOKEN=`cat ACCESS_TOKEN_ADMIN` ../skel-user/user-get.sh
```


----

## JWKS

### External JWK

```
./run-auth.sh jwks get https://www.googleapis.com/oauth2/v3/certs
```

### Own JWK

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

<img src="doc/auth-login.png">

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


## RSA Keys

Generate Keypair in PKCS8 format (java.security understans only pkcs8)

```
ssh-keygen -t rsa -b 4096 -m PEM -E SHA512 -f RS512.key -N ""
# Don't add passphrase
openssl rsa -in RS512.key -pubout -outform PEM -out RS512.key.pub
openssl pkcs8 -topk8 -nocrypt -in RS512.key -out RS512.key.pkcs8
```

### Generate Admin JWT with Private Key in file
```
./run-auth.sh jwt admin --jwt.uri=rs512://sk:pkcs8:RS512.key.pkcs8
```

### Run Server with Public key


From x509 Certificate PEM file:
```
./run-auth.sh --jwt.uri=rs512://pk:cer:RS512.key.pub
```

### Generate JWT and Validate it

__NOTE__: Token must not contain any trailing new lines !

```
./run-auth.sh jwt user --jwt.uri=rs512://sk:pkcs8:RS512.key.pkcs8 >/tmp/JWT-0
./run-auth.sh jwt valid --jwt.uri=rs512://pk:cer:RS512.key.pub `cat /tmp/JWT-0`
```

### Validate against JWKS

Login to google and get Profile JWT (__not AccessToken !!!__):

```
./run-auth.sh jwt valid --jwt.uri=https://www.googleapis.com/oauth2/v3/certs `cat /tmp/JWT-Google-Profile`
```

### Keycloak

Keys are here: https://keycloak/admin/master/console/#/realm1/realm-settings/keys

### Run Server with Public key


From Base64 encoded public key file (Keycloak)
```
./run-auth.sh --jwt.uri=rs256://pk:base64:RS256.key
```

From Base64 encoded certificate file (Keycloak)
```
./run-auth.sh --jwt.uri=rs256://cer:base64:RS256.key
```

