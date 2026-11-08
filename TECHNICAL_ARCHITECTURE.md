# Technical Architecture & Component Reference Manual
**Project:** Live Sports Match Data Aggregator  
**Target Specification:** FanCode Backend Engineering Assignment

---

## 1. Executive Summary & Requirement Traceability Matrix

This document provides an exhaustive breakdown of every class, service, controller, entity, repository, and DTO in the codebase, detailing:
1. **What the component does** (internals, design patterns, lifecycle).
2. **How it fits into the FanCode specification** (the requirements prompt).
3. **The exact invariant or contract it enforces**.

### Requirement Traceability Matrix

| Specification Requirement | Primary Java Class(es) | Key Method(s) | Contract / Invariant Enforced |
|:---|:---|:---|:---|
| **Webhook Push Ingestion** | `WebhookController`, `AggregatorService` | `POST /api/webhooks/provider-a`, `processPushEvent()` | Ingests real-time match events with snapshot and commentary payloads. |
| **Delivery Idempotency** | `ProviderDeliveryEntity`, `ProviderDeliveryRepository` | `existsByProviderAndDeliveryId()`, `save()` | Duplicate `(provider, deliveryId)` returns `duplicate_ignored` with zero database modifications. |
| **Snapshot Monotonicity `(seq, rev)`** | `ProviderSnapshotEntity`, `AggregatorService` | `isCandidateStrictlyNewer()`, `applySnapshotInternal()` | Lexicographically greater check: `(5,1) > (4,9)`, `(4,2) > (4,1)`. Older snapshots rejected. |
| **Commentary Sequence Ordering** | `ProviderCommentaryEntity`, `ProviderCommentaryRepository` | `findByMatchIdAndProviderOrderBySequenceAsc()` | Balls ordered strictly by provider `sequence`, not arrival time. Sequence gaps permitted without delaying score. |
| **Commentary Revisions & Corrections** | `ProviderCommentaryEntity`, `AggregatorService` | `applyCommentaryInternal()` | Higher revision replaces text for stable `ballId`. Equal/lower revision ignored. Exactly 1 record per ball. |
| **Deterministic Precedence** | `AppProperties`, `AggregatorService` | `determineActiveSnapshot()`, `determineActiveProvider()` | Primary provider (`provider-a`) prioritized; falls back to secondary (`provider-b`). Sequences never compared across providers. |
| **Provider B Polling & 503 Resilience** | `ProviderBPollerService`, `MockProviderBService` | `pollOnce()`, `getScore()`, `getCommentary()` | Poller catches HTTP 503 and preserves last good state. Repeated identical polls create no duplicate balls. |
| **Push Worked Example Generator** | `ProviderAGeneratorService`, `MockProviderAController` | `runWorkedPushScenario()`, `POST /mock/provider-a/play` | Deterministically executes the 6-step timeline from the prompt (d1, d2, d2 duplicate, d4, d3 late, d5 correction). |
| **Client Read APIs** | `ClientApiController`, `GlobalExceptionHandler` | `GET /matches`, `GET /matches/:id`, `GET /score`, `GET /commentary` | Unified client views. Unknown match returns structured HTTP 404. |
| **Durable Storage Across Restarts** | `application.yml`, JPA Entities | `jdbc:h2:file:./data/fancodedb;DB_CLOSE_DELAY=-1` | Stored scores, commentary, and delivery logs survive JVM shutdowns and host restarts. |
| **Concurrency Safety** | `AggregatorService` | `synchronized (ingestionLock)`, `@Transactional` | Concurrent webhook pushes and background polls cannot corrupt match state or cause race conditions. |

---

## 2. Controllers Layer (`com.fancode.aggregator.controller`)

### 2.1 `ClientApiController`
- **Purpose**: Serves all public-facing client read APIs.
- **Specification Alignment**: Directly implements the routes described in the assignment:
  - `GET /matches`: Returns a list of known matches, their status, and a human-readable summary score (e.g. `"10/1 (1.1)"`).
  - `GET /matches/{id}`: Returns the complete match view, including canonical match ID, status, active provider, and full scoreboard breakdown.
  - `GET /matches/{id}/score`: Returns current status, innings, runs, wickets, and overs as opaque strings/integers.
  - `GET /matches/{id}/commentary`: Returns ball-by-ball commentary items, strictly ordered by commentary sequence number.
