## skel-auth

Authentication and Authorization

1. OAuth2 __Google__
2. OAuth2 __Twitter__

----

### OAuth2 Google

Client credentials are created here: [https://console.cloud.google.com/apis/credentials/oauthclient](https://console.cloud.google.com/apis/credentials/oauthclient)

### Service

1. Run Service

```
./run-auth --host=localhost --port 8080
```

2. Open Quick Login page: [http://localhost:8080/api/v1/auth/login](http://localhost:8080/api/v1/auth/login)

----

### Investigation Mode


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

