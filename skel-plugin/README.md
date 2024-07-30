# skel-plugin

## Plugin locations

* Directory with jars
* Classpath


### Manifest

Search for Class in all jars in specific directory which have Manifest defined

Regexp is supproted to load mulitple plugins.

__NOTE__: `skel-plug` does not support injecting jar into classpath. For plugin to be instantiated, the Jar must be also in classpath !
In current implementation there is not much of a difference from `class://`

Load Specific Set of Plugins from `plugins` directory (jars):
```
PLUGINS=`pwd`/plugins ./run-plugin.sh runtime start --datastore='manifest://plugins://'
```


### Jars

Search for Class in all jars in specific directory. 

Regexp is supproted to load mulitple plugins.

__NOTE__: `skel-plug` does not support injecting jar into classpath. For plugin to be instantiated, the Jar must be also in classpath !
In current implementation there is not much of a difference from `class://`

Load Specific Set of Plugins from `plugins` directory (jars):
```
PLUGINS=`pwd`/plugins ./run-plugin.sh runtime start --datastore='jars://plugins://.*DetectorExt.*'
```


### Classpath (default)

Load Plugin from current `classpath` 

Full package name must be specified !

__WARNING__: class:// does not support wildcards or regexp. Better to use `jars://`

```
./run-plugin.sh runtime start --datastore=class://io.syspulse.skel.plugin.DetectorExtTx
```

Mulitple classes are supported:
```
./run-plugin.sh runtime start --datastore=class://io.syspulse.skel.plugin.DetectorExtTx,io.syspulse.skel.plugin.DetectorExtBlock
```


