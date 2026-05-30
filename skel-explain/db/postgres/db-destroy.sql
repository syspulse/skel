REASSIGN OWNED BY explain_user TO postgres;  -- or some other trusted role
DROP OWNED BY explain_user;

DROP TABLE explaination;

-- SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = 'explain_db';
DROP DATABASE explain_db WITH (FORCE);

DROP USER explain_user;

