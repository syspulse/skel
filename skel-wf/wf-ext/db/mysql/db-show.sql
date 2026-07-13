USE workflow_db;
SELECT 'workflow_schema' AS tbl, count(*) AS n FROM workflow_schema
UNION ALL SELECT 'workflow_config', count(*) FROM workflow_config
UNION ALL SELECT 'workflow_graf',   count(*) FROM workflow_graf;
