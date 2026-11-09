# Live Sports Match Data Aggregator (FanCode Backend Assignment)

A high-performance, resilient Spring Boot service that ingests live cricket match data from two heterogeneous providers (Push via Webhook and Poll via HTTP GET) and serves a unified, normalized set of client-facing read APIs.

---

## Features

- **Heterogeneous Ingestion**:
  - **Provider A (Push)**: Webhook endpoint (`POST /api/webhooks/provider-a`) with delivery idempotency.
  - **Provider B (Poll)**: Scheduled/on-demand poller for scores (`GET /provider/matches/:id/score`) and commentary (`GET /provider/matches/:id/commentary`).
- **Strict Ordering & Monotonicity Rules**:
  - Snapshot `(sequence, revision)` is applied only when strictly newer (lexicographically greater).
  - Out-of-order snapshots are rejected without discarding late commentary balls.
- **Commentary Revision Rules**:
  - Stable `ballId`. Higher revisions replace stored commentary text; equal or lower revisions are ignored.
  - Client commentary is strictly sorted by commentary `sequence` (not arrival time). Missing sequences and sequence gaps are handled cleanly.
- **Deterministic Precedence**:
  - Configured Primary Provider (`provider-a`) with seamless fallback to Secondary Provider (`provider-b`).
  - Sequence numbers are never compared across providers.
- **Durable Storage Across Restarts**:
  - Embedded file-based H2 database (`./data/fancodedb`). All matches, scores, and commentary survive application restarts.
- **Deterministic Mocks**:
  - Provider A generator executing the full 6-step worked scenario from the specification.
  - Provider B mock supporting `initial`, `unchanged`, `newer_score`, `new_ball`, `stale`, and `error_503` states.
- **Zero-Dependency Execution**:
  - Multi-stage `Dockerfile` and `docker-compose.yml` for containerized runs.
  - Embedded Maven wrapper (`./mvnw` / `mvnw.cmd`) for native runs.
- **Interactive Swagger UI / OpenAPI 3.0**:
  - Full interactive browser UI at `http://localhost:8080/swagger-ui/index.html` to execute and test all APIs directly.

---

## Interactive Swagger / OpenAPI Documentation

Once the server is running, you can test and explore all endpoints directly in your browser:

