#!/bin/bash

# Run the Actor Proxy HTTP Server
# This server uses Future to communicate with an Actor for SSE requests

echo "Starting HttpServerActorProxy on port 8301..."

# Set environment variables
export PORT=8301
export HOST=0.0.0.0
export DELAY=500

# Run the server
cd /home/andreyk/prj/syspulse/skel/skel
sbt "project tools" "runMain io.syspulse.skel.tools.HttpServerActorProxy" 