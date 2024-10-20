COINS=${1:-coins.json}

cat ${COINS} | jq '{id:.id,symbol:.symbol,name:.name,detail_platforms:.detail_platforms,icon:.image.large}' -c