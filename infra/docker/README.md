# Docker Infra

Docker and Docker Compose

1. [harbor](harbor) - Harbor Private Registry
2. [kafka](kafka) - Kafka Docker-Compose Deployments

----

## Docker log limit

`/etc/docker/daemon.json`:

```
{
  "log-driver": "local",
  "log-opts": {
    "max-size": "100m",
    "max-file": "3"
  }
}
```


Clea, Restart and check

```
echo "" > $(docker inspect --format='{{.LogPath}}' container_name)
service docker restart
docker-compose up -d --force-recreate
```


----

## Credentials

Local credentials:
```
$HOME/.docker/config.json
```

[https://docs.docker.com/engine/reference/commandline/login/#credentials-store](https://docs.docker.com/engine/reference/commandline/login/#credentials-store)
