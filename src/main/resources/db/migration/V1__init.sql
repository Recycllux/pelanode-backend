-- PelaNode H0 schema. Source: P0-13 section 7 (Integrated Spec), with two H2 fixes:
--  * context_record.value is stored as norm_value (VALUE is reserved in H2; P0-13 note).
--  * media_asset.observation_id has no foreign key: the media registry is seeded before
--    the replay ingests observations (P0-17 section 5 load order).

CREATE TABLE mission (
  mission_id      VARCHAR(64) PRIMARY KEY,
  name            VARCHAR(200) NOT NULL,
  scenario        VARCHAR(200) NOT NULL,
  region          VARCHAR(200) NOT NULL,
  node_id         VARCHAR(64)  NOT NULL,
  mode            VARCHAR(16)  NOT NULL,
  planned_start   TIMESTAMP WITH TIME ZONE NOT NULL,
  planned_end     TIMESTAMP WITH TIME ZONE NOT NULL,
  area_bbox       VARCHAR(200),                -- JSON [min_lon,min_lat,max_lon,max_lat]
  event_id_start  INTEGER DEFAULT 1,
  data_origin     VARCHAR(16)  NOT NULL,
  schema_version  VARCHAR(8)   NOT NULL
);

CREATE TABLE node (
  node_id          VARCHAR(64) PRIMARY KEY,
  node_type        VARCHAR(16) NOT NULL,
  camera_ids       VARCHAR(400) NOT NULL,      -- JSON array
  firmware_version VARCHAR(64),
  schema_version   VARCHAR(8)  NOT NULL
);

CREATE TABLE device_status (
  status_id         VARCHAR(64) PRIMARY KEY,
  node_id           VARCHAR(64) NOT NULL REFERENCES node(node_id),
  mission_id        VARCHAR(64) NOT NULL REFERENCES mission(mission_id),
  observed_at       TIMESTAMP WITH TIME ZONE NOT NULL,
  battery_pct       DOUBLE PRECISION,
  connectivity      VARCHAR(16) NOT NULL,
  gnss_status       VARCHAR(16) NOT NULL,
  surface_camera    VARCHAR(16) NOT NULL,
  underwater_camera VARCHAR(16) NOT NULL,
  storage_pct       DOUBLE PRECISION,
  data_origin       VARCHAR(16) NOT NULL
);
CREATE INDEX ix_status_mission_time ON device_status(mission_id, observed_at);

CREATE TABLE track_point (
  track_point_id VARCHAR(64) PRIMARY KEY,
  mission_id     VARCHAR(64) NOT NULL REFERENCES mission(mission_id),
  node_id        VARCHAR(64) NOT NULL REFERENCES node(node_id),
  captured_at    TIMESTAMP WITH TIME ZONE NOT NULL,
  lat            DOUBLE PRECISION NOT NULL,
  lon            DOUBLE PRECISION NOT NULL,
  h_acc_m        DOUBLE PRECISION,
  speed_mps      DOUBLE PRECISION,
  heading_deg    DOUBLE PRECISION,
  source         VARCHAR(16) NOT NULL,
  quality_flag   VARCHAR(16) NOT NULL,
  data_origin    VARCHAR(16) NOT NULL
);
CREATE INDEX ix_track_mission_time ON track_point(mission_id, captured_at);

