
\c :"DB_DATABASE"

-- create the wf-ext tables AS DB_USER -> owned by DB_USER (ALTER/upgrade works later).
-- (WorkflowStoreDB also runs these CREATE TABLE IF NOT EXISTS at startup as the same role.)
SET ROLE :"DB_USER";

-- ---------------------------------------------------------------------------------------------
-- EXTERNAL tables: DetectorConfig -> "detector", DetectorSchema -> "detector_schema".
-- They are OWNED by a different product and MUST NOT be created/dropped here in shared
-- environments (WorkflowStoreDB never creates them). The reference DDL below (the actual upstream
-- schema) is ONLY for a standalone local/dev database.
--
-- WorkflowStoreDB maps these FLAT, without JOINs: detector.contract_id / schema_id are kept as
-- ids only (DetectorConfigContract / DetectorConfigSchema / destinations are NOT populated).
-- created_at/updated_at are `timestamp` (mapped to/from epoch-ms); tags/network_tags are text[].
-- ---------------------------------------------------------------------------------------------
CREATE TABLE public.detector_schema (
  id serial4 NOT NULL,
  created_at timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updated_at timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
  status text DEFAULT 'ACTIVE' NOT NULL,
  "name" text NOT NULL,
  "version" text NOT NULL,
  "schema" jsonb NOT NULL,
  tags _text DEFAULT '{}' NOT NULL,
  description text DEFAULT '' NOT NULL,
  faq jsonb DEFAULT '"[]"'::jsonb NOT NULL,
  ui_schema jsonb DEFAULT '{}'::jsonb NOT NULL,
  author text NULL,
  icon text NULL,
  network_tags _text DEFAULT '{}' NOT NULL,
  title text NULL,
  CONSTRAINT detector_schema_name_version UNIQUE (name, version),
  CONSTRAINT detector_schema_pkey PRIMARY KEY (id)
);
CREATE TABLE public.detector (
  id serial4 NOT NULL,
  created_at timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updated_at timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
  status text DEFAULT 'ACTIVE' NOT NULL,
  contract_id int4 NOT NULL,
  "name" text NOT NULL,
  "source" text NOT NULL,
  schema_id int4 DEFAULT 1 NOT NULL,
  tags _text DEFAULT '{}' NOT NULL,
  config jsonb DEFAULT '{}' NOT NULL,
  CONSTRAINT detector_pkey PRIMARY KEY (id)
);
-- FKs (upstream): detector.contract_id -> contract(id) ON DELETE CASCADE;
--                 detector.schema_id  -> detector_schema(id)


CREATE TABLE public.tenant (
	id serial4 NOT NULL,
	created_at timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
	updated_at timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
	status text DEFAULT 'ACTIVE'::text NOT NULL,
	"name" text NOT NULL,
	icon text NULL,
	logo_light text NULL,
	dark_logo text NULL,
	CONSTRAINT tenant_pkey PRIMARY KEY (id)
);

CREATE TABLE public.project (
	id serial4 NOT NULL,
	created_at timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
	updated_at timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
	tenant_id int4 NOT NULL,
	"name" text NOT NULL,
	view_tenant_ids _int4 DEFAULT '{}'::integer[] NULL,
	description text NULL,
	icon text NULL,
	tags _text DEFAULT '{}'::text[] NOT NULL,
	legal_name text NULL,
	website text NULL,
	network_status text DEFAULT 'NONE'::text NOT NULL,
	coin_gecko_id text NULL,
	params jsonb DEFAULT '{}'::jsonb NULL,
	"type" varchar(100) NULL,
	meta jsonb NULL,
	CONSTRAINT project_pkey PRIMARY KEY (id)
);

CREATE TABLE public.contract (
	id serial4 NOT NULL,
	created_at timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
	updated_at timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
	project_id int4 NOT NULL,
	address text NULL,
	chain_uid text NULL,
	"name" text NOT NULL,
	abi jsonb NULL,
	tags _text DEFAULT '{}'::text[] NOT NULL,
	address_type text DEFAULT 'CONTRACT'::text NOT NULL,
	category text NULL,
	aml_required bool DEFAULT false NOT NULL,
	"implementation" text NULL,
	impl_abi jsonb NULL,
	icon text DEFAULT ''::text NOT NULL,
	CONSTRAINT contract_pkey PRIMARY KEY (id)
);

-- CREATE UNIQUE INDEX contract_project_id_chain_address_implementation ON public.contract USING btree (project_id, chain_uid, address, implementation) NULLS NOT DISTINCT WHERE (address IS NOT NULL);


-- public.contract foreign keys

-- ALTER TABLE public.contract ADD CONSTRAINT contract_project_id_fkey FOREIGN KEY (project_id) REFERENCES public.project(id) ON DELETE CASCADE;


-- public.project foreign keys

-- ALTER TABLE public.project ADD CONSTRAINT project_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenant(id) ON DELETE CASCADE;

RESET ROLE;