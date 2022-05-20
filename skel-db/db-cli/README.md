# db-cli

Shell for DB research/prototyping. 

More convenient to have it as cli to try something in the future than test/spec

### Syntax

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

Connect as JDBC Driver
```
connect jdbc jdbc:mysql://172.17.0.1:3306/test_db
```
```
connect jdbc:mysql://172.17.0.1:3306/test_db
```


2. sql

```
sql <statement>
```

Example:
```
sql SELECT * FROM TABLE
```