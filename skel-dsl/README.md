# DSL

## ATTENTION

### Resources

https://stackoverflow.com/questions/49319402/how-to-call-function-or-module-using-scala-scriptengine

https://stackoverflow.com/questions/70945320/how-to-compile-and-execute-scala-code-at-run-time-in-scala3

https://stackoverflow.com/questions/73911801/how-can-i-run-generated-code-during-script-runtime


__Most of scala evals/interpreters do not work in sbt ScalaTests !__

[https://www.evrete.org/](https://www.evrete.org/)
[https://www.eclipse.org/Xtext](https://www.eclipse.org/Xtext)
[https://github.com/TypeFox/theia-xtext-sprotty-example](https://github.com/TypeFox/theia-xtext-sprotty-example)


----

## DSL

### Scala 

Since Scala types are not dynamic in scripts, must be cast:

```
./run-dsl.sh scala 'i.asInstanceOf[Int] + 200'
./run-dsl.sh scala-script 'i.asInstanceOf[Int] + 200'
./run-dsl.sh scala-interpreter 'i.asInstanceOf[Int] + 200'
```

### JavaScript

By default `js` uses GraalVM polyglot engine

```
./run-dsl.sh js 'i + 200'
```

Run with `nashorn`

```
./run-dsl.sh nashorn "i + 20"
```



