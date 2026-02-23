# TEST_PLAN.md

## 1. Overview
- **User Story Summary:** Este microservicio expone dos capacidades principales: creación de transacciones (POST /transactions) con idempotencia por `Idempotency-Key` y normalización UTC, y consulta paginada por rango de fechas del historial de un usuario (GET /transactions?userId&from&to&page&size) con límite de 6 meses y tamaño de página máximo 10. (Fuentes: HU-TRANSACTION-01, HU-TRANSACTION-02).
- **Brownfield Risk Analysis:** Código existente sigue Hexagonal Architecture; riesgos típicos incluyen dependencias ocultas en infra (repositorios, store de idempotencia), efectos laterales en la base de datos (transacciones duplicadas), y falta de contratos de integración (fechas/zonas). Idempotencia y normalización de zonas horarias son puntos de mayor riesgo; la persistencia y el TTL del store (Redis) pueden introducir condiciones de carrera o fallos que causen inconsistencias financieras.

## 2. Applied Testing Principles
- **Principle Identified:** Fall-Forward Testing (Test the boundaries and failure modes that most impact money/consistency).
- **Justification:** En sistemas financieros, detectar errores en límites (importe = 0, rango de fecha límite de 6 meses, idempotencia) y fallos de infra es crítico; priorizar pruebas que eviten duplicados y pérdidas.

## 3. Test Levels Strategy
### Unit Testing
- Validación de invariantes de dominio: `amount > 0`, enums `type/currency/category`, timestamp parsing/normalización.
- Lógica de negocio del UseCase: comportamiento de creación (generación de id, mapeos) y de consulta (cálculo de ventanas por defecto, límites de rango, paginación).
- Manejo de resultados de la capa de idempotencia (existente/no existente) mediante mocks.

### Integration Testing
- Controller → UseCase → Repository adapters con una base de datos embebida o Testcontainers PostgreSQL.
- Idempotency store simulada (Redis real o stub compatible) para validar TTL y atomicidad.
- Tests de contratos JSON y códigos HTTP (201,200,400,503) con Spring Boot Test slices.

### System Testing
- Pruebas end-to-end con Postgres y Redis reales (docker-compose/Testcontainers): crear transacciones, reintentos idempotentes, queries paginadas y límites temporales.
- Pruebas de resiliencia: simular timeouts DB/Redis y validar 503 y logs/metrics.

## 4. Test Design Application

### 4.1 Equivalence Partitioning
- Campos de entrada principales y particiones válidas / inválidas:
  - `amount`: Válidos: (0.01..∞) ; Inválidos: (≤ 0), NaN/formato no decimal, escala > 2.
  - `type`: Válidos: {DEBIT, CREDIT} ; Inválidos: cualquier otro string o null.
  - `currency`: Válidos: {COP, USD} ; Inválidos: otras monedas o null.
  - `category`: Válidos: listado autorizado ; Inválidos: valores fuera del enum o null.
  - `timestamp`: Válidos: ISO-8601 con zona ; Inválidos: formatos no ISO, strings vacíos.
  - `Idempotency-Key` header: Válidos: no vacío ; Inválidos: ausente o vacío.
  - `size` (query): Válidos: 1..10 ; Inválidos: ≤0, >10, no integer.
  - `date range`: Válidos: intervalos ≤ 6 meses ; Inválidos: intervalos > 6 meses.

Mapeo a escenarios: Cada partición genera al menos un escenario Gherkin (ver Sección 5, grupo Equivalence Partitioning).

### 4.2 Boundary Value Analysis
- Límites identificados:
  - `amount` borde inferior: 0 (invalid), 0.01 (valid). Probar -0.01 como negativo.
  - `size`: 1 (min), 10 (max), 11 (invalid).
  - Fecha límite de rango: exactamente 6 meses (valid), 6 months + 1 day (invalid). Probar `from = 2025-08-01T00:00:00Z`, `to = 2026-02-01T00:00:00Z` (6 meses si se cuenta inclusive según reglas) y `to = 2026-02-02T00:00:00Z` (excede).
  - Idempotency TTL: reenvío dentro de 24h (duplicate behavior), reenvío después de 24h (create new).

Mapeo a escenarios: Sección 5, grupo Boundary Value contiene escenarios que utilizan n-1, n, n+1 para los límites mencionados.

### 4.3 Decision Table
- Condiciones relevantes para la creación:
  - C1: `Idempotency-Key` presente? (Y/N)
  - C2: Payload válido (amount>0, enums válidos)? (Y/N)
  - C3: Idempotency store contiene (userId, key)? (Y/N)

- Acciones:
  - A1: Persistir nueva transacción y devolver 201.
  - A2: Devolver representación almacenada (200) sin persistir.
  - A3: Rechazar con 400 (validación).
  - A4: Devolver 503 si store/DB indeterminado.

- Tabla simplificada (combinaciones relevantes):
  1) C1=Y, C2=Y, C3=N => A1
  2) C1=Y, C2=Y, C3=Y => A2
  3) C1=Y, C2=N, C3=X => A3
  4) C1=N, C2=Y => A3 (Idempotency header obligatorio)
  5) C1=Y, C2=Y, C3=ERR(store) => A4

