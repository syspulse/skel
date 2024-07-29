# skel-plugin

## Examples

### Jars

Load Specific Set of Plugins from `plugins` directory (jars):
```
PLUGINS=`pwd`/plugins ./run-plugin.sh runtime start --datastore='jars://plugins://.*DetectorExt.*'
```


### Classpath

Load Plugin from current `classpath`:

Full package name must be specified !

__WARNING__: class:// does not support wildcards. Better to use `jars://`

```
PLUGINS=`pwd`/plugins ./run-plugin.sh runtime start --datastore=class://io.syspulse.skel.plugin.DetectorExtTx
```

Mulitple classes are supported:
```
PLUGINS=`pwd`/plugins ./run-plugin.sh runtime start --datastore=class://io.syspulse.skel.plugin.DetectorExtTx,io.syspulse.skel.plugin.DetectorExtBlock
```


