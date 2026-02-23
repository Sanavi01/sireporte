# 1️⃣ EPIC

## Epic Title
Transactions API — Creation and Date-range Query

## Epic Purpose
Provide robust, auditable transaction creation and efficient per-user retrieval by configurable date/time ranges for the Transaction microservice following Hexagonal Architecture and domain invariants.

## Business Objective
- Record financial transactions reliably (DEBIT/CREDIT) with currency and category metadata.
- Prevent accidental duplicate transaction creation due to retries (idempotency).
- Allow efficient retrieval of a user's transactions in a bounded time window for UI and reporting.

## Success Metrics
- 0 duplicates for identical (userId, Idempotency-Key) within 24 hours.
- P95 latency for create and paged queries (size ≤ 10) < 300 ms under normal load.
- 100% of persisted transactions respect domain invariants (amount > 0, enums valid).
- Date-range queries outside allowed bounds (over 6 months) are rejected.

---

# 2️⃣ USER STORIES

---

## Story ID: HU-TRANSACTION-01
## Story Title
Create Transaction

### Role
As an API client (service or UI),

### Objective
I want to create a transaction record with a client-provided timestamp and idempotency protection.

### Benefit
So that financial events are reliably recorded without duplicates and with validated domain data.

### Detailed Description
Provide POST /transactions that accepts a JSON payload with the following fields: userId (temporary), type, amount, currency, category, timestamp. The server generates a UUIDv4 `id` and returns it in the response. The request MUST include the HTTP header `Idempotency-Key`; the service stores an idempotency mapping for 24 hours to prevent duplicate persistence for the same (userId, Idempotency-Key). The server accepts `timestamp` from the client in ISO-8601 (including timezone), normalizes it to UTC, and persists it as UTC. Currency is an enum with allowed values {COP, USD}. Category is an enum with allowed values: GROCERIES, BILLS, HOUSING, TRANSPORT, DINING, ENTERTAINMENT, HEALTH, EDUCATION, SHOPPING, INCOME, INVESTMENT, SAVINGS, INSURANCE, LOAN_PAYMENT, OTHER. Type is an enum {DEBIT, CREDIT}. Amount must be > 0 and is persisted with scale of 2 (DECIMAL(18,2)). No update or delete endpoints are provided in the MVP.

---

### 🔹 Functional Requirements

- FR-TRANSACTION-01-01: The API shall expose POST /transactions that accepts a JSON body with fields: userId (string), type (enum), amount (decimal), currency (enum), category (enum), timestamp (ISO-8601 string).
- FR-TRANSACTION-01-02: The service shall generate a server-side UUIDv4 for `id` and return it in the response; any client-supplied `id` is ignored.
- FR-TRANSACTION-01-03: The service shall accept `timestamp` from the client in ISO-8601 (with timezone) and store the normalized UTC timestamp.
- FR-TRANSACTION-01-04: The `type` field shall be validated as an enum with allowed values {DEBIT, CREDIT}; invalid values must be rejected.
- FR-TRANSACTION-01-05: The `currency` field shall be validated as an enum with allowed values {COP, USD}; invalid values must be rejected.
- FR-TRANSACTION-01-06: The `category` field shall be validated as an enum with the explicit allowed values listed above; invalid values must be rejected.
- FR-TRANSACTION-01-07: The `amount` shall be a decimal value greater than 0 with scale 2; zero or negative values must be rejected.
- FR-TRANSACTION-01-08: The API shall require the HTTP header `Idempotency-Key`; the server shall ensure idempotency per (userId, Idempotency-Key) for a TTL of 24 hours: duplicate POSTs return the original resource representation and do not create additional persisted records.
- FR-TRANSACTION-01-09: The service shall persist the transaction via the transaction repository adapter into PostgreSQL.
- FR-TRANSACTION-01-10: The service shall not publish any TransactionCreated event in this MVP.

Each requirement is atomic, testable, and traceable to the story objective.

---

### 🔹 Non-Functional Requirements

- NFR-TRANSACTION-01-01 (Performance): Create transaction request latency P95 < 300 ms under normal load.
- NFR-TRANSACTION-01-02 (Security): Validate and sanitize inputs; userId is required in the request body for now; design must allow future replacement by JWT-based auth without API-breaking changes.
- NFR-TRANSACTION-01-03 (Scalability): Idempotency storage must scale horizontally; Redis is recommended. Idempotency operations must be atomic per key to avoid race conditions.
- NFR-TRANSACTION-01-04 (Observability): Emit structured logs including correlation id and userId and metrics: transactions_created, transactions_duplicates, transactions_validation_errors.
- NFR-TRANSACTION-01-05 (Data Integrity): Persist amount as DECIMAL(18,2); enforce database constraints (NOT NULL, CHECK(amount > 0)) and constrained columns or DB enum types for type, currency, category.

---

### 🔹 Acceptance Criteria

#### Positive Scenarios (Acceptance)

- GIVEN a POST /transactions request with valid JSON body and header `Idempotency-Key: abc123`
  WHEN the payload contains valid data (userId, type=DEBIT, amount=100.00, currency=COP, category=GROCERIES, timestamp=2026-02-22T12:00:00-05:00)
  THEN the server returns 201 Created with JSON containing id (UUID), userId, type, amount, currency, category, timestamp (normalized to UTC), and Location header /transactions/{id}; the transaction is persisted.

