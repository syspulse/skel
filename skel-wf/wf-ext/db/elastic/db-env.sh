# Local OpenSearch 2.13. Default is HTTP :9200 (DISABLE_SECURITY_PLUGIN=true).
# For HTTPS self-signed: ES_HOST=https://localhost:9200 ./db-create.sh
# Credentials are NEVER hardcoded in the other scripts — they live here (env).

export ES_HOST=${ES_HOST:-http://localhost:9200}
export ES_USER=${ES_USER:-${ELASTIC_USER:-admin}}
export ES_PASSWORD="${ES_PASSWORD:-${ELASTIC_PASS:-Abcd_1234#}}"
export ES_INDEX=${ES_INDEX:-detector-alert-search}

es_curl() {
  curl -skS -u "${ES_USER}:${ES_PASSWORD}" "$@"
}
export ES_USER=${ES_USER:-${ELASTIC_USER:-admin}}
export ES_PASSWORD="${ES_PASSWORD:-${ELASTIC_PASS:-Abcd_1234#}}"
export ES_INDEX=${ES_INDEX:-detector-alert-search}

es_curl() {
  curl -skS -u "${ES_USER}:${ES_PASSWORD}" "$@"
}
