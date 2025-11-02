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

### Use Openrouter

```
rlwrap ./run-ai.sh chat '--ai=openrouter://nvidia/nemotron-nano-9b-v2:free?system=file://SYS-3.txt&prompt=file://PROMPT-1.txt&timeout=60000'
```