- **Design Pattern**: REST Controller delegating to `AggregatorService` with `@Transactional(readOnly = true)`. Does not perform business logic or database writes.

### 2.2 `WebhookController`
- **Purpose**: Ingestion endpoint for Provider A (or any push sports provider).
- **Specification Alignment**: Implements the webhook path accepting POST requests with `PushEventRequest`.
  - Route: `POST /api/webhooks/provider-a`
  - Fulfills the message contract: accepts `deliveryId`, `occurredAt`, `providerMatchId`, `snapshot`, and `commentary`.
  - Returns `PushEventResponse` (`accepted` or `duplicate_ignored`) with HTTP 200.

### 2.3 `MockProviderAController`
- **Purpose**: Exposes test harnesses to trigger Provider A deterministic push scenarios via HTTP.
- **Specification Alignment**: Fulfills the requirement that the push generator must produce ordered deliveries, duplicates, out-of-order packets, delayed snapshots, and corrections without randomness.
  - Route: `POST /mock/provider-a/play?matchId=a-match-1001`
  - Executes the exact 6-step worked scenario from the specification.

### 2.4 `MockProviderBController`
- **Purpose**: Exposes mock Provider B HTTP endpoints and state-manipulation endpoints for testing.
- **Specification Alignment**: Exposes the exact routes demanded by the prompt:
  - `GET /provider/matches/{id}/score`: Returns score snapshot.
  - `GET /provider/matches/{id}/commentary`: Returns full array of commentary balls.
  - `POST /mock/provider-b/scenario/{name}`: Switches mock behavior to `initial`, `unchanged`, `newer_score`, `new_ball`, `stale`, or `error_503`.
  - `POST /mock/provider-b/poll`: Triggers an on-demand poll cycle.

---

## 3. Services Layer (`com.fancode.aggregator.service`)

### 3.1 `AggregatorService`
- **Purpose**: The central brain and domain engine of the application. Coordinates ingestion, validation, normalization, and client queries.
- **Key Methods & Logic**:
  1. `processPushEvent(PushEventRequest event)`:
     - **Idempotency Step**: Checks `deliveryRepository.existsByProviderAndDeliveryId(event.provider(), event.deliveryId())`. If found, returns `duplicate_ignored` immediately.
     - **Mapping Step**: Resolves canonical match ID (`a-match-1001` -> `match-1001`) via `AppProperties`.
     - **Snapshot Step**: Calls `applySnapshotInternal(...)`.
     - **Commentary Step**: Calls `applyCommentaryInternal(...)`.
  2. `applySnapshotInternal(String matchId, String provider, SnapshotDto snapshot)`:
     - Enforces the **Lexicographical Monotonicity Rule**: Checks `isCandidateStrictlyNewer(newSeq, newRev, storedSeq, storedRev)`.
     - If candidate is strictly newer, updates database entity and triggers `updateMatchPrecedence(...)`.
     - If candidate is older or identical, discards the snapshot and logs an audit message.
  3. `applyCommentaryInternal(String matchId, String provider, CommentaryDto dto)`:
     - Enforces the **Revision Correction Rule**: Queries existing ball by `(matchId, provider, ballId)`.
     - If ball does not exist, inserts a new `ProviderCommentaryEntity`.
     - If ball exists and `dto.revision() > existing.getRevision()`, replaces commentary text in-place and bumps revision.
     - If `dto.revision() <= existing.getRevision()`, ignores the update.
  4. `determineActiveSnapshot(String matchId)` & `determineActiveProvider(String matchId)`:
     - Enforces the **Deterministic Precedence Rule**: Primary provider (`provider-a`) is inspected first. If present, it wins. Otherwise, falls back to secondary (`provider-b`).
     - **Never compares sequences across providers.**
  5. `getMatchCommentary(String matchId)`:
     - Queries `commentaryRepository.findByMatchIdAndProviderOrderBySequenceAsc(matchId, activeProvider)`.
     - Ensures client commentary is strictly ordered by commentary sequence number, automatically healing sequence gaps when late balls arrive.

