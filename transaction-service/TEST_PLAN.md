# TEST_PLAN.md

## 1. Overview
- **User Story Summary:**
  - HU-TRANSACTION-01: Crear transacciones (API POST /transactions). Requisitos: validar invariantes de dominio, persistir la transacción y devolver identificador.
  - HU-TRANSACTION-02: Consultar transacciones (API GET /transactions y GET /transactions/{id}). Requisitos: filtrar por userId y rango de fechas, obtener detalle por id.
- **Architectural Implications:** Hexagonal architecture — las pruebas deben separar Domain, Application (use-cases), Persistence (adapters) e Integration (controllers). Domain es puro Java sin Spring; infraestructura implementa puertos.
- **Brownfield Risk Analysis:**
  - Hidden dependencies: repositorios JPA en infraestructura que podrían exponer detalles si se usan en pruebas de dominio.
  - Side effects: persistencia real en DB; usar dobles (mocks/fakes) para unit tests.
  - Shared mutable state: base de datos entre tests; usar limpieza o Testcontainers para aislamiento.
  - External integrations: (a futuro) RabbitMQ; no se ejecuta en estas historias.

## 2. Architectural Test Classification
- **Domain (Business Logic):** Validación de invariantes: amount > 0, type != null, userId presente, reglas de categoría/timestamp.
- **Application / Use Case:** `CreateTransactionUseCase` — orquestación, idempotencia, conversión domain↔entity.
- **Persistence:** Mappers, JPA Entity, Repository implementation (adapters/out).
- **Integration:** REST Controller endpoints, request/response mapping, status codes.
- **Cross-cutting:** Input validation (DTO), idempotency, error handling, logging, concurrency.

Lower layers (Domain → Application → Persistence → Integration) must estabilizarse primero porque cada capa depende de contratos y comportamientos de la capa inferior.

## 3. Formal Test Space Modeling

### 3.1 Equivalence Partitioning
- **Domain: Crear Transacción**
  - Válidas:
    - `amount` > 0, `type` ∈ {DEBIT, CREDIT}, `userId` presente, `timestamp` válido.
  - Inválidas:
    - `amount` == 0
    - `amount` < 0
    - `type` null o valor inválido
    - `userId` ausente o vacío
  - Mapeo a casos de prueba:
    - Caso válido estándar (amount = 100.00, type = CREDIT, userId = U1)
    - Caso amount == 0 → rechazo
    - Caso amount negativo → rechazo
    - Caso type null → rechazo
    - Caso userId vacío → rechazo

### 3.2 Boundary Value Analysis
- **Numeric boundaries (amount):**
  - Límite inferior: 0 (invalido)
  - Valores críticos: 0, 0.01 (mínimo aceptable), 1.00
  - Casos: amount = 0 (n-), amount = 0.01 (n), amount = large (p.ej. 10_000_000)
- **Length / presence boundaries:**
  - `userId` length: vacío (invalido), 1 char (válido), 255 chars (válido)

### 3.3 Decision Table (Create Transaction validations)

Conditions:
- C1: `amount` > 0
- C2: `type` presente y válido
- C3: `userId` presente

Actions:
- A1: Aceptar y persistir
- A2: Rechazar con error de validación (400/DomainViolation)

Decision table (imposibles eliminados):

| C1 | C2 | C3 | Acción |
|----|----|----|--------|
| Y  | Y  | Y  | A1     |
| N  | Y  | Y  | A2     |
| Y  | N  | Y  | A2     |
| Y  | Y  | N  | A2     |
| N  | N  | N  | A2     |


## 4. Evolutionary TDD Execution Plan

PHASE 1 — Domain Unit Scenarios
- Scenarios:
  - Validar que `Transaction` rechaza `amount <= 0`.
  - Validar que `Transaction` rechaza `type` null.
  - Validar que `Transaction` requiere `userId`.
Justification: Las invariantes de negocio deben ser firmes antes de orquestación.

PHASE 2 — Application / Use Case Scenarios
- Scenarios:
  - `CreateTransactionUseCase` crea dominio y llama al puerto `TransactionRepository`.
  - Idempotencia: reintento con mismo client-generated idempotency-key produce no duplicados.
Justification: Use cases dependen de domain estable y contratos de repositorio.

PHASE 3 — Persistence Scenarios
- Scenarios:
  - Mapeo Domain↔Entity: todos los campos mapeados correctamente.
  - Repositorio JPA persiste y recupera entity.
Justification: Confirma que el adaptador cumple el puerto de dominio.

PHASE 4 — Integration / System Scenarios
- Scenarios:
  - Controller POST /transactions: 201 con body correcto.
  - Controller POST /transactions: 400 para payload inválido.
  - GET /transactions?userId=&from=&to= devuelve filtrado correcto.
Justification: Solo cuando Application y Persistence estén estables.

PHASE 5 — Advanced / Non-Functional Concerns
- Scenarios:
  - Concurrency: múltiples solicitudes concurrentes con misma idempotency-key no generan duplicados.
  - Performance: latencia de creación aceptable bajo carga (smoke).

Reglas TDD estrictas aplicadas:
- Implementar una única prueba en rojo a la vez.
- Hacerla verde con la implementación mínima.
- Refactorizar antes de pasar al siguiente escenario.

## 5. Gherkin Scenarios

