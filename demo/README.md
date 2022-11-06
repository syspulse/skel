# demo

## Flow

1. Set up environment

```
source ./env-local.sh
```

2. run compose

```
docker-compose up -d
docler-compose logs -f 
```

3. start Enroll

```
./enroll-create.sh 

{"id":"daab59d7-1f10-4485-bbf5-1b0307ae4e91","status":"started"}
```

4. Register email

```
./enroll-email.sh daab59d7-1f10-4485-bbf5-1b0307ae4e91 dev0@syspulse.io

{"email":"dev0@syspulse.io","id":"daab59d7-1f10-4485-bbf5-1b0307ae4e91","name":"","phase":"EMAIL_ACK","tsCreated":1667741377184,"xid":"XID-0001"}
```

5. Confirm email with Code 

Email contains full clickable path:

```
curl http://localhost:8080/api/v1/enroll/daab59d7-1f10-4485-bbf5-1b0307ae4e91/confirm/XCRNIrSOnb_bUwRO5sjxWulq7DL66R4HiWcWNMHlPa4

{"email":"dev0@syspulse.io","id":"daab59d7-1f10-4485-bbf5-1b0307ae4e91","name":"","phase":"CONFIRM_EMAIL_ACK","tsCreated":1667741462403,"xid":"XID-0001"}
```

6. Check status

```
./enroll-get.sh daab59d7-1f10-4485-bbf5-1b0307ae4e91

{"email":"dev0@syspulse.io","id":"daab59d7-1f10-4485-bbf5-1b0307ae4e91","name":"","phase":"FINISH_ACK","tsCreated":1667741462,"uid":"5084db92-3713-498e-be28-40b3da4f6c06","xid":"XID-0001"}
```

7. Check user created

```
SERVICE_URI=http://localhost:8081/api/v1/user ../skel-user/user-get.sh 5084db92-3713-498e-be28-40b3da4f6c06

{"avatar":"","email":"dev0@syspulse.io","id":"5084db92-3713-498e-be28-40b3da4f6c06","name":"","tsCreated":1667741462513,"xid":"XID-0001"}
```

