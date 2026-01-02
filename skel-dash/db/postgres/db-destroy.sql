REASSIGN OWNED BY dash_user TO postgres;  -- or some other trusted role
DROP OWNED BY dash_user;

DROP TABLE dash_chat;

-- SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = 'dash_db';
DROP DATABASE dash_db WITH (FORCE);

DROP USER dash_user;