Feature: Gestión de Transacciones
  Como sistema de transacciones
  Quiero crear y consultar transacciones respetando las reglas de negocio
  Para mantener integridad y posibilidad de reporte

  # Phase 1 — Domain — Equivalence Partitioning
  Scenario: [Phase 1][EP] Crear transacción válida en dominio
    Given un conjunto de datos válidos: amount=100.00, type=CREDIT, userId="user-123", timestamp válido
    When se construye la entidad de dominio `Transaction`
    Then la instancia se crea sin excepciones y mantiene los valores

  Scenario: [Phase 1][EP] Rechazo por amount igual a cero
    Given amount=0.00, type=DEBIT, userId="user-123"
    When se intenta crear la entidad de dominio `Transaction`
    Then se lanza una excepción de validación indicando "amount must be positive"

  Scenario: [Phase 1][BVA] Boundary: amount mínimo aceptable
    Given amount=0.01, type=DEBIT, userId="user-123"
    When se crea la entidad de dominio `Transaction`
    Then la creación es exitosa

  Scenario: [Phase 1][EP] Rechazo por tipo nulo
    Given type=null, amount=10.00, userId="user-123"
    When se crea la entidad de dominio `Transaction`
    Then se lanza una excepción de validación indicando "type is required"

  # Phase 2 — Application / Use Case — Decision Table
  Scenario: [Phase 2][DT] CreateTransactionUseCase acepta cuando todas las condiciones son verdaderas
    Given un DTO válido para crear transacción
    And el puerto `TransactionRepository` es un mock que devuelve success
    When se ejecuta `CreateTransactionUseCase.handle(dto)`
    Then el puerto `save` es invocado con una entidad de dominio válida
    And la respuesta contiene `transactionId` y status CREATED

  Scenario: [Phase 2][DT] CreateTransactionUseCase rechaza cuando amount <= 0
    Given un DTO con amount=0.00
    When se ejecuta `CreateTransactionUseCase.handle(dto)`
    Then se devuelve un error de validación y no se invoca `save`

  Scenario: [Phase 2][IDEMP] Idempotencia en el caso feliz
    Given un request con `idempotency-key=abc-123`
    And el primer intento crea una transacción con id T1
    When se reenvía el mismo request con `idempotency-key=abc-123`
    Then la segunda llamada no crea una nueva transacción
    And la respuesta referencia `T1`

  # Phase 3 — Persistence — Mapping & Repository
  Scenario: [Phase 3][Persistence] Mapeo Domain->Entity y persistencia roundtrip
    Given una entidad de dominio válida
    When se mapea a `TransactionEntity` y se persiste con JPA repository
    Then al recuperar por id se obtiene una entidad mapeada al dominio con los mismos valores

  # Phase 4 — Integration — Controller
  Scenario: [Phase 4][Integration] POST /transactions crea recurso (201)
    Given un payload JSON válido
    When se hace POST /transactions
    Then el servicio responde 201 Created
    And el body contiene `transactionId` y los datos persistidos

  Scenario: [Phase 4][Integration] POST /transactions retorna 400 para payload inválido
    Given un payload JSON con amount=0
    When se hace POST /transactions
    Then el servicio responde 400 Bad Request con mensaje de validación

  Scenario: [Phase 4][Integration] GET /transactions filtra por userId y rango
    Given varias transacciones persistidas para user-123 y user-999 en distintas fechas
    When se hace GET /transactions?userId=user-123&from=2025-01-01&to=2025-12-31
    Then la respuesta contiene solo las transacciones de user-123 en el rango

  # Phase 5 — Non-Functional
  Scenario: [Phase 5][Concurrency] Concurrency: idempotency bajo carga
    Given múltiples hilos/envíos concurrentes con la misma idempotency-key
    When todas las solicitudes son procesadas simultáneamente
    Then solo una transacción es creada y las demás reciben la referencia a esa transacción


## 6. TDD Alignment Strategy
- **Primer escenario a implementar:** Phase 1 — Domain — "Rechazo por amount igual a cero".
  - Razón: validar la invariante más crítica en el nivel más bajo; es rápido de ejecutar y no depende de infra.
  - Tipo de fallo inicial esperado: aserción/exception de dominio (falla de compilación improbable si la clase existe).

- **Ejecución ordenada:**
  1. Implementar el test domain (RED)
  2. Implementar mínima lógica en `Transaction` para pasar (GREEN)
  3. Refactorizar manteniendo tests (REFACTOR)
  4. Implementar siguiente test domain y repetir hasta completar Phase 1
  5. Avanzar a Phase 2, escribiendo un único use-case test en rojo y seguir la misma regla

- **Mocking / aislamiento:**
  - Domain tests: sin mocks, crear directamente instancias del domain model.
  - Application tests: mockear puertos/outbound (`TransactionRepository`) con Mockito / Mockk.
  - Persistence tests: usar Testcontainers Postgres o una base en memoria; limpiar esquema entre tests.
  - Integration tests: arrancar Spring context slice o usar TestRestTemplate con profile de pruebas y DB aislada.

- **Refactoring checkpoints:**
  - Después de cada conjunto de tests de Domain pasar a verde y refactorizar antes de escribir tests Application.
  - Añadir coverage minimal para mappers antes de integrar controller.

- **Regression risk areas:**
  - Mapeadores Domain↔Entity
  - Conversión de decimales/monedas y rounding
  - Idempotency storage/locking

---

Archivo generado para el microservicio: transaction-service
