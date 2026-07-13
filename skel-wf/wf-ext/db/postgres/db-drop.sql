-- Drop only the workflow tables owned by this product.
-- detector / detector_schema are EXTERNAL (another product) and are NOT dropped here.
DROP TABLE IF EXISTS workflow_schema;
DROP TABLE IF EXISTS workflow_config;
DROP TABLE IF EXISTS workflow_graf;
