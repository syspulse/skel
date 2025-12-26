# AI

## Model Costs

https://www.helicone.ai/llm-cost
https://yourgpt.ai/tools/llm-comparison-and-leaderboard
https://mem0.ai/llm/calculator

It makes


### OpenIA 

The api key is taken from env by default (e.g. `OPENAI_API_KEY`)

```
./run-ai.sh --ai=openai:// "How are you ?"
```

Use API Key:
```
./run-ai.sh --ai=openai://$OPENAI_API_KEY@ "How are you ?"
```

or
```
./run-ai.sh --ai=openai://?apiKey=$OPENAI_API_KEY "How are you ?"
```

Use model and organization:
```
./run-ai.sh --ai=openai://gpt-4o?org=$OPENAI_ORG "How are you ?"
```

## URI eaxmples

Ask question with system prompt

Use `timeout` parameter for slow models

```
rlwrap ./run-ai.sh ask '--ai=openai://gpt-4o-mini?system=file://SYS-3.txt&prompt=file://PROMPT-1.txt&timeout=60000'
```

Ask to process images with prompt file `PROMPT-2.txt` and return results as `json`:

```
Extract information from provided image and present results as json
```

```
./run-ai.sh ask --images=https://i.redd.it/ak8mw9ovom161.jpg '--ai=openai://gpt-4o-mini?prompt=file://PROMPT-2.txt&output=json_object?timeout=120000
```


### Use Openrouter

```
rlwrap ./run-ai.sh chat '--ai=openrouter://nvidia/nemotron-nano-9b-v2:free?system=file://SYS-3.txt&prompt=file://PROMPT-1.txt&timeout=60000'
```
