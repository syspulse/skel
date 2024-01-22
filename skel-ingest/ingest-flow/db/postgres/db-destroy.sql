REASSIGN OWNED BY ingest_user TO postgres;  -- or some other trusted role
DROP OWNED BY ingest_user;

DROP DATABASE ingest_db WITH (FORCE);
DROP USER ingest_user;

