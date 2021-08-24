#!/bin/bash

INFLUX=influxdb
rm -rf $INFLUX/*
mkdir -p $INFLUX/{db,config}
