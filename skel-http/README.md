# skel-http

Skeleton for Simple HTTP Service

## Build & Run

__Dev__
```
sbt
~reStart
```

__Fat jar__
```
sbt assembly
./run.sh
```

__Docker__

Local 
```
sbt docker:publishLocal
./run-docker.sh
```

Publish to [hub.docker.io](hub.docker.io)
```
sbt docker:publish
./run-docker.sh
```
The build will create platform images: __amd64__,__arm64__ (for testing on RP4 clusters)

__NOTE__: Due to RP4, __openjdk8-alpine__ crashes, so migrated to much fatter [openjdk:18-slim](https://hub.docker.com/layers/openjdk/library/openjdk/18-slim/images/sha256-6a92cfcaaf66ea5fac0b7c4b4faecb5ab389485062d3b49670bd792232b36f8b?context=explore)


__ATTENTION__: Disable firewall for connection to docker0 (172.17.0.1) from Container -> Host connections (e.g. Container -> Host(MySql):3306)

----

## Configuration

A lot of flexibility to pass configuration 

Configuration reading priority can be customized. Default:

1. Command Line arguments
2. Environment Variables (easiest to pass into Docker)
3. JVM properties
4. HOCON style Typesafe configuration file (application.conf). 
   Configuration file can be customized with __$SITE__ to choose specific site/environment (e.g. __SITE=tidb__ would load __application-tidb.conf__)
   Default File location is __conf/__

__Example__:

```
run.sh --host 0.0.0.0 --port 8080
```

```
HOST=0.0.0.0 PORT=8080 run.sh
```

```
OPT="-Dhost=0.0.0.0 -Dport=8080" run.sh
```

application.conf
```
host=0.0.0.0
port=8080
```

## Logging

Logging is configured with logback.xml:

1. logback.xml is searched on classpath
1. __conf/logback.xml__ is first on Classpath in __run.sh__
2. Default embedded logger config is set to "off"

### Telemetry API

Exposes Metrics Telemetry information

- [http://{host}:{port}/api/v1/service/telemetry](http://{host}:{port}/api/v1/service/telemetry) - get all Telemetry
- [http://{host}:{port}/api/v1/service/telemetry/{metric}](http://{host}:{port}/api/v1/service/telemetry/{metric}) - get specific Telemetry metric


### Info API

Exposes Service information and Health check

- [http://{host}:{port}/api/v1/service/info](http://{host}:{port}/api/v1/service/info)


### OpenAPI Spec

Embedded API documentation

__API spec__: [http://{host}:{port}/api/v1/doc/swagger.yaml](http://{host}:{port}/api/v1/service/doc/swagger.yaml) or [http://{host}:{port}/api/v1/service/doc/swagger.json](http://{host}:{port}/api/v1/service/doc/swagger.json)

Quick: [http://localhost:8080/api/v1/service/doc/swagger.json](http://localhost:8080/api/v1/service/doc/swagger.json)

__Swagger UI__: [http://host:port/api/v1/service/swagger](http://host:port/api/v1/service/swagger)

Quick: [http://localhost:8080/api/v1/service/swagger](http://localhost:8080/api/v1/service/swagger)

<img src="doc/scr-swagger.png" width="850">

----
## Kubernetes

[kube](kube) - Kubernetes deployment options

Different options allow to access service over different URI:

1. Default [skel-http-ingress-1.yaml](skel-http-ingress-1.yaml)
```
curl http://k1.home.net/api/v1/service/health
```

2. Ingress pathes [skel-http-ingress-2.yaml](skel-http-ingress-2.yaml)
```
curl http://k1.home.net/service/health
```

3. Explicit path with version [skel-http-ingress-3.yaml](skel-http-ingress-3.yaml)
```
curl http://k1.home.net/api/v2/service/health
```

[skel-http.yaml](skel-http.yaml) - Creates 3 Deployemnts/Services/Ingresses