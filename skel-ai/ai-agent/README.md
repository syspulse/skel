# AI Agents

## Examples

The api key is taken from `OPENAI_API_KEY` or `PROVIDER_API_KEY` by default

### Simple Prompt Agent

```
source ./env.openai
rlwrap ./run-agent.sh --provider="openai://" --agent=prompt://
```

### ExtAgent

```
rlwrap ./run-agent.sh --provider="openai://gpt-4o?vdb=$OPENAI_VDB&org=$OPENAI_ORG" --agent=agent://ext-agent
```

__NOTE__: `ACCESS_TOKEN_ADMIN` is used by default

```
rlwrap ./run-agent.sh --provider="openai://gpt-4o?vdb=$OPENAI_VDB&org=$OPENAI_ORG&aid=OPENAI_AID_EXT" --agent=agent://ext-agent --service.url=https://api.dev.extractor.live/api/v1
```

## HelpAgent

Re-use Existing Assistant in Playground by ID:

```
source ./env.openai
rlwrap ./run-agent.sh --provider="openai://gpt-4o?vdb=$OPENAI_VDB&org=$OPENAI_ORG&aid=OPENAI_AID_HELP" --agent=agent://help-agent
```

----

## Ext Commands

Add Circulation Supply Detector 

```
./run-agent.sh ext --service.url=https://api.dev.extractor.live/api/v1 detector-add 898 3178 "Circulation%20Supply%20Detector" "Supply-1" COMPLIANCE -1 '{"threshold":0.25}'
```

Add AML Detector with defaults:

```
./run-agent.sh ext --service.url=https://api.dev.extractor.live/api/v1 detector-add 898 3178 "DetectorAML" AML-2
```

## ExtAgent Commands

Add Contract via Agent

```
./run-agent.sh ext-agent --service.url=https://api.dev.extractor.live/api/v1 addContract 898 0x00000000000000000000000000000000000face9 base face-9
```

Add Monitoring Type via Agent
```
./run-agent.sh ext-agent --service.url=https://api.dev.extractor.live/api/v1 addMonitoringType 898 0x19e1f2f837a3b90ebd0730cb6111189be0e1b6d6 Compliance
```

Delete Contract via Agent
```
./run-agent.sh ext-agent --service.url=https://api.dev.extractor.live/api/v1 deleteContract 898 0x00000000000000000000000000000000000face9
```
