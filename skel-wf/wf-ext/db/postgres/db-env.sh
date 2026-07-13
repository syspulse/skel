ROOT_USER=postgres
ROOT_PASS=root_pass

export DB_USER=workflow_user
export DB_PASS=workflow_pass
export DB_DATABASE=workflow_db

export DB_HOST=localhost
export DB_URL="jdbc:postgresql://${DB_HOST}:5432/${DB_DATABASE}"
