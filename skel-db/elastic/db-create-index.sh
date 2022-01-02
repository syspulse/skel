#!/bin/bash
curl -X PUT --data "@schema-movie.json" -H 'Content-Type: application/json' http://localhost:9200/video

