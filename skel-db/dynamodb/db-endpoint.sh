if [ "$AWS_REGION" == "localhost" ]; then
   DB_URI="--endpoint-url ${DB_URI:-http://localhost:8100}"
else
   DB_URI=
fi
