# Temporal Notes and Snippets

## Dev Server

```
temporal server start-dev
```

## Namespace

Create namespace:

```
temporal operator namespace create --namespace=space-1 --retention=365d
```

## Retention

```
temporal operator namespace update --name space-1 --retention 30d
```

## TLS/Authorization

```
temporal operator namespace list --address $TEMPORAL_GRPC --tls --grpc-meta "authorization=Bearer $ACCESS_TOKEN_TEMPORAL"
```

```
temporal operator namespace list --address $TEMPORAL_GPRC_INTERNAL --tls --tls-disable-host-verification --grpc-meta "authorization=Bearer $ACCESS_TOKEN_TEMPORAL"
```



## Dev Env

Create JWT

```
AUTH=haas ENV=dev ./jwt-service-account.sh temporal >ACCESS_TOKEN_DEV_TEMPORAL
```

Export

```
source env.temporal
```

Use alias

```
tp operator namespace list
```

Get Workflows from `dev` namespace

```
./run-temporal.sh temporal --engine="temporal://$TEMPORAL_GRPC/dev?tls=ignore&auth=${ACCESS_TOKEN_TEMPORAL}" list
```


