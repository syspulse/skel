#!/bin/bash

pushd db/postgres
../../../skel-db/postgres/db-destroy.sh
../../../skel-db/postgres/db-create.sh
popd

