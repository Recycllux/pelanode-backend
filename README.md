# pelanode-poc

PelaNode Intelligence Module, hackathon proof of concept (EIT Water Hackathon, 23–25 Oct 2026).
One Spring Boot service (API + replay + seed) that will also serve the React UI built in Lovable.

Specs: P0-11 (execution plan), P0-12 (replay), P0-13 (data and API contract), P0-17 (runbook).
Step IDs in commits (S-xx, B-xx, I-xx) refer to the "Plan pas cu pas" document.

## Run locally

```bash
cd backend
./mvnw spring-boot:run                 # http://localhost:8080/actuator/health
./mvnw test                            # repository round trip + time format tests
```

Local data lives in `backend/.data/` (H2 file, exports). Delete the folder to start clean.
The H2 console is at `/h2-console` on the `local` profile only (JDBC URL `jdbc:h2:file:./.data/pelanode`).

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
backend/                 Spring Boot 3.5, Java 21
  src/main/resources/db/migration/V1__init.sql   schema from P0-13 section 7
  src/main/java/.../domain   records matching the P0-13 entities (snake_case JSON)
  src/main/java/.../repo     JdbcTemplate repositories
data/demo/hack-001/      seed pack (mission, node, track, observations, media, context)
config/.env.example      all PN_* keys, no values
scripts/                 build, run, deploy, reset and smoke-test scripts (added step by step)
```