Cada combinación se convierte a escenarios Gherkin en Sección 5 (Decision Table group).

## 5. Gherkin Scenarios

Feature: Transaction Management API
  Description: Creación de transacciones con idempotencia y consulta de transacciones por rango de fechas paginado.

  Background:
    Given the transaction service is running
    And the transactions database is reachable
    And the idempotency store (Redis) is reachable

  # Equivalence Partitioning Scenarios
  # Cada escenario mapea a un Acceptance Criterion FR-TRANSACTION-01-XX / FR-TRANSACTION-02-XX

  Scenario: Crear transacción válida (partición válida)
    Given a POST /transactions with body { userId: "u-1", type: "DEBIT", amount: 100.00, currency: "COP", category: "GROCERIES", timestamp: "2026-02-22T12:00:00-05:00" }
    And header Idempotency-Key: "abc123"
    When the request is processed
    Then response status is 201 Created
    And response body contains id (UUID) and timestamp normalized to UTC
    And the transaction is persisted
    # Trace: FR-TRANSACTION-01-01, FR-TRANSACTION-01-02, FR-TRANSACTION-01-03

  Scenario: Crear transacción con amount cero (partición inválida)
    Given a POST /transactions with body { userId: "u-1", type: "DEBIT", amount: 0.00, currency: "COP", category: "GROCERIES", timestamp: "2026-02-22T12:00:00Z" }
    And header Idempotency-Key: "key-zero"
    When the request is processed
    Then response status is 400 Bad Request
    And error message contains "amount must be > 0"
    # Trace: FR-TRANSACTION-01-07

  Scenario: Crear transacción con tipo inválido (partición inválida)
    Given a POST /transactions with body { userId: "u-1", type: "INVALID", amount: 10.00, currency: "COP", category: "GROCERIES", timestamp: "2026-02-22T12:00:00Z" }
    And header Idempotency-Key: "key-type"
    When the request is processed
    Then response status is 400 Bad Request
    And error message contains "type must be one of [DEBIT, CREDIT]"
    # Trace: FR-TRANSACTION-01-04

  Scenario: Consulta transacciones por defecto (sin from/to) (partición válida)
    Given a GET /transactions?userId=u-1
    When the request is processed
    Then response status is 200 OK
    And response contains page=1,size=10 and items[] sorted by timestamp DESC
    # Trace: FR-TRANSACTION-02-04, FR-TRANSACTION-02-05, FR-TRANSACTION-02-07

  Scenario: Consulta con size excedido (partición inválida)
    Given a GET /transactions?userId=u-1&size=20
    When the request is processed
    Then response status is 400 Bad Request
    And error message contains "size must be <= 10"
    # Trace: FR-TRANSACTION-02-03

  # Boundary Value Scenarios

  Scenario: Amount justo por encima del límite (0.01) (BVA)
    Given a POST /transactions with body { userId: "u-2", type: "CREDIT", amount: 0.01, currency: "USD", category: "INCOME", timestamp: "2026-02-22T00:00:00Z" }
    And header Idempotency-Key: "bva-amount-1"
    When the request is processed
    Then response status is 201 Created
    # Trace: Boundary amount n (0.01)

  Scenario: Amount justo en límite inválido (0.00) (BVA)
    Given a POST /transactions with body { userId: "u-2", type: "CREDIT", amount: 0.00, currency: "USD", category: "INCOME", timestamp: "2026-02-22T00:00:00Z" }
    And header Idempotency-Key: "bva-amount-0"
    When the request is processed
    Then response status is 400 Bad Request
    # Trace: Boundary amount n-1 (0.00)

  Scenario: Paginación size límite superior (size=10) (BVA)
    Given a GET /transactions?userId=u-3&page=1&size=10
    When the request is processed
    Then response status is 200 OK
    # Trace: Boundary size n (10)

  Scenario: Paginación size fuera del límite (size=11) (BVA)
    Given a GET /transactions?userId=u-3&page=1&size=11
    When the request is processed
    Then response status is 400 Bad Request
    # Trace: Boundary size n+1 (11)

  Scenario: Date range exactamente 6 meses (BVA)
    Given a GET /transactions?userId=u-4&from=2025-08-01T00:00:00Z&to=2026-02-01T00:00:00Z
    When the request is processed
    Then response status is 200 OK
    # Trace: Boundary date-range n (6 months)

  Scenario: Date range que excede 6 meses por un día (BVA)
    Given a GET /transactions?userId=u-4&from=2025-08-01T00:00:00Z&to=2026-02-02T00:00:00Z
    When the request is processed
    Then response status is 400 Bad Request
    And error message contains "requested date range exceeds maximum allowed 6 months"
    # Trace: Boundary date-range n+1

  Scenario: Idempotency replay dentro de 24h devuelve representación sin duplicar (BVA)
    Given a POST /transactions with body { userId: "u-5", type: "DEBIT", amount: 50.00, currency: "COP", category: "BILLS", timestamp: "2026-02-22T10:00:00Z" }
    And header Idempotency-Key: "replay-key"
    When the first request is processed
    Then response status is 201 Created
    When the same POST with same Idempotency-Key is processed within 24 hours
    Then response status is 200 OK
    And no additional DB row is created
    # Trace: FR-TRANSACTION-01-08

  Scenario: Idempotency replay después de 24h crea nuevo recurso (BVA)
    Given a POST /transactions with body { userId: "u-5", type: "DEBIT", amount: 75.00, currency: "COP", category: "BILLS", timestamp: "2026-02-22T10:00:00Z" }
    And header Idempotency-Key: "expired-key"
    When the first request is processed
    Then response status is 201 Created
    When the same POST with same Idempotency-Key is processed after 24 hours
    Then response status is 201 Created
    And a new DB row is created
    # Trace: Idempotency TTL behavior (NFR & assumptions)

  # Decision Table Scenarios

  Scenario: DT1 - Crear cuando header presente, payload válido y clave no existente
    Given a POST /transactions with valid body and Idempotency-Key "dt-1"
    And idempotency store returns NOT_FOUND for (userId, "dt-1")
    When the request is processed
    Then the service persists the transaction and returns 201 Created
    # Trace: C1=Y,C2=Y,C3=N -> A1

  Scenario: DT2 - Repetición idempotente cuando clave existe
    Given a POST /transactions with valid body and Idempotency-Key "dt-2"
    And idempotency store returns stored representation for (userId, "dt-2")
    When the request is processed
    Then the service returns 200 OK with the stored representation and does not persist a new transaction
    # Trace: C1=Y,C2=Y,C3=Y -> A2

  Scenario: DT3 - Rechazo cuando payload inválido aun con header presente
    Given a POST /transactions with body where amount = -10 and Idempotency-Key "dt-3"
    When the request is processed
    Then response status is 400 Bad Request
    # Trace: C1=Y,C2=N -> A3

  Scenario: DT4 - Rechazo cuando falta Idempotency-Key
    Given a POST /transactions with valid body and no Idempotency-Key header
    When the request is processed
    Then response status is 400 Bad Request
    And error message indicates Idempotency-Key is required
    # Trace: C1=N,C2=Y -> A3

  Scenario: DT5 - Indeterminate store error devuelve 503
    Given a POST /transactions with valid body and Idempotency-Key "dt-err"
    And idempotency store returns ERROR/UNAVAILABLE
    When the request is processed
    Then response status is 503 Service Unavailable
    And logs/metrics indicate idempotency store failure
    # Trace: C1=Y,C2=Y,C3=ERR -> A4

  # Validation and Negative Scenarios (explicit)

  Scenario: Missing required field userId on create
    Given a POST /transactions with body missing userId
    And header Idempotency-Key: "missing-user"
    When the request is processed
    Then response status is 400 Bad Request
    And error message contains "userId is required"
    # Trace: FR-TRANSACTION-01-01, Acceptance negative

  Scenario: Timestamp no ISO-8601 en creación
    Given a POST /transactions with timestamp "22-02-2026 12:00"
    And header Idempotency-Key: "bad-ts"
    When the request is processed
    Then response status is 400 Bad Request
    And error message contains "timestamp must be ISO-8601"
    # Trace: FR-TRANSACTION-01-03

  Scenario: Consulta con userId faltante
    Given a GET /transactions with no userId parameter
    When the request is processed
    Then response status is 400 Bad Request
    And error message contains "userId is required"
    # Trace: FR-TRANSACTION-02-08

  # Infrastructure failure scenarios

  Scenario: DB timeout durante creación
    Given a POST /transactions with valid body and Idempotency-Key "db-timeout"
    And the database returns a timeout/connection error
    When the request is processed
    Then response status is 503 Service Unavailable
    And a retryable hint is present in the response
    # Trace: Negative - NFR for resiliency

## 6. TDD Alignment
- Tests to implement first (RED phase):
  - Unit tests for domain validation (amount>0, enum parsing) and timestamp normalization.
  - Unit tests for CreateTransactionUseCase handling of idempotency outcomes (NOT_FOUND -> persist, FOUND -> return stored).
  - Integration test: POST /transactions happy path returning 201 with persisted record.
- Mocking strategy:
  - Mock repository (JPA adapter) and idempotency store for unit tests.
  - Use Testcontainers/Postgres + an embedded Redis or Redis Testcontainer for integration tests that validate TTL and atomicity.
- Isolation strategy:
  - Keep domain tests pure (no Spring), test use cases with mocked ports.
  - Controller tests as slice tests with mocked use cases for fast verification of HTTP mapping and validation.
- Risk areas for regression:
  - Idempotency logic (race conditions, TTL expiry handling).
  - Timestamp parsing and timezone normalization (consistency in storage and responses).
  - DB constraint enforcement for amount scale/precision.

---
Generated by QA Architect process from HU-TRANSACTION-01 and HU-TRANSACTION-02 and project context.
