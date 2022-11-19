# db-cli

Shell for DB research/prototyping. 

More convenient to have it as cli to try something in the future than test/spec

### Syntax

Commands can be executed both as stdin pipe or in interactive shell:
```
echo "connect; select * from TABLE;" | ./db-cli.sh
```

1. connect
```
connect <type> <uri> <user> <pass>
```
type: jdbc, quill

Example:

Connect as Quill:
```
connect quill jdbc:mysql://172.17.0.1:3306/test_db
```

Connect to Docker MySQL as JDBC Driver
```
connect jdbc jdbc:mysql://172.17.0.1:3306/db user pass
```

Connect to local MySQL
```
connect jdbc:mysql://127.0.0.1:3306/test_db test_user test_pass
```

Connect to local Postgres as JDBC Driver
```
connect jdbc:postgresql://localhost:5432/test_db test_user test_pass
```

2. sql

```
sql <statement>
```

Example:
```
sql SELECT * FROM TABLE
```

There is a simplified syntax to use just SELECT:
```
SELECT COUNT(*) FROM TABLE
```

