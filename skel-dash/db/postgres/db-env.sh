ROOT_USER=postgres
ROOT_PASS=root_pass

export DB_USER=dash_user
export DB_PASS=dash_pass
export DB_DATABASE=dash_db

export DB_HOST=localhost

export DB_NAME=$DB_DATABASE
export DB_URL="jdbc:postgresql://${DB_HOST}/$DB_DATABASE"
