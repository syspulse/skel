#!/bin/bash

docker run --rm  --network=host -e OLLAMA_BASE_URL=http://127.0.0.1:11434 -p 8080:8080 --add-host=host.docker.internal:host-gateway -v open-webui:/app/backend/data --name open-webui ghcr.io/open-webui/open-webui:main
