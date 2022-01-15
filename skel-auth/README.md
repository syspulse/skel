## skel-auth

Authentication and Authorization

1. OAuth2 __Google__
2. OAuth2 __Twitter__

Basic OAuth2 Flow

<img src="doc/oauth2-flow.jpg" width="750">


----

### OAuth2 Google

Client credentials: [https://console.cloud.google.com/apis/credentials/oauthclient](https://console.cloud.google.com/apis/credentials/oauthclient)

### OAuth2 Twitter

Client credentials: [https://developer.twitter.com/en/portal/projects/$PROJECT/apps/$APP/auth-settings](https://developer.twitter.com/en/portal/projects/$PROJECT/apps/$APP/auth-settings)

__NOTE__: Twitter does not support profile scope and does not return email in v2 userprofile request !


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