### 3.2 `ProviderAGeneratorService`
- **Purpose**: Deterministic generator for Provider A push timeline.
- **Specification Alignment**: Hardcodes the exact 6 steps from the prompt:
  - Step 1: `d1`, snapshot `(1, 1)` = 4/0 in `0.4`, ball `b1` rev 1, text `"four"`.
  - Step 2: `d2`, snapshot `(2, 1)` = 4/1 in `0.5`, ball `b2` rev 1, text `"out"`.
  - Step 3: `d2` again (verifies idempotency).
  - Step 4: `d4`, snapshot `(4, 1)` = 10/1 in `1.1`, ball `b4` rev 1, text `"six"` (sequence gap).
  - Step 5: `d3`, snapshot `(3, 1)` = 8/1 in `1.0`, ball `b3` rev 1, text `"two runs"` (delayed older snapshot).
  - Step 6: `d5`, snapshot `(4, 1)` repeated, ball `b2` rev 2, text `"out, review stays out"` (commentary correction).

### 3.3 `ProviderBPollerService`
- **Purpose**: Background polling client for Provider B.
- **Specification Alignment**:
  - Periodically polls Provider B via `@Scheduled` or on-demand via `pollOnce()`.
  - Catches `MockProviderUnavailableException` (HTTP 503) without throwing unhandled exceptions.
  - Ensures **last good state remains intact** during outages.

### 3.4 `MockProviderBService`
- **Purpose**: State machine simulating evolving Provider B match data.
- **Scenarios Supported**:
  - `initial` / `unchanged`: returns baseline `(15, 1)` = 98/2 (12.4) and ball 76.
  - `newer_score`: returns `(16, 1)` = 104/2 (13.0).
  - `new_ball`: appends ball 77 to commentary array.
  - `stale`: returns older tuple `(10, 1)` = 80/1 (10.0) to test rejection.
  - `error_503`: throws `MockProviderUnavailableException`.

---

## 4. Entity & Domain Layer (`com.fancode.aggregator.entity`)

### 4.1 `MatchEntity`
- Table: `matches`
- Fields: `matchId` (PK), `status`, `activeProvider`, `updatedAt`
- Purpose: Represents the top-level match container known to client applications (`match-1001`).

### 4.2 `ProviderSnapshotEntity`
- Table: `provider_snapshots`
- Unique Constraint: `(match_id, provider)`
- Fields: `sequence`, `revision`, `status`, `innings`, `runs`, `wickets`, `overs`, `asOf`, `updatedAt`
- Helper Method: `isCandidateStrictlyNewer(candSeq, candRev, storedSeq, storedRev)`:
  ```java
  public static boolean isCandidateStrictlyNewer(long cSeq, int cRev, long sSeq, int sRev) {
      if (cSeq > sSeq) return true;
      return (cSeq == sSeq && cRev > sRev);
  }
  ```
- Purpose: Preserves the single authoritative scoreboard snapshot for each provider independently.

### 4.3 `ProviderCommentaryEntity`
- Table: `provider_commentaries`
- Unique Constraint: `(match_id, provider, ball_id)`
- Index: `(match_id, provider, sequence_num)`
- Fields: `ballId` (stable identifier), `sequence` (display order), `revision`, `innings`, `over`, `ball`, `text`, `occurredAt`, `updatedAt`
- Purpose: Guarantees that each logical ball exists exactly once per provider. Corrections update the row in-place.

### 4.4 `ProviderDeliveryEntity`
- Table: `provider_deliveries`
- Unique Constraint: `(provider, delivery_id)`
- Fields: `id`, `provider`, `deliveryId`, `receivedAt`
- Purpose: Enforces message idempotency at the database constraint level.

---

## 5. Repositories Layer (`com.fancode.aggregator.repository`)

