-- Row counts for the workflow tables owned by this product.
SELECT 'workflow_schema' AS tbl, count(*) FROM workflow_schema
UNION ALL SELECT 'workflow_config', count(*) FROM workflow_config
UNION ALL SELECT 'workflow_graf',   count(*) FROM workflow_graf;

-- external (present only in a standalone workflow db):
-- SELECT 'detector' AS tbl, count(*) FROM detector
-- UNION ALL SELECT 'detector_schema', count(*) FROM detector_schema;
