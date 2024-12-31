
## Installation

It is important to star Ollama with 0.0.0.0:

https://github.com/ollama/ollama/blob/main/docs/faq.md#setting-environment-variables-on-linux

## Model

Location: `/usr/share/ollama`


## Open-WebUI

```
docker run --rm  -p 3000:8080 --add-host=host.docker.internal:host-gateway -v open-webui:/app/backend/data --name open-webui ghcr.io/open-webui/open-webui:main
```

## AMD Tuning

Spec: https://frame.work/products/laptop16-diy-amd-7040?tab=specs:
```
AMD Ryzen™ 9 7940HS
Radeon™ 780M Graphics
```

https://llm-tracker.info/
https://llm-tracker.info/howto/AMD-GPUs
https://community.frame.work/t/vram-allocation-for-the-7840u-frameworks/36613/20
https://github.com/ggerganov/llama.cpp/blob/master/docs/build.md#hip

https://github.com/aidatatools/ollama-benchmark
https://community.frame.work/t/llm-benchmark-amd-7840u/49351/7
https://github.com/ollama/ollama/pull/5426

## Benchmark results:

```
No GPU detected.
Your machine UUID : 7c045b1b-7ec2-5d32-a27c-1d30b50e08f2
-------Linux----------

No GPU detected.
{
    "mistral:7b": "11.47",
    "llama3.1:8b": "7.97",
    "phi3:3.8b": "18.87",
    "qwen2:7b": "10.85",
    "gemma2:9b": "8.48",
    "llava:7b": "10.11",
    "llava:13b": "5.23",
    "uuid": "7c045b1b-7ec2-5d32-a27c-1d30b50e08f2",
    "ollama_version": "0.5.4"
}
----------
```




