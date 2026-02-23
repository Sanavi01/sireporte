## Story ID: HU-TRANSACTION-02
## Story Title
Query Transactions by Date Range (user-scoped)

### Role
As an API client (service or UI),

### Objective
I want to fetch a user's transactions for a configurable date/time range (ISO-8601) with page-based pagination (default size 10, max 10),

### Benefit
So I can show transaction history or compute reports while protecting system performance with bounded queries.

### Detailed Description
Expose GET /transactions?userId={userId}&from={ISO-8601}&to={ISO-8601}&page={page}&size={size}. `userId` is required (for now). `from` and `to` are optional ISO-8601 datetimes; if omitted the server defaults to the last 30 days. Dates are accepted with timezone and normalized to UTC. Enforce maximum allowed interval between `from` and `to` of 6 months (inclusive). Pagination is page-based, default page=1, size default=10 and size max=10. Default sorting is timestamp DESC. Response includes pagination metadata and items[] containing full transaction fields (id, userId, type, amount, currency, category, timestamp).

---

### 🔹 Functional Requirements

- FR-TRANSACTION-02-01: The API shall expose GET /transactions with query params userId (required), from (optional), to (optional), page (optional), size (optional).
- FR-TRANSACTION-02-02: If both `from` and `to` are supplied they must be ISO-8601 datetimes; service must normalize to UTC and validate that (to - from) ≤ 6 months; otherwise return 400.
- FR-TRANSACTION-02-03: Pagination shall be page-based (page=1..N) with default page=1 and size default=10 and size max=10. If client requests size > 10 return 400.
- FR-TRANSACTION-02-04: If neither `from` nor `to` are supplied, default window is last 30 days (from = now - 30 days, to = now in UTC).
- FR-TRANSACTION-02-05: The service shall return transactions sorted by timestamp DESC by default.
- FR-TRANSACTION-02-06: Each returned transaction shall include id, userId, type, amount, currency, category, timestamp (UTC normalized).
- FR-TRANSACTION-02-07: The response must include pagination metadata: page, size, totalElements, totalPages.
- FR-TRANSACTION-02-08: The service shall respond 400 if userId is missing or empty.

---

### 🔹 Non-Functional Requirements

- NFR-TRANSACTION-02-01 (Performance): Date-range queries returning a single page (size ≤ 10) should have P95 < 300 ms for normal dataset sizes.
- NFR-TRANSACTION-02-02 (Scalability): Queries must use indexed columns (timestamp and userId) to limit scan sizes; queries across large ranges must be limited by the 6-month cap to avoid table scans.
- NFR-TRANSACTION-02-03 (Observability): Query operations must emit metrics (transactions_query_count, transactions_query_latency_ms) and structured logs containing query parameters and outcome.
- NFR-TRANSACTION-02-04 (Data Integrity): Returned timestamps must be normalized to UTC consistently.

---

### 🔹 Acceptance Criteria

#### Positive Scenarios (Acceptance)

- GIVEN GET /transactions?userId=123&from=2025-08-01T00:00:00-05:00&to=2025-10:01T00:00:00-05:00&page=1&size=10
  WHEN request is valid and range ≤ 6 months
  THEN return 200 OK with JSON containing items[] (up to 10), each with full fields, and pagination metadata page=1,size=10,totalElements,totalPages.

- GIVEN GET /transactions?userId=123 (no from/to)
  WHEN request is valid
  THEN server uses default last-30-days window and returns page=1,size=10 results sorted by timestamp DESC.

#### Negative Scenarios (Non-Acceptance)

- Validation failures:
  - GIVEN size=20 (exceeds max)
    WHEN validated
    THEN return 400 Bad Request with message "size must be <= 10".
  - GIVEN from or to invalid format
    WHEN parsing fails
    THEN return 400 Bad Request with "timestamp must be ISO-8601".

- Unauthorized access:
  - GIVEN missing userId in query
    WHEN validated
    THEN return 400 Bad Request (when auth added, should enforce 401/403).

- Timeout cases:
  - GIVEN DB timeout
    WHEN query cannot complete
    THEN 503 Service Unavailable with retryability hints.

- Range exceed:
  - GIVEN to - from > 6 months
    WHEN validation runs
    THEN return 400 Bad Request with message "requested date range exceeds maximum allowed 6 months".

---

### Process Flow

1. Client issues GET /transactions with required userId and optional from/to/page/size.
2. Controller validates query params and normalizes from/to to UTC (if present) or sets default window (now-30d..now).
3. Controller enforces range limit (≤ 6 months) and size ≤ 10.
4. Controller calls QueryTransactionsUseCase with filters and pagination DTOs.
5. Use case queries repository (domain/repository via adapter) with userId and timestamp range, applying sort (timestamp DESC), page/size limit.
6. Repository returns paged results and total count; use case maps to DTOs.
7. Controller returns 200 with pagination metadata and items[] sorted by timestamp DESC.
8. Logs and metrics emitted.

Error scenarios:
- Invalid params -> 400
- Range too large -> 400
- DB errors -> 503

---

### 7️⃣ ASSUMPTIONS

- A1: userId is required in query param for now; future auth will provide identity via JWT.
- A2: from/to accepted as ISO-8601 datetimes with timezone and normalized to UTC.
- A3: Default window when from/to are omitted = last 30 days.
- A4: Pagination is page-based, page default = 1, size default = 10, size max = 10.
- A5: Max allowed range between from and to is 6 months inclusive.
- A6: Returned transactions include full domain fields (id, userId, type, amount, currency, category, timestamp UTC).

---

### 8️⃣ CONSTRAINTS

- Must follow Hexagonal Architecture and project domain rules in `AI_PROJECT_CONTEXT.md`.
- PostgreSQL is the persistent store; use proper indexes on (userId, timestamp).
- Default time normalization to UTC.
- No direct DB access by other services.

---

### 9️⃣ DEPENDENCIES

- PostgreSQL (transactions_db).
- Indexes on userId and timestamp for performant range queries.
- Micrometer + Prometheus for metrics; SLF4J/Logback for logging.

---

### 🔟 OPEN QUESTIONS

- Behavior when neither from nor to provided: chosen default = last 30 days.
- Page indexing: page number is 1-based (page=1 is the first page).

---

