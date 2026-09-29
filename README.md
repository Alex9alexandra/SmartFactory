# Smart Factory – Data Warehouse Module

A mini digital factory simulator ("Smart Factory") built as a team project during a **summer internship focused on Java backend development**. The system is split into **8 functional modules** that communicate through events. This repository contains the full project, and my contribution was the **Data Warehouse module**.

## About the Project

Smart Factory simulates the operation of a factory: the different modules (production, vehicles, and so on) generate events as the factory runs. The Data Warehouse module collects all of these events, stores them, and makes them available through reports and REST APIs.

The project was developed in a team following **Agile** methodology, with sprints and daily stand-ups.

## My Contribution: Data Warehouse Module

Together with a teammate (**[Colleague's Name]**), I worked on the Data Warehouse module. My responsibilities were:

- **Kafka consumers**: implemented consumers that ingest the events produced by the other factory modules
- **Data persistence**: stored the data in **PostgreSQL**, with schema migrations managed through **Flyway**
- **REST API**: built endpoints with **Quarkus** to expose data and reports, documented with **Swagger / OpenAPI**
- **Reporting features**: implemented reports such as the **vehicle timeline** and the **digital twin**, using the **Repository** and **Service** patterns
- **Architecture**: followed the **ECB (Entity-Control-Boundary)** pattern to keep responsibilities clearly separated
- **Monitoring, debugging and optimization** of the application

## Tech Stack

| Area | Technologies |
|---|---|
| Language | Java |
| Framework | Quarkus |
| Messaging | Apache Kafka |
| Database | PostgreSQL |
| Migrations | Flyway |
| API docs | Swagger / OpenAPI |
| Build | Maven |
| Containers | Docker |
| CI/CD | [e.g. GitLab CI / GitHub Actions / Jenkins] |
| IDE | IntelliJ IDEA |

## Architecture

The module follows the **ECB (Entity-Control-Boundary)** pattern:

- **Boundary**: REST resources and Kafka consumers, the entry points into the module
- **Control**: services holding the business and reporting logic
- **Entity**: domain entities and repositories for database access

```
Other factory modules ──► Kafka ──► Kafka consumers ──► Services ──► Repositories ──► PostgreSQL
                                                             ▲
                                              REST API (Swagger) ┘
```

## Features

- Consumption and storage of events from all factory modules
- Versioned database schema through Flyway migrations
- REST endpoints for querying stored data
- **Vehicle timeline**: the history of events for a given vehicle
- **Digital twin**: a view of the current state of factory entities
- Interactive API documentation via Swagger UI


### Prerequisites

- Java [17/21]
- Maven
- Docker and Docker Compose

## What I Learned

- Building event-driven systems with Kafka
- Designing REST APIs and documenting them with OpenAPI
- Managing database schema evolution with Flyway
- Structuring code with ECB, Repository and Service patterns
- Working in an Agile team, with sprints, daily stand-ups and CI/CD

## Team

This was a team project with contributions from multiple modules. The Data Warehouse module was developed by me and a team member.

## Notes

This project was developed for educational purposes during a summer internship.

..............................................................................................................................................................

# Smart Factory Platform

Event-driven microservice platform (8 services) communicating over Apache Kafka, each owning a
private PostgreSQL database. 

## Layout

```
smart-factory/
├── pom.xml                      # Maven parent (aggregator + dependency/plugin management)
├── docker-compose.yaml          # shared infra (Kafka + Postgres + Kafka UI) + 8 services
├── init-db/create-databases.sql # creates the 8 service databases
├── infra/create-topics.sh       # (optional) explicit Kafka topic creation
├── services/                    # the 8 microservice modules (skeletons)
│   ├── order-service/           # Team 1  · orderdb       · dev :8081
│   ├── planning-service/        # Team 2  · planningdb    · dev :8082
│   ├── inventory-service/       # Team 3  · inventorydb   · dev :8083
│   ├── procurement-service/     # Team 4  · procurementdb · dev :8084
│   ├── assembly-service/        # Team 5  · assemblydb    · dev :8085
│   ├── agv-service/             # Team 6  · agvdb         · dev :8086
│   ├── quality-service/         # Team 7  · qualitydb     · dev :8087
│   └── dwh-service/             # Team 8  · dwhdb         · dev :8088
```

Each service is a standalone Quarkus app (REST + Panache + Kafka + Flyway + OpenAPI + health) and today
contains only a minimal `PingResource` (a Boundary component) — teams add entities, messaging, and
domain services per the docs. All modules inherit versions, the Quarkus BOM, and plugin config from
the root `pom.xml`.

## Architecture (ECB)

Every service follows the **Entity–Control–Boundary** pattern, one package per layer:

```
com.smartfactory.<svc>/
├── boundary/   # REST resources + Kafka adapters (the edge)   ── depends on control, entity
├── control/    # use cases, business logic, @Transactional     ── depends on entity
└── entity/     # Panache entities + repositories (owns schema) ── depends on nothing
```

Dependencies point inward (`boundary → control → entity`); `control` must never import
`boundary`. See **[docs/teams/ecb-architecture.md](docs/teams/ecb-architecture.md)** for the
layer rules and a full worked example.

## Database schema (Flyway)

Each service owns its schema via Flyway migrations in `src/main/resources/db/migration`
(`V1__init.sql`, `V2__…`, …). Migrations run automatically on startup
(`quarkus.flyway.migrate-at-start=true`) and Flyway creates its `flyway_schema_history`
table on first run. Because services run with `quarkus.hibernate-orm.database.generation=validate`,
**any entity change must be paired with a new migration** or startup fails.

> Local dev connects to the compose Postgres on `localhost:5432`, so start it first
> (`docker compose up -d postgres`) before `mvn quarkus:dev`.

## Prerequisites

| Tool | Version |
|------|---------|
| JDK | 21 |
| Maven | 3.9+ |
| Docker + Compose | recent |

## Building

```bash
# Build every module from the repo root (reactor)
mvn clean package

# Build a single module (with its parent)
mvn -pl services/order-service -am clean package
```

## Quick start

```bash
# 1) Start shared infrastructure
docker compose up -d kafka postgres kafka-ui
#    Kafka UI: http://localhost:8080 · Postgres: localhost:5432 (factory/factory)
#    On first start this also runs automatically:
#      • create-databases.sql  → creates the 8 service databases (Postgres init)
#      • create-topics.sh       → creates the 17 topics (one-shot `kafka-init` service)
#    Re-run topic creation anytime with: docker compose up kafka-init

# 2) Run a service in dev mode (hot reload)
cd services/order-service
mvn quarkus:dev
#    Health:     http://localhost:8081/q/health
#    Swagger UI: http://localhost:8081/q/swagger-ui
#    Ping:       http://localhost:8081/api/ping

# 3) Build & run a service in a container
docker compose up -d --build order-service   # exposed on http://localhost:8081
```

> **Note:** the database and topic bootstrap scripts run only on first
> initialization. `create-databases.sql` runs when the `sf-pgdata` volume is
> empty — to reset it, `docker compose down -v`. Topic creation is idempotent
> (`--if-not-exists`) and can be re-run via `docker compose up kafka-init`.

