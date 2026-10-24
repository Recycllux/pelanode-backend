# pelanode-backend

PelaNode Intelligence Module, hackathon proof of concept (EIT Water Hackathon, 23–25 Oct 2026).
One Spring Boot service (API + replay + seed) that also serves the React UI from
`Recycllux/pelanode-mission-portal` (built in Lovable).

Specs: P0-11 (execution plan), P0-12 (replay), P0-13 (data and API contract), P0-17 (runbook).
Step IDs in commits (S-xx, B-xx, I-xx) refer to the "Plan pas cu pas" document.

## Run it (no setup needed)

Requirements: Java 21. Nothing else: Maven comes with the wrapper, the demo data is in the repo,
the database is an embedded H2 file, and the UI bundle is already built.

- **IntelliJ IDEA:** File → Open → pick this folder (it is a Maven project). Choose the run
  configuration **PelaNode** at the top right and press Run. **PelaNode tests** runs all tests.
- **Command line:** `./mvnw spring-boot:run` (Windows: `mvnw.cmd spring-boot:run`).

Then open <http://localhost:8080> for the portal, or <http://localhost:8080/api/v1/missions/HACK-001>.
Press Start on the time bar to replay HACK-001 (about 25 s at demo speed).

Without `PN_WRITE_API_KEY` set, POST endpoints are open and a warning is logged; that is fine on
your own machine. Data paths are relative (`data/demo/hack-001`) and are looked up from the working
directory upwards, so it does not matter which folder the app is started from.

Local data lives in `.data/` (H2 file, exports). Delete the folder to start clean.
The H2 console is at `/h2-console` on the `local` profile only (JDBC URL `jdbc:h2:file:./.data/pelanode`).

## Database

Embedded H2 by default (file in `.data/`, PostgreSQL compatibility mode): nothing to install.
For PostgreSQL, create an empty database and put the connection in `config/.env`
(copy `config/.env.example`); the app reads that file by itself:

```
PN_DB_URL=jdbc:postgresql://localhost:5432/pelanode
PN_DB_USER=postgres
PN_DB_PASSWORD=...
```

Liquibase creates the tables at startup (`src/main/resources/db/changelog`). To change the schema,
add a new SQL file and a new changeSet; never edit one that has already run. Run the storyboard
test on PostgreSQL with `PN_TEST_PG_URL=jdbc:postgresql://localhost:5432/pelanode_test
PN_TEST_PG_USER=postgres PN_TEST_PG_PASSWORD=... ./mvnw test` (that database is wiped first).

## Tests and scripts

```bash
./mvnw test                                   # 21 tests: repositories, replay, review, report, exports, context import, Danube forecast
scripts/run-local.sh [--clean]                # loads config/.env if present; --clean wipes .data first
scripts/reset-demo.sh [base_url]              # POST /demo/reset, needs PN_WRITE_API_KEY if the server has one
scripts/smoke-test.sh [base_url]              # P0-17 checklist: events, context, review, report, export, UI
scripts/build-ui.sh [path-to-mission-portal]  # rebuild the UI bundle into src/main/resources/static
GCP_PROJECT=<id> scripts/deploy-cloudrun.sh   # Cloud Build + Cloud Run, europe-west3, 1 instance
```

At startup the service logs one `SERVICE_STARTED` line with profile, simulated mode, replay speed
and whether a write key is set; check it after every deploy.

## Updating the UI

The UI's source is `Recycllux/pelanode-mission-portal`. After changes there, run
`scripts/build-ui.sh ../pelanode-mission-portal` (needs bun or npm) and commit
`src/main/resources/static`. The bundle is built in `http` mode with an empty base URL, so it calls
`/api/v1` on the same origin.

## Run the image

```bash
docker build -t pelanode-poc:dev .
docker run --rm -p 8080:8080 --env-file config/.env pelanode-poc:dev
```

The image uses the `cloud` profile: H2 in `/tmp`, so a restart equals a reset (P0-17, P17-03).

## Configuration

Every setting is a `PN_*` environment variable; see `config/.env.example` and P0-11 section 7.
`PN_WRITE_API_KEY` is a secret: keep it in `config/.env` (ignored by Git) or in Cloud Run settings.

## Layout

```
pom.xml, mvnw             Spring Boot 3.5, Java 21 (Maven project at the root)
src/main/resources/db/changelog/  Liquibase changelog; sql/001-init.sql = schema from P0-13 section 7
src/main/resources/static/  prebuilt UI bundle (scripts/build-ui.sh)
src/main/java/.../domain    records matching the P0-13 entities (snake_case JSON)
src/main/java/.../repo      JdbcTemplate repositories
.run/                     IntelliJ run configurations (PelaNode, PelaNode tests)
data/demo/hack-001/       seed pack (mission, node, track, observations, media, context)
config/.env.example       all PN_* keys, no values
scripts/                  demo pack builder, build-ui, run-local, reset-demo, smoke-test, deploy-cloudrun
```

## Danube level forecast

Organiser data from `Share/Danube` (daily levels, 61 bulletin stations from Germany to Tulcea plus 13 Bulgarian
automatic gauges, 2021-06-06 to 2021-11-09) is cleaned into `data/danube/` by
`python3 scripts/build_danube_pack.py <Share/Danube dir>`; see `data/danube/PROVENANCE.md`.
All endpoints are read-only. Dates can be given in that 2021 window or on the common 2025 scenario timeline
(`2025-MM-DD`, mapped back to 2021); every response carries a `scenario` block and dated rows carry `scenario_date`.

