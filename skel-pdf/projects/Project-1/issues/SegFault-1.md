# Segmenation fault

ID: __PRJ1-35__

Severity: __3__

Status: __New__

Reference: http://github.com/project/code

## Description

Start of the bug description


File: [http://github.com/project/code/file.cpp](http://github.com/project/code/file.cpp)

1. Problem-1
2. Problem-2

```
let matches = App::new("keccak")
        .version("0.0.1")
        .arg(Arg::with_name("DATA").index(1).required(false))
        .arg(Arg::with_name("x").short("x").help("hex input"))
        .arg(Arg::with_name("b").short("b").help("binary output"))
        .arg(Arg::with_name("e").short("e").help("Ethereum signature style"))
        .get_matches();
```

Should be ok now !

----

## Recommendation

Fix this function:

```
code {
    val s = "String"
}
```
