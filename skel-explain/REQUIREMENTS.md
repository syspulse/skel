# skel-explain


Skel Explain is a service that generates human readable explanations for input data.
Human readable explanation is Markdown output

Input data JSON object with a schema and associated data.
Fields:

- `oid`:String - Owner ID (optional key to find Script rules). If not specified, "default" rules are used (empty key)
- `rid`:String - Rule ID - mandatory to find specific Rule's ScriptFlow
- `schema` - Optinal Schema describing data
- `data` - data which needs explanation

The Objects relationships:

```
Map[oid,Map[rid,ScriptFlow]]
```

Every `oid` points to a Map of RuleId -> ScriptFlow


Input Example:

```
{
    "oid": 490,
    "rid": "DetectorWallet",

    "schema": {
        "type": "object",
        "properties": {
            "address": {
                "type": "string",
                "description": "Address"
            }
        },
        "required": ["address"],
        "title": "Schema",
        "description": "Schema"
    },

    "data": {
        "address": "0x9000000000000000000000000000000000000000",
        "network": "ethereum",
        "name": "Wallet-1",
        "tid": 490,
        "pid": 100,
        "tenant": "Tenant-1",
        "project": "Project-1",
        "timestamp": 1715769600000,
        "severity": 0.25,
        "type": "SENTRY",
        "category": "ALERT",
        "sid": "ext",
        "eid": "0x770bc9a1f7c32cb63a5002b9ceb5c7994cd3af0fc6b2309cb32d3c46f629daa0:1000",
        "did": "DetectorWallet",
        "ver": "0.1.0",
        "description": "Wallet-1",
        "metadata": {
            "tx_hash": "0x770bc9a1f7c32cb63a5002b9ceb5c7994cd3af0fc6b2309cb32d3c46f629daa0",
            "tx_from": "0xA911Ff351B143634Dbc5aF3E204EA074583A83e3",
            "balance": 100,
            "threshold": "> 1000.0",
            "wallet": "0x9000000000000000000000000000000000000000"
        }
    }
}
```

Output is json:

```
{
    "explanation": "...",
    "ts": 17222224445,
    "scripts": ["ScriptJS,ScriptAI],
    "oid": 490
}
```

## Processing Engine

ScriptFlow: [../skel-script](../ske-script)

## API

Endpoint: `/api/v1/explain`

- CRUD for oid: `/api/v1/explain/{oid}/{rid}`
- CRUD for rid  `/api/v1/explain/rule/{rid}
- Explain: `/api/v1/explain/{rid}`

### Authorization

[../skel-dash](skel-dash) contains Authorization (JWT) for oid based operations

If no `oid` is specified, than use default `rid`. Custom `oid/rid` always overrides default `rid`

## Reference

Use as a reference implementation: [../skel-dash](skel-dash) for Object Model, Server, Stores implementations:


- ExplainStoreMem (ref: DashStoreMem)
- ExplainStoreDir (ref: DashStoreDir)
- ExplainStoreDB  (ref: DashStoreDB)

Future async is the primary implementation for `ExplainStore`

Since `ScriptFlow` consists of mulitple Scripts, each individual script data records in Database relationships needs attention how data model is done


## Explain API

Explain API works in several async (Future steps):

1. Find ScriptFlow based on `oid/rid` (if oid is not specified, use default)
2. If ScriptFlow for `oid/rid` not found, find default ScriptFlow by `rid`
3. If ScriptFlow for default `rid` is not found, return error
4. Executre ScriptFlow by passing input object (with schema and data to it).
5. ScriptFlow result is human readable Markdown
6. Server retuns objects with ScriptFlow result in `explanation` field. See Output json example

Example of ScriptFlow result:

```
Sender [0xA911Ff351B143634Dbc5aF3E204EA074583A83e3](https://etherscan.io/address/0xa911ff351b143634dbc5af3e204ea074583a83e3) 
triggered balance drop below threshold on [0x9000000000000000000000000000000000000000](https://etherscan.io/address/0x9000000000000000000000000000000000000000), 
changing above threshold __1000.0__ ETH to __100.0__ ETH. 
Alert fired because observed value __100.0__ ETH, triggered condition `> 1000.0` ETH, delta __-900.0__ ETH under rule threshold."
```


## Run scripts

Use [../skel-dash](skel-dash) for run scripts reference

## Tests

Add comprehensive Tests for 

- default rules (no oid)
- custom rules with oid overriding default rule
- different rules with different result
- primary case for ScriptFlow: ScriptJS and/or ScriptAI script engines in the ScriptFlow
- Tests must include ExplainStoreMem and ExplainStoreDir tests
- Tests must include full HTTP tests with ExplainStoreMem as a store for fast operation


