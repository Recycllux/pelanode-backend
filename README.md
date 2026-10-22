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

## Tests and scripts

```bash
./mvnw test                                   # 18 tests: repositories, replay, review, report, exports, context import
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
src/main/resources/db/migration/V1__init.sql   schema from P0-13 section 7
src/main/resources/static/  prebuilt UI bundle (scripts/build-ui.sh)
src/main/java/.../domain    records matching the P0-13 entities (snake_case JSON)
src/main/java/.../repo      JdbcTemplate repositories
.run/                     IntelliJ run configurations (PelaNode, PelaNode tests)
data/demo/hack-001/       seed pack (mission, node, track, observations, media, context)
config/.env.example       all PN_* keys, no values
scripts/                  demo pack builder, build-ui, run-local, reset-demo, smoke-test, deploy-cloudrun
```

## Organiser data (B-17)

`POST /api/v1/context/import` with `{dataset: {...provenance: "organiser"}, records: [...], mission_id?}`
stores the records (same id and value again is a no-op, a different value is a 409) and re-runs the
association for the mission. The UI shows the dataset with its ORGANISER provenance next to the
simulated series; nothing is overwritten. Records need variable, value, unit, observed_at (with offset), lat, lon.
