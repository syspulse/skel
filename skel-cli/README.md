# skel-cli

Embedded cli with editing capabilities. Reads from stdin thus can execute scripts from pipe

Based on jline (supports history, search)

### Syntax

1. Commands delimiter: ';'
2. Prefix command with '!' to make it blocking (applies to awaitable (future) based commands)
   This is needed for scripts execution which require some state update from previous command

### Examples

```
echo "help; exit" | ./run-cli.sh
```

Run future based command (it will exist before commands finish):
```
echo "future 2000" |  ./run-cli.sh
```

Run several futures in parallel and wait (futures will finish):
```
echo "future 100; future 200; future 300; sleep 1000" |  ./run-cli.sh
```

Run several futures one after another, then exit:
```
echo "!future 100; !future 200; !future 300; exit" |  ./run-cli.sh
```

----
## Libraries and Credits

1. jline3: 

