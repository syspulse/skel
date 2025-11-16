#!/bin/bash
# Syntax: classpath.sh [PROJECT_ROOT] [PROJECT_NAME]
# NOTE: It is very importat classpath.sh knows about Project Root and points to ti
#
# If you are in another repo/project use:
# $SKEL_ROOT/classpath.sh $PROJECT_ROOT
# For example:
# $SKEL_ROOT/classpath.sh ../ - generate for local module
# $SKEL_ROOT/classpath.sh ../ project-name - generate for project-name


if [ "$1" != "" ]; then
    ROOT=$1
else
    ROOT=`echo $(dirname $(readlink -f $0))`
fi

if [ "$2" != "" ]; then
    NAME=$1
else
    NAME=`basename $PWD | sed 's/\-/_/g'`
fi

DIR=$PWD

>&2 echo "ROOT=$ROOT"
>&2 echo "NAME=$NAME"

pushd $ROOT
#sbt -error ";project $NAME; export dependencyClasspath" 2>/dev/null >$DIR/CLASSPATH
sbt  -error ";project $NAME; export dependencyClasspath" >$DIR/CLASSPATH
popd
