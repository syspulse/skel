# demo

## Environment

1. Set up environment

```
source ./env-local.sh
```

2. run compose

```
docker-compose up -d
docler-compose logs -f 
```

## Authentication + Automatic Enrollment

Go to `skel-auth`

1. Try to authenticate with Metamask

```
./auth-web3-login.sh                                  
addr: 0x71CB05EE1b1F506fF321Da3dac38f25c0c9ce6E1                                                                                         
sig: 0x78065fecadefc909b246fa036ea15bfe2a760b2b2d97ce559e10ac0638c90543622517b62aed7fee9acbcc5490ae051bcb09faa5b3a042f54653bc0e842432f21c 
LOCATION: Location: http://localhost:8080/api/v1/auth/eth/callback?code=5TZvDyfGavfzzqYbTZXLfGmx9i26gmHgLrsxXD47n1k
REDIRECT_URI=http://localhost:8080/api/v1/auth/eth/callback?code=5TZvDyfGavfzzqYbTZXLfGmx9i26gmHgLrsxXD47n1k
--- Auth Response:                                                                                                                       
{
  "accesToken": "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzUxMiJ9.eyJleHAiOjE2Njc3Nzc1MjYsImlhdCI6MTY2Nzc3MzkyNiwidWlkIjoiMDAwMDAwMDAtMDAwMC0wMDAwLTAwMDAtMDAwMDAwMDAwMDAwIn0.PIoEsVpFQ92VhDoI48ybLsProlgnT4ftbfAcLw4j8S0YJdvLAzuAnsG82UaNXIo7MMpGsOfqDCKZRTeh74Ppjw",
  "avatar": "",                                                                                                                          
  "email": "",       
  "idToken": "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzUxMiJ9.eyJleHAiOjE2Njc3Nzc1MjYsImlhdCI6MTY2Nzc3MzkyNiwiaWQiOiIwMDAwMDAwMC0wMDAwLTAwMDAtMDAwMC0wMDAwMDAwMDAwMDAifQ.ebTBGSa7-9U6G_gQGa464ZOmnlgHu8Dsftm1-YAZK3CLlulNbRu6KLz3OSPkhKWiR-LocxWb3CXRSbXLvTu3xw",
  "locale": "universe",        
  "name": "",          
  "xid": "0x71CB05EE1b1F506fF321Da3dac38f25c0c9ce6E1"
}                           
TOKEN=eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzUxMiJ9.eyJleHAiOjE2Njc3Nzc1MjYsImlhdCI6MTY2Nzc3MzkyNiwidWlkIjoiMDAwMDAwMDAtMDAwMC0wMDAwLTAwMDAtMDAwMDAwMDAwMDAwIn0.PIoEsVpFQ92VhDoI48ybLsProlgnT4ftbfAcLw4j8S0YJdvLAzuAnsG82UaNXIo7MMpGsOfqDCKZRTeh74Ppjw
xid: 0x71CB05EE1b1F506fF321Da3dac38f25c0c9ce6E1
uid: null                                            
User does not exist -> Enrolling to http://localhost:8083/api/v1/enroll
Enroll: R={"id":"bd0a3543-7e51-498c-b0f5-00cfe87d1e1b","status":"started"}: eid=bd0a3543-7e51-498c-b0f5-00cfe87d1e1b
Enroll: R={"avatar":"http://avatar/a0001.jpg","email":"dev0@syspulse.io","id":"bd0a3543-7e51-498c-b0f5-00cfe87d1e1b","name":"0x71","phase":"EMAIL_ACK","tsCreated":1667773926627,"xid":"0x71CB05EE1b1F506fF321Da3dac38f25c0c9ce6E1"}
```

2. Confirm Email

```
curl  http://localhost:8083/api/v1/enroll/bd0a3543-7e51-498c-b0f5-00cfe87d1e1b/confirm/QKzSiktbJPCqG03v1EyW7vMqft20fx5TOIEo7jvdkMg
```

3. Try to Authenticate again

