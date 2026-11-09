# System Design & Architecture Document
**Project:** Live Sports Match Data Aggregator  
**Target:** FanCode Backend Engineering Review  
**Author:** Candidate  

---

## 1. Database Choice

### Decision: Embedded File-Based Relational Database (H2 Database in File Mode)
For this single-process implementation, **H2 Database** operating in persistent file mode (`jdbc:h2:file:./data/fancodedb;DB_CLOSE_DELAY=-1`) managed through **Spring Data JPA / Hibernate** was chosen.

### Trade-Offs & Rationale
1. **Zero External Dependency with Local Durability**: Reviewers can clone and run the project immediately on any workstation or inside Docker without spinning up external database clusters. All match data, delivery logs, snapshots, and commentary items are persisted to disk and fully survive application restarts.
2. **ACID Transaction Guarantees**: Enforcing strict snapshot ordering and commentary revisions requires atomic updates across multiple entities (e.g. inserting delivery logs while conditionally updating snapshots). An ACID relational database ensures that crashes during ingestion never leave partial state.
3. **Compound Indexes and Unique Constraints**:
   - `provider_deliveries (provider, delivery_id)` guarantees delivery idempotency at the storage engine level.
   - `provider_snapshots (match_id, provider)` ensures each provider maintains exactly one current scoreboard per match.
   - `provider_commentaries (match_id, provider, ball_id)` guarantees stable ball identity.
4. **Production Upgrade Path**: In a distributed multi-node production deployment, this JPA schema maps directly to **PostgreSQL** or **CockroachDB** with zero application code changes (just changing the JDBC driver and dialect in `application.yml`).

---

## 2. Data Model

The data model uses a normalized, decoupled architecture where provider streams are stored independently in their raw provider coordinates, and merged on-demand according to deterministic precedence rules.

```
                    +------------------------+
                    |        matches         |
                    +------------------------+
                    | PK match_id (VARCHAR)  |
                    |    status (VARCHAR)    |
                    | active_provider(VAR)   |
                    |   updated_at (TIMESTAMP|
                    +------------------------+
                               | 1
              +----------------+----------------+
              | 1..*                            | 1..*
+-----------------------------+   +------------------------------------+
|     provider_snapshots      |   |        provider_commentaries       |
+-----------------------------+   +------------------------------------+
| PK id (BIGINT AUTO)         |   | PK id (BIGINT AUTO)                |
| FK match_id (VARCHAR)       |   | FK match_id (VARCHAR)              |
|    provider (VARCHAR)       |   |    provider (VARCHAR)              |
|    sequence_num (BIGINT)    |   |    ball_id (VARCHAR) [STABLE ID]   |
|    revision_num (INT)       |   |    sequence_num (BIGINT) [DISPLAY] |
|    status (VARCHAR)         |   |    revision_num (INT)              |
|    innings (INT)            |   |    innings, over_num, ball_num     |
|    runs (INT), wickets (INT)|   |    text (VARCHAR 1024)             |
|    overs (VARCHAR)          |   |    occurred_at, updated_at         |
|    as_of, updated_at        |   +------------------------------------+
| UNIQUE (match_id, provider) |   | UNIQUE (match_id, provider, ballId)|
+-----------------------------+   | INDEX (match_id, provider, seq)    |
                                  +------------------------------------+

+-----------------------------------------+
|           provider_deliveries           |
+-----------------------------------------+
| PK id (BIGINT AUTO)                     |
|    provider (VARCHAR), delivery_id (VAR)|
|    received_at (TIMESTAMP)              |
| UNIQUE (provider, delivery_id)          |
+-----------------------------------------+
```

### Key Schema Properties:
- **`provider_snapshots`**: Stores the highest applied snapshot for each provider. The compound constraint `(match_id, provider)` ensures we do not accumulate stale scoreboard history in the active lookup path.
- **`provider_commentaries`**: Stores each logical ball keyed by `(match_id, provider, ball_id)`. The display order is driven by `sequence_num`, which has a dedicated B-tree index for sequential queries.
- **`provider_deliveries`**: High-throughput deduplication table for push messages.

---

## 3. Concurrency & Thread-Safety

### Problem Statement
Concurrent HTTP requests from Provider A's push webhooks, the background Provider B poller thread, and client read traffic can execute simultaneously on identical match rows. Race conditions could lead to:
1. Two simultaneous requests both passing the duplicate check.
2. A delayed older snapshot committing after a newer snapshot if evaluated concurrently.

