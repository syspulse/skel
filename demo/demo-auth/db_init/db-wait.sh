HOST=${1:-postgres}
PORT=${2:-5432}

source $PWD/db-env.sh

wait() {
    while true; do
    if ! echo "\echo ok" | PGPASSWORD=$ROOT_PASS psql --host=$1 --port=$2 --username=$ROOT_USER
    then
        echo "$1 not available, retrying..."
        sleep 1
    else
        echo "$1 is available"
        sleep 1
        break;
    fi
    done;
}

wait $HOST $PORT
