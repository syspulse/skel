This directory is needed only when running main from sbt in modules

```
sbt run
```

sbt uses project root directory due to __build.sbt__:

```
initialize ~= { _ =>
  System.setProperty("config.file", "conf/application.conf")
}
```

