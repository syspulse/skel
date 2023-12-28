REASSIGN OWNED BY medar_user TO postgres;  -- or some other trusted role
DROP OWNED BY medar_user;

DROP TABLE medar_db;

DROP DATABASE medar_db WITH (FORCE);
DROP USER medar_user;