### Implemented Strategy
1. **Critical Section Synchronization**:
   - Ingestion methods (`processPushEvent`, `processPollScore`, `processPollCommentary`) synchronize on a centralized lock within the single process (`ingestionLock`).
   - Combined with Spring's `@Transactional` boundary, this serializes concurrent writes to the match state, guaranteeing that the monotonicity checks `(sequence, revision)` evaluate against up-to-date committed database rows.
2. **Database-Level Idempotency Guards**:
   - Even if concurrent requests bypass application memory locks (e.g. across multi-process instances), the database unique constraint `uk_provider_delivery` on `(provider, delivery_id)` causes the second transaction to abort with a unique constraint violation, preventing duplicate execution.
3. **Non-Blocking Read Concurrency**:
   - Client read APIs (`/matches`, `/matches/:id`, `/matches/:id/score`, `/matches/:id/commentary`) use `@Transactional(readOnly = true)` with default MVCC (Multi-Version Concurrency Control) isolation. Readers never block writers, and writers never block readers.

---

## 4. Idempotency

### Push Path (Provider A)
- **Mechanism**: Every incoming webhook event contains `deliveryId`.
- **Deduplication Check**: Before executing snapshot or commentary logic, the service checks `deliveryRepository.existsByProviderAndDeliveryId(provider, deliveryId)`.
- **Response**: If found, it immediately short-circuits and returns HTTP 200 with:
  ```json
  {"status": "duplicate_ignored", "deliveryId": "d1"}
  ```
  The database state remains untouched.

### Poll Path (Provider B)
- Polling returns snapshots and full arrays of commentary without a message delivery ID.
- **Score Idempotency**: Polling the same snapshot tuple `(seq, rev)` evaluates `isCandidateStrictlyNewer(...)`. Because `candidate.seq == stored.seq` and `candidate.rev == stored.rev`, `strictlyNewer` evaluates to `false`. The update is skipped.
- **Commentary Idempotency**: Polling the same list of balls repeatedly encounters existing rows with `candidate.rev == existing.rev`. The revision rule requires `candidate.rev > existing.rev` to trigger an update; otherwise, the text and entity are untouched. No duplicate balls are ever created.

---

## 5. Push Path vs. Poll Path Differences

| Dimension | Push Path (Provider A) | Poll Path (Provider B) |
|:---|:---|:---|
| **Initiator** | External provider calls our HTTP webhook. | Aggregator initiates HTTP GET requests on a timer. |
| **Delivery Tracking** | Explicit message envelope `deliveryId`. | Implicit polling state (no deliveryId). |
| **Payload Structure** | Single delta/snapshot event with 1 commentary item. | Full score snapshot + complete array of historical balls. |
| **Deduplication** | Fast-path check on `(provider, deliveryId)`. | Entity-level evaluation of `(sequence, revision)` per item. |
| **Backpressure / Errors**| We send 500 / 429 to push provider to throttle. | Poller controls its own pace, applying backoff upon 503. |
| **Latency** | Immediate (sub-50ms from provider event). | Bounded by polling interval (e.g. 1–5 seconds). |

---

## 6. Provider Precedence & Conflict Handling

### The Problem
Provider A and Provider B publish scores and commentary with independent clocks, independent sequence counters, and different commentary ball identifiers. **Sequence numbers cannot be compared across providers.**

### Implemented Rule: Configured Primary Provider with Automatic Fallback
1. **Configuration**: `app.primary-provider = provider-a` (configurable in `application.yml`).
2. **Snapshot Resolution**:
   - When serving `GET /matches/:id/score`, the system queries stored snapshots for the match.
   - If the primary provider (`provider-a`) has any valid snapshot stored, **primary provider's score wins**.
   - If the primary provider has not yet published data (or is down/uninitialized), the system falls back to secondary provider (`provider-b`).
3. **Commentary Resolution**:
   - Commentary strictly follows the active provider determined by the score snapshot.
   - Balls are returned strictly ordered by that provider's commentary `sequence ASC`.
   - Commentary streams from different providers are never interweaved, preventing nonsensical ball sequences.

---

## 7. Failure Recovery

### 1. Process Crash & Recovery
- All operational state is written to the H2 database file on disk before acknowledging the HTTP request.
- Upon process restart, Spring Boot reconnects to the existing `./data/fancodedb.mv.db` file.
- The `DatabaseRestartPersistenceTest` automated test verifies that terminating the JVM and relaunching the application retains 100% of stored scores, match statuses, delivery records, and commentary items.

