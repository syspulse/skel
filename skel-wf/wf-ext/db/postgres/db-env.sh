ROOT_USER=postgres
ROOT_PASS=root_pass

export DB_USER=${DB_USER:-workflow_user}
export DB_PASS=${DB_PASS:-workflow_pass}
export DB_DATABASE=${DB_DATABASE:-workflow_db}

export DB_HOST=${DB_HOST:-localhost}
export DB_URL="jdbc:postgresql://${DB_HOST}:5432/${DB_DATABASE}"
