#!/bin/bash

./run-auth.sh --auth.uri=http://localhost:12090/v1 --auth.headers.mapping="EVAL:X-Violet-App-Id:{client_id},EVAL:X-Violet-App-Secret:{client_secret}" $@