### 2. Provider Outage (HTTP 503 / Network Timeouts)
- If Provider B returns HTTP 503, connection refused, or timeout:
  - The `ProviderBPollerService` catches the exception cleanly (`MockProviderUnavailableException`).
  - An error log is recorded.
  - **No database rollback or state wipe occurs.** The last good stored snapshot and commentary remain live and queryable by client applications.
- When Provider B recovers, subsequent poll requests resume normal ingestion.

---

## 8. Caching Strategy

In a live cricket architecture, reads outnumber writes by orders of magnitude (millions of users reading vs. tens of updates per minute).

### Recommended Implementation (Production)
1. **Tier 1: In-Memory Read Cache (Caffeine Cache / Spring Cache)**
   - Cache `GET /matches/:id/score` with a short TTL (500ms – 1000ms).
   - High cache hit ratio during peak live ball traffic (e.g. India vs Pakistan World Cup final).
2. **Tier 2: Event-Driven Cache Invalidation**
   - Whenever an incoming snapshot or commentary revision is successfully applied, emit a local cache eviction for `match:{id}:score` and `match:{id}:commentary`.
3. **HTTP ETag / Conditional Headers**:
   - Return `ETag: W/"{seq}-{rev}-{updatedAt}"` and `Cache-Control: public, max-age=1`.
   - Client browsers and mobile apps receive HTTP `304 Not Modified` when the match has not changed between user polls.

---

## 9. The First Bottleneck Expected

### Database Write Contention on Single Match Rows
- In a relational database, updates to the same match snapshot require row-level exclusive locks (`X-lock`).
- While a cricket match only updates ball-by-ball (every 20–40 seconds), multiple providers sending rapid updates, commentary edits, and retries simultaneously will queue behind the same row lock.
- In H2 file mode, table-level locking occurs under high concurrent write loads.

### Solution
- Decouple write ingestion from client reads using an **asynchronous ingestion queue** (Kafka / RabbitMQ).
- Ingest webhooks into an append-only log in Redis/Kafka first (sub-5ms ACK), and process state reductions asynchronously with a dedicated single-threaded consumer per match ID.

---

## 10. Scaling for Hundreds of Concurrent Matches & Heavy Read Traffic

```
[Providers A, B, ...]
       |
  (Webhooks / Pollers)
       v
+------------------------+
|   API Gateway / Edge   | (SSL termination, rate limiting)
+------------------------+
       |
       v
+------------------------+
| Ingestion Cluster (Go) | (Stateless, writes directly to Kafka)
+------------------------+
       |
       v
+-------------------------------------------------------+
|  Kafka Partitioned by MatchId (e.g. 64 partitions)   |
+-------------------------------------------------------+
       |
       v (Key-by-match ensures total order per match)
+-----------------------------------+
| Aggregation Workers (Spring Boot) | (Applies seq/rev logic)
+-----------------------------------+
       |                    \
       v                     v
+-------------------+   +------------------------------------+
|  PostgreSQL / Aurora| | Redis Cluster (Primary Key: MatchId)|
|  (Durable Storage)|   | Active Score & Commentary Cache    |
+-------------------+   +------------------------------------+
                                      |
                                      v
                        +----------------------------+
                        | Edge / CDN Layer (Cloudflare)
                        | Server-Sent Events / WebSockets
                        +----------------------------+
                                      |
                                      v
                             [Millions of Fans]
```

### Architectural Evolutions for Scale:
1. **Partitioned Message Bus (Kafka)**:
   - Partition incoming events by `matchId`. All events for a specific match land on the exact same worker thread/partition, guaranteeing deterministic sequential processing without distributed locking.
2. **Redis Read-Through Cache**:
   - Store the active match score and commentary in Redis as a JSON document or Sorted Set (`ZREVRANGEBYSCORE`). Client read APIs serve 100% of traffic from Redis memory (sub-millisecond latency).
3. **Push to Clients via Server-Sent Events (SSE) / WebSockets**:
   - Rather than having millions of mobile apps polling `/matches/:id/score` every 2 seconds, establish persistent SSE connections.
   - When an aggregator updates a match score, publish an event to Redis Pub/Sub, which fans out to connected edge servers and broadcasts the delta to client devices instantly.

