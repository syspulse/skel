# skel-plugin

## Examples

Run Specific Set of Detectors from `plugins` directory with jars:
```
PLUGINS=`pwd`/plugins ./run-plugin.sh runtime start --datastore='jars://plugins://.*DetectorExt.*'
```