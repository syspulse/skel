#!/bin/bash

influx bucket update -i $INFLUX_BUCKET -r 180d
