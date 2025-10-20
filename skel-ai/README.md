# AI relay

## Example

### OpenIA 

The api key is taken from `OPENAI_API_KEY` by default

```
./run-ai.sh --datastore=openai:// "How are you ?"
```

Use API Key:
```
./run-ai.sh --datastore=openai://$OPENAI_API_KEY@ "How are you ?"
```

or
```
./run-ai.sh --datastore=openai://?apiKey=$OPENAI_API_KEY "How are you ?"
```

Use model and organization:
```
./run-ai.sh --datastore=openai://gpt-4o?org=$OPENAI_ORG "How are you ?"
```

## URI eaxmples

Ask question with system prompt

```
rlwrap ./run-ai.sh ask '--ai=openai://gpt-4o-mini?system=file://SYS-3.txt&prompt=file://PROMPT-1.txt'
```