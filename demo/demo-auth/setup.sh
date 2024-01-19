#!/bin/bash

export DEMO_DATA=/mnt/share/data/skel/demo/demo-auth/data

echo "Data: $DEMO_DATA"

source env-local.sh

mkdir -p $DEMO_DATA
mkdir -p $DEMO_DATA/db_data

mkdir -p $DEMO_DATA/store

# Initialize auth dir://
mkdir -p $DEMO_DATA/store/auth
mkdir -p $DEMO_DATA/store/auth/cred
mkdir -p $DEMO_DATA/store/auth/rbac/permissions
mkdir -p $DEMO_DATA/store/auth/rbac/users

mkdir -p $DEMO_DATA/store/notify

# authorizations
mkdir -p $DEMO_DATA/auth
echo "UNKNOWN_TOKEN" >$DEMO_DATA/auth/ACCESS_TOKEN_SERVICE


# link

ln -s $DEMO_DATA ./data