CREATE TABLE observation (
  observation_id        VARCHAR(64) PRIMARY KEY,
  mission_id            VARCHAR(64) NOT NULL REFERENCES mission(mission_id),
  node_id               VARCHAR(64) NOT NULL REFERENCES node(node_id),
  captured_at           TIMESTAMP WITH TIME ZONE NOT NULL,
  lat                   DOUBLE PRECISION,
  lon                   DOUBLE PRECISION,
  h_acc_m               DOUBLE PRECISION,
  position_source       VARCHAR(16) NOT NULL,
  speed_mps             DOUBLE PRECISION,
  heading_deg           DOUBLE PRECISION,
  roll_deg              DOUBLE PRECISION,
  pitch_deg             DOUBLE PRECISION,
  source_sensor         VARCHAR(32) NOT NULL,
  perspective           VARCHAR(16) NOT NULL,
  camera_id             VARCHAR(64),
  sequence_no           INTEGER,
  paired_observation_id VARCHAR(64),
  pair_gap_ms           INTEGER,
  media_id              VARCHAR(64),
  media_status          VARCHAR(16) NOT NULL,
  quality_score         DOUBLE PRECISION,
  quality_flags         VARCHAR(400),           -- JSON array
  sensors               VARCHAR(1000),          -- JSON object
  data_origin           VARCHAR(16) NOT NULL,
  schema_version        VARCHAR(8)  NOT NULL
);
CREATE INDEX ix_obs_mission_time ON observation(mission_id, captured_at);

CREATE TABLE media_asset (
  media_id       VARCHAR(64) PRIMARY KEY,
  observation_id VARCHAR(64) NOT NULL,
  kind           VARCHAR(16) NOT NULL,
  uri            VARCHAR(400) NOT NULL,
  width_px       INTEGER,
  height_px      INTEGER,
  mime_type      VARCHAR(64) NOT NULL,
  provenance     VARCHAR(16) NOT NULL,
  licence_note   VARCHAR(400),
  sha256         VARCHAR(64)
);

CREATE TABLE model_output (
  model_output_id  VARCHAR(64) PRIMARY KEY,
  observation_id   VARCHAR(64) NOT NULL REFERENCES observation(observation_id),
  task             VARCHAR(32) NOT NULL,
  taxonomy         VARCHAR(64) NOT NULL,
  class            VARCHAR(64) NOT NULL,
  confidence       DOUBLE PRECISION NOT NULL,
  bbox             VARCHAR(100),               -- JSON [x,y,w,h]
  mask_uri         VARCHAR(400),
  model_id         VARCHAR(64) NOT NULL,
  model_version    VARCHAR(64) NOT NULL,
  inference_origin VARCHAR(16) NOT NULL,
  processed_at     TIMESTAMP WITH TIME ZONE NOT NULL,
  processing_ms    INTEGER,
  review_required  BOOLEAN NOT NULL,
  schema_version   VARCHAR(8) NOT NULL
);
CREATE INDEX ix_mo_observation ON model_output(observation_id);

CREATE TABLE event (
  event_id              VARCHAR(64) PRIMARY KEY,
  mission_id            VARCHAR(64) NOT NULL REFERENCES mission(mission_id),
  node_id               VARCHAR(64) NOT NULL REFERENCES node(node_id),
  event_type            VARCHAR(64) NOT NULL,
  perspective           VARCHAR(16) NOT NULL,
  observation_id        VARCHAR(64) NOT NULL REFERENCES observation(observation_id),
  paired_observation_id VARCHAR(64),
  model_output_id       VARCHAR(64) NOT NULL REFERENCES model_output(model_output_id),
  captured_at           TIMESTAMP WITH TIME ZONE NOT NULL,
  lat                   DOUBLE PRECISION,
  lon                   DOUBLE PRECISION,
  attention             VARCHAR(16) NOT NULL,
  attention_reasons     VARCHAR(400) NOT NULL,  -- JSON array
  review_status         VARCHAR(16) NOT NULL,
  current_label         VARCHAR(64),
  cluster_id            VARCHAR(64),
  created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
  data_origin           VARCHAR(16) NOT NULL,
  schema_version        VARCHAR(8)  NOT NULL
);
CREATE INDEX ix_event_mission_time   ON event(mission_id, captured_at);
CREATE INDEX ix_event_mission_review ON event(mission_id, review_status);
CREATE INDEX ix_event_latlon         ON event(lat, lon);

