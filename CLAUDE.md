# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Tech Stack & Versions

| Category | Technology | Version |
|----------|------------|---------|
| Language | Java | 21 |
| Framework | Spring Boot | 3.4.4 |
| Dependency Management | Spring Dependency Management | 1.1.7 |
| Cloud | Spring Cloud | 2024.0.1 |
| Build Tool | Gradle (Kotlin DSL) | 8.13+ |
| API Documentation | SpringDoc OpenAPI | 2.7.0 |
| ORM | Spring Data JPA + QueryDSL | (managed by Spring Boot) |
| Database | MySQL | 8.0 |
| Cache | Redis (Master-Replica) | - |
| Messaging | Kafka | 3.5.1 |
| Monitoring | Micrometer + Prometheus | (managed by Spring Boot) |
| Logging | Logback + Slack Appender | 1.6.1 |
| Testing | JUnit 5, Mockito 5.14.0, SpringMockk 4.0.2, Instancio 5.0.2 | - |
| Containers | TestContainers | (managed by Spring Boot) |

## Build & Run Commands

```bash
# Build all modules
./gradlew build

# Run tests (profile: test, timezone: Asia/Seoul)
./gradlew test

# Run specific app
./gradlew :apps:commerce-api:bootRun
./gradlew :apps:commerce-batch:bootRun --args='--job.name=jobName'
./gradlew :apps:commerce-streamer:bootRun

# Build specific module
./gradlew :apps:commerce-api:build

# Run single test class
./gradlew test --tests "com.loopers.ExampleServiceIntegrationTest"

# Run single test method
./gradlew test --tests "com.loopers.ExampleServiceIntegrationTest.testMethodName"

# Test with coverage report
./gradlew test jacocoTestReport
```

**Java version**: 21 (configured via Gradle toolchain)

## Local Infrastructure

```bash
# Start MySQL, Redis (master+replica), Kafka
docker-compose -f docker/infra-compose.yml up

# Start Prometheus + Grafana monitoring
docker-compose -f docker/monitoring-compose.yml up
```

- MySQL: localhost:3307 (root/root, application/application)
- Redis Master: localhost:6379, Replica: localhost:6380
- Kafka: localhost:19092, Kafka UI: localhost:9099
- Grafana: localhost:3000 (admin/admin)

## Architecture

### Multi-Module Structure

```
loopers-java-spring-template/
├── apps/                          # Executable Spring Boot applications
│   ├── commerce-api              # REST API (web, actuator, springdoc-openapi)
│   ├── commerce-batch            # Batch jobs (spring-batch)
│   └── commerce-streamer         # Event streaming (web, kafka)
├── modules/                       # Reusable infrastructure configurations
│   ├── jpa                       # JPA, QueryDSL, MySQL connector
│   ├── redis                     # Spring Data Redis (master-replica)
│   └── kafka                     # Spring Kafka
└── supports/                      # Cross-cutting add-on modules
    ├── jackson                   # Jackson serialization (Kotlin module, JSR310)
    ├── logging                   # Logback, Slack appender
    └── monitoring                # Micrometer, Prometheus registry
```

### Module Dependencies

| App | modules | supports |
|-----|---------|----------|
| commerce-api | jpa, redis | jackson, logging, monitoring |
| commerce-batch | jpa, redis | jackson, logging, monitoring |
| commerce-streamer | jpa, redis, kafka | jackson, logging, monitoring |

### Layer Architecture (commerce-api)
```
interfaces/api/     → Controllers, DTOs, OpenAPI specs
application/        → Facades (use case orchestration)
domain/             → Entities, Services, Repository interfaces
infrastructure/     → Repository implementations, adapters
```

### Key Patterns
- **Controllers**: Implement `*ApiSpec` interfaces for OpenAPI documentation
- **Facades**: Orchestrate domain services, convert domain models to DTOs
- **Services**: `@Component` with `@Transactional`, contain business logic
- **Repositories**: Interface in `domain/`, implementation in `infrastructure/`
- **Entities**: Extend `BaseEntity` (provides id, createdAt, updatedAt, deletedAt)
- **Response wrapper**: All APIs return `ApiResponse<T>`
- **Error handling**: `CoreException` with `ErrorType` enum, caught by `ApiControllerAdvice`

### Soft Delete
Entities use `deletedAt` field via `BaseEntity`:
```java
entity.delete();   // marks as deleted
entity.restore();  // restores
```

## Configuration

- Profile-based: local, test, dev, qa, prd
- Config imports in application.yml: jpa.yml, redis.yml, logging.yml, monitoring.yml
- Management endpoints on port 8081 (/health, /prometheus)

## Testing

- Framework: JUnit 5 + AssertJ + Mockito + SpringMockk + Instancio
- `DatabaseCleanUp` utility truncates tables between tests (from jpa test fixtures)
- `RedisCleanUp` available from redis test fixtures
- TestContainers support for MySQL, Redis, Kafka

## 개발 규칙

### 진행 Workflow - 증강 코딩
- **대원칙**: 방향성 및 주요 의사 결정은 개발자에게 제안만 할 수 있으며, 최종 승인된 사항을 기반으로 작업 수행
- **중간 결과 보고**: AI가 반복적인 동작을 하거나, 요청하지 않은 기능을 구현, 테스트 삭제를 임의로 진행할 경우 개발자가 개입
- **설계 주도권 유지**: AI가 임의판단을 하지 않고, 방향성에 대한 제안 등을 진행할 수 있으나 개발자의 승인을 받은 후 수행

### 개발 Workflow - TDD (Red → Green → Refactor)
- 모든 테스트는 3A 원칙으로 작성 (Arrange - Act - Assert)

| Phase | 설명 |
|-------|------|
| **Red** | 요구사항을 만족하는 실패하는 테스트 케이스 먼저 작성 |
| **Green** | Red Phase의 테스트가 모두 통과할 수 있는 최소한의 코드 작성 (오버엔지니어링 금지) |
| **Refactor** | 불필요한 private 함수 지양, 객체지향적 코드 작성, unused import 제거, 성능 최적화. 모든 테스트 통과 필수 |

### 주의사항

**Never Do:**
- 실제 동작하지 않는 코드, 불필요한 Mock 데이터를 이용한 구현 금지
- null-safety 하지 않은 코드 작성 금지 (Java의 경우 Optional 활용)
- println 코드 남기지 말 것

**Recommendation:**
- 실제 API를 호출해 확인하는 E2E 테스트 코드 작성
- 재사용 가능한 객체 설계
- 성능 최적화에 대한 대안 및 제안
- 개발 완료된 API는 `http/*.http` 파일에 분류해 작성

**Priority:**
1. 실제 동작하는 해결책만 고려
2. null-safety, thread-safety 고려
3. 테스트 가능한 구조로 설계
4. 기존 코드 패턴 분석 후 일관성 유지
