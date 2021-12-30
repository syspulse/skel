#!/bin/bash

ID=${1:-M-1788210406}
TABLE=${2:-MOVIE}

source db-cred.sh

source db-endpoint.sh

aws dynamodb query --table-name $TABLE \
   --key-condition-expression "VID = :vid" \
   --expression-attribute-values  "{\":vid\":{\"S\":\"${ID}\"}}" \
   $DB_URI