CREATE TABLE event_evidence (                  -- supporting observations after de-duplication
  event_id        VARCHAR(64) NOT NULL REFERENCES event(event_id),
  observation_id  VARCHAR(64) NOT NULL REFERENCES observation(observation_id),
  model_output_id VARCHAR(64) REFERENCES model_output(model_output_id),
  PRIMARY KEY (event_id, observation_id)
);

CREATE TABLE dataset_source (
  dataset_id  VARCHAR(64) PRIMARY KEY,
  provider    VARCHAR(100) NOT NULL,
  name        VARCHAR(200) NOT NULL,
  variables   VARCHAR(400) NOT NULL,          -- JSON array
  format      VARCHAR(100),
  licence     VARCHAR(400),
  provenance  VARCHAR(16) NOT NULL,
  status      VARCHAR(16) NOT NULL
);

CREATE TABLE context_record (
  context_record_id VARCHAR(128) PRIMARY KEY,
  dataset_id        VARCHAR(64) NOT NULL REFERENCES dataset_source(dataset_id),
  provider          VARCHAR(100) NOT NULL,
  variable          VARCHAR(64)  NOT NULL,
  norm_value        DOUBLE PRECISION,           -- JSON field "value"
  unit              VARCHAR(32)  NOT NULL,
  source_value      VARCHAR(64)  NOT NULL,
  source_unit       VARCHAR(32)  NOT NULL,
  observed_at       TIMESTAMP WITH TIME ZONE NOT NULL,
  source_timestamp  VARCHAR(64)  NOT NULL,
  station_id        VARCHAR(64),
  station_name      VARCHAR(200),
  lat               DOUBLE PRECISION,
  lon               DOUBLE PRECISION,
  geometry_type     VARCHAR(16)  NOT NULL,
  provenance        VARCHAR(16)  NOT NULL,
  licence_note      VARCHAR(400),
  imported_at       TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_ctx_dataset_var_time ON context_record(dataset_id, variable, observed_at);
CREATE INDEX ix_ctx_latlon           ON context_record(lat, lon);

CREATE TABLE event_context (
  event_id          VARCHAR(64)  NOT NULL REFERENCES event(event_id),
  context_record_id VARCHAR(128) NOT NULL REFERENCES context_record(context_record_id),
  relation          VARCHAR(16)  NOT NULL,
  distance_km       DOUBLE PRECISION,
  time_offset_min   DOUBLE PRECISION NOT NULL,
  rule_id           VARCHAR(64)  NOT NULL,
  associated_at     TIMESTAMP WITH TIME ZONE NOT NULL,
  PRIMARY KEY (event_id, context_record_id)
);

CREATE TABLE review_action (
  review_id        VARCHAR(64) PRIMARY KEY,
  event_id         VARCHAR(64) NOT NULL REFERENCES event(event_id),
  action           VARCHAR(16) NOT NULL,
  new_label        VARCHAR(64),
  note             VARCHAR(1000),
  reviewer         VARCHAR(100) NOT NULL,
  request_id       VARCHAR(64) NOT NULL UNIQUE,
  resulting_status VARCHAR(16) NOT NULL,
  created_at       TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_review_event_time ON review_action(event_id, created_at);

CREATE TABLE alert (
  alert_id   VARCHAR(64) PRIMARY KEY,
  event_id   VARCHAR(64) NOT NULL REFERENCES event(event_id),
  rule_id    VARCHAR(64) NOT NULL,
  message    VARCHAR(400) NOT NULL,
  status     VARCHAR(16) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE report (
  report_id       VARCHAR(64) PRIMARY KEY,
  mission_id      VARCHAR(64) NOT NULL REFERENCES mission(mission_id),
  format          VARCHAR(16) NOT NULL,
  file_uri        VARCHAR(400) NOT NULL,
  summary         VARCHAR(4000) NOT NULL,     -- JSON object
  simulation_note VARCHAR(1000) NOT NULL,
  generated_at    TIMESTAMP WITH TIME ZONE NOT NULL
);