- **`MatchRepository`**: `JpaRepository<MatchEntity, String>`
- **`ProviderDeliveryRepository`**: Includes `existsByProviderAndDeliveryId(provider, deliveryId)`.
- **`ProviderSnapshotRepository`**: Includes `findByMatchIdAndProvider(matchId, provider)`.
- **`ProviderCommentaryRepository`**: Includes:
  - `findByMatchIdAndProviderAndBallId(matchId, provider, ballId)` (for point lookups during corrections).
  - `findByMatchIdAndProviderOrderBySequenceAsc(matchId, provider)` (for sequential client commentary delivery).

---

## 6. DTO Layer (`com.fancode.aggregator.dto`)

All DTOs are implemented as Java 21 `record` types for immutability, thread-safety, and zero-boilerplate serialization:

1. **`PushEventRequest`**: Ingestion payload for Provider A webhook.
2. **`PushEventResponse`**: Ingestion acknowledgement (`accepted` vs `duplicate_ignored`).
3. **`SnapshotDto`**: Wire representation of scoreboard `(sequence, revision, status, runs, wickets, overs)`.
4. **`CommentaryDto`**: Wire representation of commentary item `(ballId, sequence, revision, text)`.
5. **`PollScoreResponse` & `PollCommentaryResponse`**: Wire representations returned by Provider B mock.
6. **`MatchSummaryResponse`**: Compact summary for `GET /matches`.
7. **`MatchDetailResponse` & `ClientScoreResponse`**: Detailed score breakdown for client apps.
8. **`ClientCommentaryItemResponse`**: Clean commentary view for client apps.
9. **`ErrorResponse`**: Standard structured error payload for HTTP 400/404/500 errors.

---

## 7. Configuration & Exceptions Layer

### 7.1 `AppProperties` (`com.fancode.aggregator.config`)
- Annotated with `@ConfigurationProperties(prefix = "app")`.
- Maps external provider match IDs (`a-match-1001`, `b-match-1001`) to canonical client match ID (`match-1001`).
- Defines primary provider priority (`app.primary-provider = provider-a`).
- Configures poller interval and target endpoints.

### 7.2 `GlobalExceptionHandler` (`com.fancode.aggregator.exception`)
- Annotated with `@RestControllerAdvice`.
- Catches `ResourceNotFoundException` and returns HTTP 404 with structured JSON.
- Catches `IllegalArgumentException` and returns HTTP 400.
- Catches unexpected errors and returns HTTP 500 without leaking stack traces.

---

## 8. Test Suite Verification Matrix

| Test Class | Method | Requirement Verified | Result |
|:---|:---|:---|:---:|
| `AggregatorIntegrationTests` | `testOrderedIngestion()` | Ordered sequence ingestion (d1 -> d2) | **PASS** |
| `AggregatorIntegrationTests` | `testDuplicatePushDelivery()` | Duplicate deliveryId ignored | **PASS** |
| `AggregatorIntegrationTests` | `testOlderSnapshotWithLateBallReturnedInSequence()` | Late ball b3 inserted in sequence order | **PASS** |
| `AggregatorIntegrationTests` | `testCommentaryCorrection()` | Ball b2 text replaced by revision 2 | **PASS** |
| `AggregatorIntegrationTests` | `testRepeatedIdenticalPolls()` | Repeated identical polls produce 0 duplicates | **PASS** |
| `AggregatorIntegrationTests` | `testStalePoll()` | Stale poll rejected by monotonicity guard | **PASS** |
| `AggregatorIntegrationTests` | `testProviderErrorLeavesLastGoodState()` | HTTP 503 preserves last good state | **PASS** |
| `AggregatorIntegrationTests` | `testFullWorkedPushScenario()` | All 6 steps of the prompt scenario | **PASS** |
| `ClientApiControllerTest` | `testUnknownMatchReturns404()` | Structured 404 on invalid match ID | **PASS** |
| `ClientApiControllerTest` | `testWebhookAndClientReadFlow()` | End-to-end webhook POST and client GET | **PASS** |
| `DatabaseRestartPersistenceTest` | `testRestartAgainstSameDatabase()` | Complete data recovery across JVM reboots | **PASS** |
