🏗 Project Context – Distributed Financial Reporting System
🎯 Objective

Build a distributed backend system using Spring Boot microservices following:

Hexagonal Architecture (Ports & Adapters)

SOLID principles

Clean separation of concerns

Domain-driven design fundamentals

Asynchronous communication with RabbitMQ

PostgreSQL per service (database per microservice pattern)

TDD (basic level)

This project simulates an enterprise-grade financial system.

🧱 Architecture Overview
Microservices
1️⃣ transaction-service

Responsibilities:

Create transactions

Persist transactions

Expose REST API for querying transactions

(Optional) Publish event when transaction is created

Database: transactions_db

2️⃣ report-service

Responsibilities:

Receive report generation request

Persist report request with status PENDING

Publish ReportRequested event

Expose endpoints to query report status

Database: reports_db

3️⃣ report-processor-service

Responsibilities:

Consume ReportRequested event

Call transaction-service via HTTP (never access its DB)

Compute report (e.g., Transaction Summary)

Persist report result

Publish ReportGenerated event

Update report status to COMPLETED

Database: report_results_db

🔥 Communication Pattern

REST for synchronous service-to-service queries

RabbitMQ for asynchronous workflows

Each service owns its own database

No direct database sharing

Loose coupling between services

🧠 Architectural Rules
1️⃣ Hexagonal Architecture (Ports & Adapters)

Each microservice must follow this structure:

service-name
└── domain
├── model
├── port
│     ├── in
│     └── out
└── service (opcional lógica compleja)

└── application
└── usecase (implementa ports in)

└── infrastructure
├── adapter
│     ├── in
│     └── out
└── config
2️⃣ Domain Layer Rules

Pure Java (no Spring annotations)

No JPA annotations

No framework dependencies

Contains:

Entities / Aggregates

Domain logic

Invariants

Repository interfaces (ports)

Example:

domain/model/Transaction.java
domain/repository/TransactionRepository.java
3️⃣ Application Layer Rules

Contains use cases

Orchestrates domain logic

Depends only on domain interfaces

No Spring annotations required (except optionally @Service)

Example:

application/usecase/CreateTransactionUseCase.java
4️⃣ Infrastructure Layer Rules

Contains adapters:

REST Controllers

JPA Entities

Spring Data repositories

RabbitMQ listeners/publishers

Mappers (Domain ↔ Entity)

Important:

JPA Entities are NOT domain models.

Example:

infrastructure/persistence/TransactionEntity.java
infrastructure/persistence/JpaTransactionRepository.java
infrastructure/controller/TransactionController.java
🧩 SOLID Principles Applied
S – Single Responsibility

Controllers only handle HTTP

Use cases only handle orchestration

Repositories only abstract persistence

Domain models contain business logic only

O – Open/Closed

Add new report types without modifying existing core logic

Extend behavior through new implementations

L – Liskov Substitution

Infrastructure repositories must respect domain repository contracts

I – Interface Segregation

Repository interfaces must be small and focused

D – Dependency Inversion

Domain defines repository interfaces

Infrastructure implements them

Application depends on abstractions, not implementations

📊 First Report Type (MVP)
Transaction Summary Report

Inputs:

userId

date range (from, to)

Output:

total credits

total debits

net balance

total transactions count

🌐 API Design
transaction-service
POST /transactions
GET /transactions?userId=&from=&to=
GET /transactions/{id}
report-service
POST /reports
GET /reports
GET /reports/{id}
📦 Domain Modeling Guidelines
Transaction (Domain Model)

Must include:

id

userId

type (DEBIT/CREDIT)

amount

category

timestamp

Business rules:

amount must be positive

type must not be null

userId required

Domain model must enforce invariants.

🔄 Event Flow

User requests report

report-service saves status = PENDING

report-service publishes ReportRequested

report-processor consumes event

report-processor calls transaction-service

report-processor calculates result

report-processor persists result

report-processor publishes ReportGenerated

report-service updates status to COMPLETED

🧪 Testing Strategy
Unit Tests

Domain logic

Use cases

Report calculation logic

Mock HTTP client in report-processor

Integration Tests (Optional)

JPA with Testcontainers

Controller + Repository flow

TDD approach:

Write failing test

Implement minimal solution

Refactor

🐳 Infrastructure

Docker Compose must include:

transaction-service

report-service

report-processor-service

postgres-transactions

postgres-reports

postgres-report-results

rabbitmq

(optional) sonarqube

(optional) pgadmin

⚠️ Important Constraints

No service accesses another service database

No domain class contains JPA annotations

Use constructor injection

Avoid field injection

Keep controllers thin

Avoid anemic domain models

Keep use cases small and focused

🎯 Engineering Goal

This is not a CRUD demo.

This is:

Clean architecture

Real microservices separation

Asynchronous processing

Enterprise-style layering

Maintainable and testable design

📌 How the AI Should Generate Code

When generating code:

Follow hexagonal structure

Separate domain model from JPA entity

Implement repository interfaces in infrastructure

Keep controllers minimal

Place business logic in domain or use case

Respect SOLID principles

Avoid unnecessary complexity

Keep MVP simple but clean