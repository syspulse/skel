CREATE KEYSPACE table_space_1 WITH replication = {'class': 'SimpleStrategy', 'replication_factor': '1'}  AND durable_writes = true;
CREATE table table_space_1.table_1(id bigint PRIMARY KEY, ts bigint, user text, txt text);  
INSERT INTO table_space_1.table_1(id, ts, user, txt) VALUES (1, 30000000, 'user1','test text');
exit;