- **Interactive Swagger UI**: [**`http://localhost:8080/swagger-ui/index.html`**](http://localhost:8080/swagger-ui/index.html) *(or `http://localhost:8080/swagger-ui.html`)*
- **OpenAPI 3.0 JSON Spec**: [**`http://localhost:8080/v3/api-docs`**](http://localhost:8080/v3/api-docs)

From the Swagger UI, you can click **"Try it out"** on:
1. **Client Read APIs**: View live scores, matches list, and commentary in sequence.
2. **Push Ingestion**: Send custom webhook deliveries with snapshots and commentary.
3. **Mock Provider A**: Click `POST /mock/provider-a/play` to run the entire 6-step timeline with a single click.
4. **Mock Provider B**: Toggle scenarios (`newer_score`, `new_ball`, `error_503`, `stale`) and trigger poll cycles.

---

## System Requirements

- **Option A (Native)**: Java 21 LTS installed (Maven is optional; bundled `./mvnw` is included).
- **Option B (Docker)**: Docker 20+ and Docker Compose (zero Java or Maven installation needed).

---

## Quick Start Guide

### 1. Run via Docker Compose (Recommended for reviewers)

```bash
# Build and run container
docker compose up --build
```
The server will start on port `8080`. Persistent database files are mounted to `./data`.

To shut down:
```bash
docker compose down
```

---

### 2. Run Natively via Maven Wrapper

#### Linux / macOS:
```bash
./mvnw clean spring-boot:run
```

#### Windows (PowerShell / CMD):
```powershell
.\mvnw.cmd clean spring-boot:run
```

---

## Running the Automated Test Suite

The test suite validates all 8 mandatory specification scenarios plus HTTP controller contracts:
1. Ordered ingestion
2. Duplicate push delivery (state remains unchanged)
3. Older snapshot arriving after a newer one (late ball still returned in sequence order)
4. Commentary correction (higher revision replaces text, lower revision ignored)
5. Repeated identical polls (no duplicate balls created)
6. Stale poll response (older tuple loses to newer stored snapshot)
7. Provider error (HTTP 503) leaving last good state in place
8. Restart against the same persistent database (persisted state survives JVM termination)

#### Run tests via Maven Wrapper:
```bash
./mvnw test
```
*(Windows: `.\mvnw.cmd test`)*

Expected result:
```
[INFO] Results:
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

## Exercising a Match Locally (Curl Examples)

The canonical match identifier is **`match-1001`**, which maps to:
- Provider A: `a-match-1001`
- Provider B: `b-match-1001`

### Step 1: Run Provider A Worked Push Scenario

Trigger the 6-step deterministic timeline from the FanCode specification:
```bash
curl -X POST "http://localhost:8080/mock/provider-a/play?matchId=a-match-1001"
```
**Response:**
```json
[
  {"status":"accepted","deliveryId":"d1","message":"Event processed successfully"},
  {"status":"accepted","deliveryId":"d2","message":"Event processed successfully"},
  {"status":"duplicate_ignored","deliveryId":"d2","message":"Delivery ID already processed, ignored as idempotent duplicate"},
  {"status":"accepted","deliveryId":"d4","message":"Event processed successfully"},
  {"status":"accepted","deliveryId":"d3","message":"Event processed successfully"},
  {"status":"accepted","deliveryId":"d5","message":"Event processed successfully"}
]
```

### Step 2: Query Client Read APIs

#### 1. List All Matches
```bash
curl -s http://localhost:8080/matches
```
```json
[
  {
    "id": "match-1001",
    "status": "live",
    "summaryScore": "10/1 (1.1)"
  }
]
```

#### 2. Get Match Score
```bash
curl -s http://localhost:8080/matches/match-1001/score
```
```json
{
  "status": "live",
  "innings": 1,
  "runs": 10,
  "wickets": 1,
  "overs": "1.1",
  "scoreFormatted": "10/1",
  "provider": "provider-a"
}
```

#### 3. Get Match Commentary (Ordered by Sequence)
```bash
curl -s http://localhost:8080/matches/match-1001/commentary
```
```json
[
  {
    "ballId": "b1",
    "sequence": 1,
    "revision": 1,
    "innings": 1,
    "over": 0,
    "ball": 4,
    "text": "four"
  },
  {
    "ballId": "b2",
    "sequence": 2,
    "revision": 2,
    "innings": 1,
    "over": 0,
    "ball": 5,
    "text": "out, review stays out"
  },
  {
    "ballId": "b3",
    "sequence": 3,
    "revision": 1,
    "innings": 1,
    "over": 1,
    "ball": 0,
    "text": "two runs"
  },
  {
    "ballId": "b4",
    "sequence": 4,
    "revision": 1,
    "innings": 1,
    "over": 1,
    "ball": 1,
    "text": "six"
  }
]
```
*Notice:*
- Ball `b3` arrived after ball `b4`, but is sorted into its correct sequential position (sequence 3).
- Ball `b2` was corrected to revision 2 with text `"out, review stays out"`.
- Total balls: exactly 4.

---

### Step 3: Send Custom Webhook Event (Provider A)

```bash
curl -X POST http://localhost:8080/api/webhooks/provider-a \
  -H "Content-Type: application/json" \
  -d '{
    "provider": "provider-a",
    "deliveryId": "d-custom-99",
    "occurredAt": "2026-10-22T14:05:00.000Z",
    "providerMatchId": "a-match-1001",
    "snapshot": {
      "sequence": 5,
      "revision": 1,
      "status": "live",
      "innings": 1,
      "runs": 16,
      "wickets": 1,
      "overs": "1.2"
    },
    "commentary": {
      "ballId": "b5",
      "sequence": 5,
      "revision": 1,
      "innings": 1,
      "over": 1,
      "ball": 2,
      "text": "Flighted outside off, driven past cover for six!"
    }
  }'
```

---

### Step 4: Exercising Provider B (Poll Mock)

#### 1. Inspect Mock Provider B Endpoints directly:
```bash
# Get Provider B Score Snapshot
curl -s http://localhost:8080/provider/matches/b-match-1001/score

# Get Provider B Commentary Items
curl -s http://localhost:8080/provider/matches/b-match-1001/commentary
```

#### 2. Switch Mock Provider B Scenarios:
- `initial`: sequence 15, 98/2 in 12.4
- `unchanged`: identical response (verifies idempotency)
- `newer_score`: sequence 16, 104/2 in 13.0
- `new_ball`: adds ball 77
- `stale`: sequence 10, 80/1 in 10.0 (rejected by monotonicity guard)
- `error_503`: simulates temporary HTTP 503 Service Unavailable

```bash
# Switch to newer score
curl -X POST http://localhost:8080/mock/provider-b/scenario/newer_score

# Trigger on-demand poll cycle
curl -X POST http://localhost:8080/mock/provider-b/poll?matchId=b-match-1001

# Switch to 503 error
curl -X POST http://localhost:8080/mock/provider-b/scenario/error_503

# Poll again (gracefully caught, last good state preserved)
curl -X POST http://localhost:8080/mock/provider-b/poll?matchId=b-match-1001
```

---

## Webhook Response Contract & Crash Recovery

| Status Code | Response Body | Meaning | Provider Action |
|:---|:---|:---|:---|
| **`200 OK`** | `{"status": "accepted", "deliveryId": "..."}` | Event processed and state committed. | None (Ack received). |
| **`200 OK`** | `{"status": "duplicate_ignored", "deliveryId": "..."}` | DeliveryId was already processed. State unchanged. | None (Ack received). |
| **`400 Bad Request`** | `{"error": "...", "status": 400}` | Malformed payload (missing provider/deliveryId). | Fix payload; do not retry as-is. |
| **`500 Internal Error`** | `{"error": "...", "status": 500}` | Database crash or unhandled error before commit. | **Retry delivery with the exact same `deliveryId`** using exponential backoff. |

### If the process crashes around acknowledgement:
- **Crash before DB commit**: Delivery was not recorded. Provider will retry with the same `deliveryId`. When the aggregator recovers, it processes the event normally.
- **Crash after DB commit but before HTTP response**: Delivery was already persisted. When the provider retries with the same `deliveryId`, the aggregator hits the idempotency guard, returns `duplicate_ignored`, and makes zero duplicate modifications to the database.

