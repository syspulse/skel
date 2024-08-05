# AI relay

## Example

### OpenIA 

The api key is taken from `OPENAI_API_KEY` by default

```
./run-ai.sh --datastore=openai:// "How are you ?"
```

Use model:
```
./run-ai.sh --datastore=openai://$OPENAI_API_KEY@gpt-4o "How are you ?"
```

