if [ "$AWS_REGION" == "localhost" ]; then
   DB_URL="--endpoint-url ${DB_URL:-http://localhost:8100}"
else
   DB_URL=
fi
