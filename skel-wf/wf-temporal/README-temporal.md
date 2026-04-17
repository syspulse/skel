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

## Dev Env

```
temporal operator namespace list --address $TEMPORAL_GRPC --tls --grpc-meta "authorization=Bearer $ACCESS_TOKEN_TEMPORAL"
```

```
temporal operator namespace list --address $TEMPORAL_GPRC_INTERNAL --tls --tls-disable-host-verification --grpc-meta "authorization=Bearer $ACCESS_TOKEN_TEMPORAL"
```
