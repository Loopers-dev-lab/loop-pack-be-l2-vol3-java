# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Multi-module Spring Boot 3.4.4 / Java 21 template project with clean architecture. The actual codebase is in `loop-pack-be-l2-vol3-java/`.

## Build & Test Commands

```bash
# Build
./gradlew build

# Run all tests
./gradlew test

# Run single test class
./gradlew test --tests "ExampleV1ApiE2ETest"

# Run tests matching pattern
./gradlew test --tests "*ModelTest"

# Generate coverage report
./gradlew jacocoTestReport
```

## Local Development Infrastructure

```bash
# Start MySQL, Redis (master/replica), Kafka
docker-compose -f ./docker/infra-compose.yml up

# Start Prometheus + Grafana (localhost:3000, admin/admin)
docker-compose -f ./docker/monitoring-compose.yml up
```

## Module Structure

```
loop-pack-be-l2-vol3-java/
├── apps/                    # Executable Spring Boot applications
│   ├── commerce-api         # REST API service
│   ├── commerce-batch       # Batch processing
│   └── commerce-streamer    # Kafka event streaming
├── modules/                 # Reusable configuration modules
│   ├── jpa                  # JPA + QueryDSL config
│   ├── redis                # Redis cache config
│   └── kafka                # Kafka config
└── supports/                # Add-on utilities
    ├── jackson              # JSON serialization
    ├── logging              # Structured logging
    └── monitoring           # Prometheus/Grafana metrics
```

## Architecture Layers (per app)

- `interfaces/api/` - REST Controllers + DTOs (request/response records)
- `application/` - Facades/Use Cases (business orchestration)
- `domain/` - Business logic, entities, domain services
- `infrastructure/` - JPA repositories, external integrations

## Key Conventions

### Entity Design
All entities extend `BaseEntity` (`modules/jpa/.../domain/BaseEntity.java`):
- Auto-managed: `id`, `createdAt`, `updatedAt`, `deletedAt`
- Soft-delete via idempotent `delete()` / `restore()` methods
- Override `guard()` for validation (called on PrePersist/PreUpdate)

### Error Handling
Use `CoreException` with `ErrorType` enum:
```java
throw new CoreException(ErrorType.NOT_FOUND);
throw new CoreException(ErrorType.BAD_REQUEST, "Custom message");
```
Available: `BAD_REQUEST`, `NOT_FOUND`, `CONFLICT`, `INTERNAL_ERROR`

### API Response Format
All responses wrapped in `ApiResponse<T>`:
```json
{
  "meta": { "result": "SUCCESS|FAIL", "errorCode": null, "message": null },
  "data": { ... }
}
```

### DTO Pattern
Use Java records with nested response classes and static `from()` factories:
```java
public class ExampleV1Dto {
    public record ExampleResponse(Long id, String name) {
        public static ExampleResponse from(ExampleModel model) { ... }
    }
}
```

## Testing Strategy

Three test tiers with naming conventions:
1. **Unit tests** (`*ModelTest`) - Domain logic, no Spring context
2. **Integration tests** (`*IntegrationTest`) - `@SpringBootTest`, uses `DatabaseCleanUp.truncateAllTables()` in `@AfterEach`
3. **E2E tests** (`*E2ETest`) - `@SpringBootTest(webEnvironment=RANDOM_PORT)`, uses `TestRestTemplate`

Test configuration:
- Profile: `spring.profiles.active=test`
- Timezone: `Asia/Seoul`
- TestContainers for MySQL and Redis

## Tech Stack

- Java 21, Spring Boot 3.4.4, Spring Cloud 2024.0.1
- MySQL 8.0 + JPA + QueryDSL
- Redis 7.0 (master-replica), Kafka 3.5.1 (KRaft mode)
- JUnit 5, Mockito, SpringMockK, Instancio, TestContainers

## 개발 규칙
### 진행 Workflow - 증강 코딩
- **대원칙** : 방향성 및 주요 의사 결정은 개발자에게 제안만 할 수 있으며, 최종 승인된 사항을 기반으로 작업을 수행.
- **중간 결과 보고** : AI 가 반복적인 동작을 하거나, 요청하지 않은 기능을 구현, 테스트 삭제를 임의로 진행할 경우 개발자가 개입.
- **설계 주도권 유지** : AI 가 임의판단을 하지 않고, 방향성에 대한 제안 등을 진행할 수 있으나 개발자의 승인을 받은 후 수행.

### 개발 Workflow - TDD (Red > Green > Refactor)
- 모든 테스트는 given-when-then 원칙으로 작성할 것
#### 1. Red Phase : 실패하는 테스트 먼저 작성
- 요구사항을 만족하는 기능 테스트 케이스 작성
- 테스트 예시
#### 2. Green Phase : 테스트를 통과하는 코드 작성
- Red Phase 의 테스트가 모두 통과할 수 있는 코드 작성
- 오버엔지니어링 금지
#### 3. Refactor Phase : 불필요한 코드 제거 및 품질 개선
- 불필요한 private 함수 지양, 객체지향적 코드 작성
- unused import 제거
- 성능 최적화
- 모든 테스트 케이스가 통과해야 함
- ## 주의사항
### 1. Never Do
- 실제 동작하지 않는 코드, 불필요한 Mock 데이터를 이요한 구현을 하지 말 것
- null-safety 하지 않게 코드 작성하지 말 것 (Java 의 경우, Optional 을 활용할 것)
- println 코드 남기지 말 것

### 2. Recommendation
- 실제 API 를 호출해 확인하는 E2E 테스트 코드 작성
- 재사용 가능한 객체 설계
- 성능 최적화에 대한 대안 및 제안
- 개발 완료된 API 의 경우, `.http/**.http` 에 분류해 작성

### 3. Priority
1. 실제 동작하는 해결책만 고려
2. null-safety, thread-safety 고려
3. 테스트 가능한 구조로 설계
4. 기존 코드 패턴 분석 후 일관성 유지