- GIVEN the same client repeats the identical POST with the same Idempotency-Key within 24 hours
  WHEN the second request is received
  THEN the server returns 200 OK with the same resource representation and does not create a second DB entry.

#### Negative Scenarios (Non-Acceptance)

- Validation failures:
  - GIVEN a request where amount <= 0
    WHEN server validates the payload
    THEN server returns 400 Bad Request with structured error "amount must be > 0".
  - GIVEN missing required fields (userId, type, amount, currency, category, timestamp)
    WHEN server validates
    THEN server returns 400 Bad Request with field-level errors.
  - GIVEN invalid enum values for type/currency/category
    WHEN server validates
    THEN server returns 400 Bad Request with specific enum validation errors.

- Duplicate cases:
  - GIVEN same userId and Idempotency-Key already used and response persisted
    WHEN another POST arrives with same key
    THEN server returns the stored response and does not persist a duplicate.

- Unauthorized access:
  - GIVEN missing or empty userId in the body
    WHEN server validates
    THEN return 400 Bad Request (future auth will return 401/403 when implemented).

- Timeout/infrastructure errors:
  - GIVEN DB connection or idempotency store errors
    WHEN server cannot persist or ensure idempotency
    THEN return 503 Service Unavailable with retryable hint and emit logs/metrics.

- Invalid formats:
  - GIVEN timestamp not ISO-8601
    WHEN parsing fails
    THEN return 400 Bad Request with message "timestamp must be ISO-8601".

- Security violations:
  - GIVEN client supplies `id` in body
    WHEN validated
    THEN server ignores client-supplied id and generates server-side UUID; optionally log a warning.

---

### Process Flow

1. Client builds JSON payload and sets `Idempotency-Key` header.
2. Client POSTs /transactions with body { userId, type, amount, currency, category, timestamp } and header `Idempotency-Key`.
3. Controller validates JSON schema and required fields.
4. Controller calls CreateTransactionUseCase with DTO.
5. Use case enforces domain invariants (amount > 0, enums valid).
6. Idempotency check: idempotency store is queried for (userId, Idempotency-Key).
   - If an entry exists: return stored response representation (no DB write).
   - Else: proceed.
7. Use case maps domain model to persistence entity and persists via repository adapter to PostgreSQL.
8. Persisted transaction is returned; server stores idempotency record (mapping to response) with TTL = 24 hours.
9. Controller returns 201 Created (first create) or 200 OK (duplicate), with full transaction DTO.
10. Structured logs and metrics emitted.

Error scenarios:
- Validation -> 400
- Timestamp parse -> 400
- Idempotency store error -> 503 (if indeterminate to avoid duplicate money events)
- DB persist error -> 503

---

### 7️⃣ ASSUMPTIONS

- A1: Server generates `id` as UUIDv4; client-supplied id is ignored.
- A2: Client provides `userId` in POST body for now; future versions will extract userId from JWT.
- A3: Client supplies `timestamp` as ISO-8601 with timezone; server normalizes to UTC and persists UTC.
- A4: Currency enum = {COP, USD}.
- A5: Category enum = {GROCERIES, BILLS, HOUSING, TRANSPORT, DINING, ENTERTAINMENT, HEALTH, EDUCATION, SHOPPING, INCOME, INVESTMENT, SAVINGS, INSURANCE, LOAN_PAYMENT, OTHER}.
- A6: Type enum = {DEBIT, CREDIT}.
- A7: Idempotency uses HTTP header `Idempotency-Key` with TTL = 24 hours.
- A8: Amount is BigDecimal persisted as DECIMAL(18,2) and must be > 0.
- A9: No TransactionCreated event published in MVP.
- A10: No update or delete endpoints provided in MVP.

---

### 8️⃣ CONSTRAINTS

- Must follow Hexagonal Architecture: domain models without framework annotations, repository interfaces as ports, infrastructure adapters implement ports.
- PostgreSQL is used for transactions persistence; no other service accesses this DB directly.
- Idempotency store must support atomic operations and TTL (Redis recommended).
- Controllers must remain thin; business logic in use cases/domain.
- Use constructor injection and avoid field injection.

---

### 9️⃣ DEPENDENCIES

- Persistence: PostgreSQL (transactions_db).
- Idempotency store: Redis (recommended) or transactional DB table with TTL cleanup.
- Libraries: Java Time API for ISO-8601 parsing/normalization; Jackson for JSON; Micrometer + Prometheus for metrics; SLF4J/Logback for logging.
- Future: Spring Security / JWT for authentication replacement of body-supplied userId.

---

### 🔟 OPEN QUESTIONS

- Response code for idempotent replays: chosen behaviour is initial create -> 201 Created; subsequent identical requests -> 200 OK with same representation. This choice can be changed if desired.
- Client-supplied `id`: chosen behaviour is to ignore and generate server-side UUIDv4. Optionally the server may reject requests that include `id` if stricter behaviour is preferred.
- Default GET behaviour when from/to omitted: default to last 30 days. This is a cross-story default (can be changed).
