#!/bin/bash
# Read guidelines: https://www.elastic.co/guide/en/elasticsearch/reference/current/docker.html
cwd=`pwd`
CONF="-v $cwd/elasticsearch.yml:/usr/share/elasticsearch/config/elasticsearch.yml"
docker run --name elastic --rm -p 9200:9200 -p 9300:9300 -e "discovery.type=single-node" docker.elastic.co/elasticsearch/elasticsearch:7.6.2
docker logs -f elastic
