# ingest-proxy

Simple Text lines Proxy from Input Feed (Source) -> Output (Sink)

It operates on string lines only and supports some basic transformations

## Examples

### Proxy Kafka to Websockets

```
./run-proxy.sh -f kafka://broker:9092/topic -o server:ws://0.0.0.0:9300/ws
```

Connnect with clients (beware of websocat buffering):

```
websocat ws://localhost:9300/ws -B 1000000
websocat ws://localhost:9300/ws -B 1000000
```

__ATTENTION__: `websocat` buffers large messages to stdout (default). Use `wscat` or pipe to file and tail:

```
websocat ws://localhost:9300/ws -B 1000000 >output.log
tail -F output.log
```

### Running proxy on obfuscated URI

```
./run-proxy.sh  -o server:ws://0.0.0.0:9300/849dd74c05160b3ebffb2d5547164337a204b7f88d0322d25e18684c0a2d248a
```

### Json-izing output

It is possible to *json-ize* output 

```
./run-proxy.sh -f tail://1.log -o server:ws://0.0.0.0:9300/ws to-json
```

### Running in docker

```
../../tools/run-docker.sh -f kafka://broker:9092/topic/group/latest -o server:ws://0.0.0.0:8080/ws
```

```
websocat ws://localhost:9300/ws -B 1000000 >output.log
```

---
