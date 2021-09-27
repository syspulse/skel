MySQL 

### Init

1. Start docker-compose 

Compose will create DB and user/pass (see [docker-compose.yaml](docker-compose.yaml))

2. [db-create.sh](db-create.sh)

Script can be used to re-initialize DB from scratch (e.g after [db-delete.sh](db-delete.sh))

3. Tables and Indexes are created automatically by app/service

