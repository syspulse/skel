
## Installation

It is important to star Ollama with 0.0.0.0:

https://github.com/ollama/ollama/blob/main/docs/faq.md#setting-environment-variables-on-linux

## Model

Location: `/usr/share/ollama`


## Open-WebUI

```
docker run --rm  -p 3000:8080 --add-host=host.docker.internal:host-gateway -v open-webui:/app/backend/data --name open-webui ghcr.io/open-webui/open-webui:main
```

