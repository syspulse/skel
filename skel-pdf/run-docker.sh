#!/bin/bash

export DATA_DIR=/mnt/share/data/skel-pdf
mkdir -p $DATA_DIR

cp -r templates $DATA_DIR/
cp -r projects $DATA_DIR/

chmod -R 777 $DATA_DIR

../tools/run-docker.sh --report.template-dir=/data/templates/H4 --report.issues-dir=/data/projects/Project-1/issues --report.output-file=/data/output.pdf
