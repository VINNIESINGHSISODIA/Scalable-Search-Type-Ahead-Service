# Scalable Search Type-Ahead Service

A production-shaped **search autocomplete (type-ahead) backend** built as a system-design study — the kind of service that powers the live suggestion dropdown under a search bar. As you type, it returns the top-5 most popular completions for your prefix, served with low latency.

**Stack:** Java 17 · Spring Boot · PostgreSQL · Redis · Docker · Nginx

---

## What it does

- Returns the **top-5** suggestions for a prefix, ranked by popularity (search frequency).
- Suggestions fire only from the **3rd character** onward (the first two are too ambiguous to be useful).
- Learns from real searches — popular queries rise in the rankings over time.
- Uses **time decay** so trending queries outrank stale all-time favourites.
- Runs **load-balanced** across multiple stateless app instances.

---

## Architecture

```
                 http://localhost:8081
                         │
                    ┌────▼────┐
                    │  Nginx  │   load balancer (round-robin)
                    └────┬────┘
              ┌──────────┴──────────┐
         ┌────▼────┐           ┌────▼────┐
         │  app1   │           │  app2   │   stateless Spring Boot instances
         └────┬────┘           └────┬────┘
              └──────────┬──────────┘
          ┌──────────────┴──────────────┐
     ┌────▼─────┐                  ┌─────▼────┐
     │PostgreSQL│                  │  Redis   │
     │ HM1 + HM2│                  │ cache +  │
     │ (truth)  │                  │  buffer  │
     └──────────┘                  └──────────┘
```

### The two-map model

| Map | Stored in | Keyed by | Holds |
|-----|-----------|----------|-------|
| **HM1** — frequency store | PostgreSQL | `term` | every term's total search frequency (the full universe, source of truth) |
| **HM2** — suggestion store | PostgreSQL | `prefix` | the **top-5** terms per prefix (with their frequencies) |

**Redis plays two roles:**
- **Read cache** — a sorted set per prefix (`ta:sug:<prefix>`, scored by frequency) that serves reads without touching Postgres.
- **Write buffer** — a hash (`ta:pending`) that accumulates search counts before they're flushed to the database.

**Why two maps?** HM1 tracks *every* term — including the thousands that aren't currently in any top-5 but might become popular. HM2 holds only the winners for fast reads. When a term in HM1 grows popular enough, it's **promoted** into HM2's top-5, pushing out the weakest (whose count stays safe in HM1).

---

## How it works

### Read path (super low latency)
```
GET /suggestions?prefix=mic
  → enforce "≥3 characters"
  → check Redis cache (ZREVRANGE ta:sug:mic 0 4)
      ├─ HIT  → return top-5 (no database)
      └─ MISS → read HM2 from Postgres → populate cache → return
```

### Write path (batched + sampled)
```
POST /frequency {"query":"microsoft"}   (fired on Enter — a completed search)
  → sampling gate (count only a fraction of writes)
  → increment pending counter in Redis (no DB write yet)
  → when a term crosses the THRESHOLD:
       - update HM1 frequency in Postgres
       - re-evaluate HM2 top-5 for each of the term's prefixes (promotion)
       - invalidate the affected Redis cache keys
```
Batching behind a threshold cuts database writes dramatically (analytically ~500× at the production threshold), turning a read+write-heavy workload into a read-heavy one.

### Time decay
A daily job divides every frequency by 1.1, so a search today counts more than one from yesterday — keeping trending queries on top and preventing all-time favourites from dominating forever.

---

## Running it

### Option A — Docker (full stack, one command)
```bash
docker compose up --build
```
Starts PostgreSQL, Redis, two app instances, and Nginx. Open **http://localhost:8081** for the live search box.

Watch the load balancing:
```bash
for i in {1..6}; do curl -s http://localhost:8081/api/v1/whoami; echo; done
```
The instance ID alternates between the two apps.

Stop:
```bash
docker compose down      # add -v to also wipe the database
```

### Option B — Local (IntelliJ)
Requires JDK 17, Maven, and a local PostgreSQL + Redis.
1. Create a `typeahead` database in PostgreSQL.
2. Set credentials in `src/main/resources/application.properties`.
3. Run `TypeaheadApplication`, open **http://localhost:8080**.

---

## API

| Method | Endpoint | Purpose |
|--------|----------|---------|
| `GET`  | `/api/v1/suggestions?prefix=mic` | top-5 suggestions for a prefix |
| `POST` | `/api/v1/frequency` `{"query":"microsoft"}` | record a completed search |
| `POST` | `/api/v1/admin/decay` | manually trigger time decay (demo) |
| `GET`  | `/api/v1/whoami` | which app instance served the request |

Example:
```bash
curl "http://localhost:8081/api/v1/suggestions?prefix=mic"
# ["microsoft","michael obama","microwave","microsoft office","michael jackson"]
```

---

## Configuration (`application.properties`, prefix `typeahead.*`)

| Property | Demo | Production | Meaning |
|----------|------|-----------|---------|
| `write.threshold` | `3` | `500` | pending count before a term flushes to the DB |
| `write.sampling-rate` | `1.0` | `0.2` | fraction of writes counted |
| `decay.factor` | `1.1` | `1.1` | daily frequency divisor |
| `seed-on-start` | `true` | `false` | whether this instance seeds demo data |

In Docker these are set per-instance via environment variables (e.g. only `app1` seeds).

---

## Design notes — demo vs. production

This project is deliberately simplified for demonstration. In production:

- **Seeding** → data accumulates from real traffic; schema via Flyway/Liquibase migrations (which lock and run once), not an in-app seeder.
- **Load balancing** → a Kubernetes Deployment + Service (ClusterIP) with a HorizontalPodAutoscaler, discovering instances dynamically — instead of a hardcoded Nginx upstream.
- **PostgreSQL** → primary + read replicas, sharded by key, with automatic failover.
- **Redis** → Redis Cluster (sharded) with master + replicas and Sentinel failover. Since HM2 is a derived cache, a full Redis loss is recoverable by rebuilding from Postgres.
- **Config/secrets** → a secrets manager instead of plaintext.

---

## Key design decisions

- **Availability over consistency** — a slightly stale suggestion list is fine; the service must stay up and fast. Eventually consistent for popular terms.
- **HM2 is a derived cache** — always rebuildable from HM1, so it can be invalidated and regenerated freely.
- **Invalidate-on-write, rebuild-on-read** — flushes delete affected cache keys rather than updating them, keeping the cache simple and always correct.
- **Stateless app tier** — all state lives in Postgres/Redis, so the app scales horizontally behind a load balancer.

---

## Author

**Vini Singh Rajput** — MTech CSE, IIIT-Bangalore