# skel-user

User Service Template

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

## Run 


Postgres

```
GOD=1 ./run-user.sh --datastore=postgres:// --log=DEBUG
```

MySql

```
GOD=1 ./run-user.sh --datastore=mysql:// --log=DEBUG
```