- `GET /api/v1/danube/stations`: stations, data coverage, demo thresholds
- `GET /api/v1/danube/profile?date=`: level, 24 h change and anomaly against the station median, by river km, for one day
- `GET /api/v1/danube/series?station=&from=&to=`: level and water temperature for one station
- `GET /api/v1/danube/forecast?station=|mission_id=&as_of=&horizon=`: 1 to 6 day level forecast with an error band,
  low-water / high-water / rapid-change alerts, the upstream gauges driving it and a PelaNode deployment
  recommendation; `mission_id` picks the forecastable station nearest the mission area (HACK-001: Giurgiu)
- `GET /api/v1/danube/backtest?station=`: walk-forward skill against persistence and how many days ahead each
  threshold crossing was warned
- `GET /api/v1/danube/point?lat=&lon=&as_of=&month=`: real context for one position (a PelaNode event or any
  point): the nearest Danube gauge with its level, 24 h change and thresholds, plus precipitation and soil moisture
  from the nearest climate grid cell with its rank across Bulgaria and the 12 month series

The model is a ridge regression on 1 and 3 day level changes at upstream gauges (50 to 1,000 km upstream),
refitted for every as-of date on data available at that date only. Giurgiu: mean error 1.6 cm at 1 day and
13.8 cm at 6 days, against 8.1 and 39.8 cm for "no change". Thresholds are the station's P10/P90, a stand-in until
the navigation and flood-alert levels are configured.

## Connected river intelligence (P0-19 v1.3)

PelaNode turns fragmented river-monitoring data into connected downstream intelligence: it learns hydrological
propagation between stations, estimates where and when authorities should monitor downstream, and flags candidate
environmental anomalies for investigation.

- `GET /api/v1/danube/lags?source=&min_class=&max_km=`: without `source`, edges between consecutive gauges of the two
  lower-Danube chains (bulletin, Bulgarian automatic; below the Iron Gates, km 943); with `source`, every downstream
  gauge of its chain within `max_km` (default 320). Each edge: overlap `n`, gap rate, level and Δh correlation, the
  lags that maximise them, a monitoring window (range in days) and a quality class A/B/C (demo tuning).
  The lag is the historical propagation of the river-level signal, never a pollutant travel time.
- `GET /api/v1/danube/anomalies?station=&from=&to=&threshold=&include_one_sided=&include_influenced=`: candidate
  water-temperature anomalies on the Bulgarian gauges: residual against the mean of the two neighbouring temperature
  stations, minus the station's usual offset, scored with a robust z (σ = 1.4826 × MAD, threshold 4, demo tuning).
  Adjacent flags of opposite sign on the same day keep the largest |z| as `primary` and mark the others
  `neighbour_influenced`. End stations (one neighbour) are hidden unless `include_one_sided=true`. Candidates are
  `review_required`, with no source attribution.
- `/api/v1/danube/point` adds `downstream_windows` from the nearest gauge; `/api/v1/danube/stations` adds `chain`,
  `has_temperature`, `temperature_coverage`, `in_propagation_model` and `contextual_sources` (Iskar).

### 2025 scenario timeline and Iskar handoff

Iskar discharge (organiser files, native 2025) is cleaned into `data/iskar/` by
`python3 scripts/build_iskar_pack.py <Share dir>`; see `data/iskar/PROVENANCE.md`. It does not overlap the 2021
Danube data natively, so it is shown only as a **scenario-aligned** tributary input on the common 2025 timeline.

- `GET /api/v1/scenario/datasets`: compatibility state per dataset (DEMONSTRATED, SCENARIO-ALIGNED, CONTEXT ONLY,
  OPTIONAL CONTEXT, FUTURE) and the shared Danube–Iskar window (2025-07-12 to 2025-11-09)
- `GET /api/v1/scenario/tributaries/iskar?from=&to=`: Novi Iskar and Orehovitsa discharge (gaps stay missing), the
  confluence node (~km 637) and the days whose rise over the previous day is at least 5 m³/s (demo tuning)
- `POST /api/v1/scenario/tributaries/iskar/event` `{"station_id","scenario_date"}`: a tributary input event handed off
  at the confluence to the windows of the first gauge below it on each chain (Corabia, Zagrazhden; Nikopol is class C
  and listed as excluded). 422 outside the shared window or on a data gap. It computes a view and stores nothing, so
  it does not need the write key.

No Iskar–Danube correlation is computed: the handoff is topology, not measured propagation.

Climate grids (precipitation and soil moisture over Bulgaria, monthly 2024) come from the organiser's Earth Engine
exports, built into `data/climate/` by `python3 scripts/build_climate_pack.py <Share/gee_output dir>`; see
`data/climate/PROVENANCE.md`. They are matched to the Danube date by calendar month.

## Organiser data (B-17)

`POST /api/v1/context/import` with `{dataset: {...provenance: "organiser"}, records: [...], mission_id?}`
stores the records (same id and value again is a no-op, a different value is a 409) and re-runs the
association for the mission. The UI shows the dataset with its ORGANISER provenance next to the
simulated series; nothing is overwritten. Records need variable, value, unit, observed_at (with offset), lat, lon.
