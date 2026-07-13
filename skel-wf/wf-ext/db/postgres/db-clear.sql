-- Truncate the workflow tables owned by this product.
-- detector / detector_schema are EXTERNAL (another product) and are NOT truncated here.
TRUNCATE TABLE workflow_schema, workflow_config, workflow_graf;
