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


### Postgres

```
GOD=1 ./run-user.sh --datastore=postgres:// 
```

or


```
GOD=1 ./run-user.sh --datastore=jdbc://postgres
```

or using specific DB in config file (`application-local.conf` by default)

```
GOD=1 ./run-user.sh --datastore=jdbc://postgres-local
```

or using specific config file with env variables settings (e.g. `application-dev.conf`)

```
source ./env.dev
GOD=1 ./run-user.sh --conf=conf/application-dev.conf
```

When running from docker, by default `application.conf` is used (packaged by default).

To use specific config file, you can use `-Dconfig.file=/app/conf/application-dev.conf` or `--conf=/app/conf/application-local.conf`

`-Dconfig.file` - uses only by `app.sh` Docker startup script

`--conf` - is used by skel-config component and can be used with or without docker

To package custom configs, use ```appDockerConfig("walle3","io.syspulse.user.App",Seq("application-dev.conf"))``` in build.sbt


### MySql

```
GOD=1 ./run-user.sh --datastore=mysql://
```

### Database with specific config (application.conf section)

```
GOD=1 ./run-user.sh --datastore=postgres://db1
```

or

```
GOD=1 ./run-user.sh --datastore=jdbc://postgres/db1
```

### Postgres Async

```
GOD=1 ./run-user.sh server --datastore=jdbc://async/postgres/postgres_async
```

### MySql Async

```
GOD=1 ./run-user.sh server --datastore=jdbc://async/mysql/mysql_async
```
