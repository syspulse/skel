#!/bin/bash
kubectl apply -f https://raw.githubusercontent.com/datashim-io/datashim/master/release-tools/manifests/dlf.yaml
kubectl wait --for=condition=ready pods -l app.kubernetes.io/name=dlf -n dlf

#kubectl apply -f secret.yaml

# Temporary quick work-around
sed -e "s/{AWS_ACCESS_KEY_ID}/${AWS_ACCESS_KEY_ID}/g" -e "s/{AWS_SECRET_ACCESS_KEY}/${AWS_SECRET_ACCESS_KEY}/g" s3-data-template.yml >s3-data.yml

kubectl apply -f s3-data.yml