```
./auth-web3-login.sh 
addr: 0x71CB05EE1b1F506fF321Da3dac38f25c0c9ce6E1
sig: 0x78065fecadefc909b246fa036ea15bfe2a760b2b2d97ce559e10ac0638c90543622517b62aed7fee9acbcc5490ae051bcb09faa5b3a042f54653bc0e842432f21c 
LOCATION: Location: http://localhost:8080/api/v1/auth/eth/callback?code=nyGxv3PuKcyL96_c_Ip7Ba49TWge2qLKR0zsZ2rhQJ8
REDIRECT_URI=http://localhost:8080/api/v1/auth/eth/callback?code=nyGxv3PuKcyL96_c_Ip7Ba49TWge2qLKR0zsZ2rhQJ8
--- Auth Response:   
{                          
  "accesToken": "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzUxMiJ9.eyJleHAiOjE2Njc3Nzc2MjgsImlhdCI6MTY2Nzc3NDAyOCwidWlkIjoiZWM1M2E3OWMtMWM0OC00MDRkLTk3YmMtODQzYjc2N2ViYWVjIn0.EYHzhqcThdKmL7LfY3bfSzq5BMRagki7UZJCeKLW355QaPzInRSrjccrBblfNP0eOuEA94u0ebp-Up26XwOjmg",
  "avatar": "http://avatar/a0001.jpg",
  "email": "dev0@syspulse.io",
  "idToken": "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzUxMiJ9.eyJleHAiOjE2Njc3Nzc2MjgsImlhdCI6MTY2Nzc3NDAyOCwiZW1haWwiOiJhbmRyaXlrQGdtYWlsLmNvbSIsIm5hbWUiOiIweDcxIiwiYXZhdGFyIjoiaHR0cDovL2F2YXRhci9hMDAwMS5qcGciLCJpZCI6IjB4NzFDQjA1RUUxYjFGNTA2ZkYzMjFEYTNkYWMzOGYyNWMwYzljZTZFMSJ9.WIl9M3W
GBXbH25DMHxDvqPl-7JfabS8rY9ZChRjFf7ZSFgEBxbJyQqTlZ-lxEyQySLj8stvveBteZqgGE-MB1g",
  "locale": "universe",                                    
  "name": "0x71",                  
  "uid": "ec53a79c-1c48-404d-97bc-843b767ebaec",
  "xid": "0x71CB05EE1b1F506fF321Da3dac38f25c0c9ce6E1"
}                           
TOKEN=eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzUxMiJ9.eyJleHAiOjE2Njc3Nzc2MjgsImlhdCI6MTY2Nzc3NDAyOCwidWlkIjoiZWM1M2E3OWMtMWM0OC00MDRkLTk3YmMtODQzYjc2N2ViYWVjIn0.EYHzhqcThdKmL7LfY3bfSzq5BMRagki7UZJCeKLW355QaPzInRSrjccrBblfNP0eOuEA94u0ebp-Up26XwOjmg
xid: 0x71CB05EE1b1F506fF321Da3dac38f25c0c9ce6E1   
uid: ec53a79c-1c48-404d-97bc-843b767ebaec
User: ec53a79c-1c48-404d-97bc-843b767ebaec
```


## Enrollment

Go to `skel-enroll`

1. start Enroll

```
./enroll-create.sh 

{"id":"daab59d7-1f10-4485-bbf5-1b0307ae4e91","status":"started"}
```

2. Register email

```
./enroll-email.sh daab59d7-1f10-4485-bbf5-1b0307ae4e91 dev0@syspulse.io

{"email":"dev0@syspulse.io","id":"daab59d7-1f10-4485-bbf5-1b0307ae4e91","name":"","phase":"EMAIL_ACK","tsCreated":1667741377184,"xid":"XID-0001"}
```

3. Confirm email with Code 

Email contains full clickable path:

```
curl http://localhost:8080/api/v1/enroll/daab59d7-1f10-4485-bbf5-1b0307ae4e91/confirm/XCRNIrSOnb_bUwRO5sjxWulq7DL66R4HiWcWNMHlPa4

{"email":"dev0@syspulse.io","id":"daab59d7-1f10-4485-bbf5-1b0307ae4e91","name":"","phase":"CONFIRM_EMAIL_ACK","tsCreated":1667741462403,"xid":"XID-0001"}
```

4. Check status

```
./enroll-get.sh daab59d7-1f10-4485-bbf5-1b0307ae4e91

{"email":"dev0@syspulse.io","id":"daab59d7-1f10-4485-bbf5-1b0307ae4e91","name":"","phase":"FINISH_ACK","tsCreated":1667741462,"uid":"5084db92-3713-498e-be28-40b3da4f6c06","xid":"XID-0001"}
```

5. Check user created

```
SERVICE_URI=http://localhost:8081/api/v1/user ../skel-user/user-get.sh 5084db92-3713-498e-be28-40b3da4f6c06

{"avatar":"","email":"dev0@syspulse.io","id":"5084db92-3713-498e-be28-40b3da4f6c06","name":"","tsCreated":1667741462513,"xid":"XID-0001"}
```

