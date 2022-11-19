#!/bin/bash

DOMAIN=${1:-docker.u132.net}
PLACE=${2:-Andromeda}
ORG=${3:-syspulse}

openssl genrsa -out ca.key 4096
openssl req -x509 -new -nodes -sha512 -days 3650 \
 -subj "/C=CN/ST=${PLACE}/L=${PLACE}/O=${ORG}/OU=Personal/CN=${DOMAIN}" \
 -key ca.key \
 -out ca.crt

openssl genrsa -out ${DOMAIN}.key 4096

openssl req -sha512 -new \
    -subj "/C=CN/ST=${PLACE}/L=${PLACE}/O=${ORG}/OU=Personal/CN=${DOMAIN}" \
    -key ${DOMAIN}.key \
    -out ${DOMAIN}.csr

DNS_2=`echo ${DOMAIN} |sed '/\..*\./s/^[^.]*\.//'`

cat > v3.ext <<-EOF
authorityKeyIdentifier=keyid,issuer
basicConstraints=CA:FALSE
keyUsage = digitalSignature, nonRepudiation, keyEncipherment, dataEncipherment
extendedKeyUsage = serverAuth
subjectAltName = @alt_names

[alt_names]
DNS.1=${DOMAIN}
DNS.2=${DNS_2}
DNS.3=docker
EOF

mkdir -p /data/cert
mkdir -p /etc/docker/certs.d

openssl x509 -req -sha512 -days 3650 \
    -extfile v3.ext \
    -CA ca.crt -CAkey ca.key -CAcreateserial \
    -in ${DOMAIN}.csr \
    -out ${DOMAIN}.crt

cp ${DOMAIN}.crt /data/cert/
cp ${DOMAIN}.key /data/cert/

openssl x509 -inform PEM -in ${DOMAIN}.crt -out ${DOMAIN}.cert

cp ${DOMAIN}.cert /etc/docker/certs.d/${DOMAIN}:5000/
cp ${DOMAIN}.key /etc/docker/certs.d/${DOMAIN}:5000/
cp ca.crt /etc/docker/certs.d/${DOMAIN}:5000/
