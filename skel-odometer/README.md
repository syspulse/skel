# skel-odometer

Meters Counters Store

It is specifically designed for high-volume increment counters to measure some activity over life-time.

Counters are persisted as single value and cached on read and writes/updates.


## Info

https://aws.amazon.com/de/blogs/database/implement-resource-counters-with-amazon-dynamodb/


## Storage

### Setup DB

1. MySQL

```
cd ./db/mysql
../../../skel-db/mysql/db-create.sh
```

2. Postgres

```
cd ./db/postgres
../../../skel-db/postgres/db-create.sh
```

3. Redis

No setup is needed

## Run 


### Postgres

```
GOD=1 ./run-user.sh --datastore=postgres:// 
```

### MySql

```
GOD=1 ./run-user.sh --datastore=mysql://
```

### Redis

```
GOD=1 ./run-user.sh --datastore=redis://localhost:6379/0
```
