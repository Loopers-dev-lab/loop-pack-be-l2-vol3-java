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

## 도메인 & 객체 설계 전략

### Entity / VO / Domain Service 책임 분리
- **Entity** (extends `BaseEntity`): 식별자를 가진 도메인 객체. 생성자에서 불변식 검증, `guard()`는 JPA persist/update 시점 재검증
- **Value Object** (`@Embeddable`): 값으로 비교되는 불변 객체. `equals/hashCode` 필수 구현, `@NoArgsConstructor(access = PROTECTED)`
- **Domain Service** (`@Component`): 단일 Aggregate 내 비즈니스 규칙 수행. Repository 인터페이스에 의존 (DIP)
- **Facade** (`@Component`, application 레이어): 여러 Domain Service 조합, Info DTO 변환. 트랜잭션은 Domain Service에 위임

### 네이밍 규칙
- Entity: `{Domain}Model` (e.g., `ProductModel`, `BrandModel`)
- Repository Interface: `{Domain}Repository` (domain 패키지)
- Repository Impl: `{Domain}RepositoryImpl` (infrastructure 패키지)
- JPA Repository: `{Domain}JpaRepository` (infrastructure 패키지)
- Service: `{Domain}Service` (domain 패키지, `@Component`)
- Facade: `{Domain}Facade` (application 패키지, `@Component`)
- Info DTO: `{Domain}Info` (application 패키지, record)
- API DTO: `{Domain}V1Dto` (interfaces 패키지, 외부 class + 내부 record)
- API Spec: `{Domain}V1ApiSpec` / `{Domain}AdminV1ApiSpec` (Swagger interface)

## 아키텍처 & 패키지 구성 전략

### 레이어드 아키텍처 + DIP
```
interfaces/api/ → application/ → domain/ ← infrastructure/
```
- domain 레이어는 외부 의존 없음 (Repository는 interface만 정의)
- infrastructure가 domain의 Repository interface를 구현
- application은 domain service를 조합하여 유스케이스 수행

### 패키지 구조 (apps/commerce-api 기준)
```
com.loopers/
├── interfaces/api/{domain}/   # Controller, ApiSpec, Dto
├── application/{domain}/      # Facade, Info, Command
├── domain/{domain}/           # Model, Repository(interface), Service, VO, Enum
├── infrastructure/{domain}/   # JpaRepository, RepositoryImpl
├── config/                    # Spring Configuration
└── support/                   # Error, Util
```

## 도메인 & 객체 설계 전략
- 도메인 객체는 비즈니스 규칙을 캡슐화해야 합니다.
- 애플리케이션 서비스는 서로 다른 도메인을 조립해, 도메인 로직을 조정하여 기능을 제공해야 합니다.
- 규칙이 여러 서비스에 나타나면 도메인 객체에 속할 가능성이 높습니다.
- 각 기능에 대한 책임과 결합도에 대해 개발자의 의도를 확인하고 개발을 진행합니다.

## 아키텍처, 패키지 구성 전략
- 본 프로젝트는 레이어드 아키텍처를 따르며, DIP (의존성 역전 원칙) 을 준수합니다.
- API request, response DTO와 응용 레이어의 DTO는 분리해 작성하도록 합니다.
- 패키징 전략은 4개 레이어 패키지를 두고, 하위에 도메인 별로 패키징하는 형태로 작성합니다.
  - 예시
    > /interfaces/api (presentation 레이어 - API)
      /application/.. (application 레이어 - 도메인 레이어를 조합해 사용 가능한 기능을 제공)
      /domain/.. (domain 레이어 - 도메인 객체 및 엔티티, Repository 인터페이스가 위치)
      /infrastructure/.. (infrastructure 레이어 - JPA, Redis 등을 활용해 Repository 구현체를 제공)
- 설계 방식을 여러가지 개발자에게 제안하며, 제안한 부분에 대한 트레이드 오프를 알려줍니다. 그리고 최종 개발자가 선택한 방향으로 개발합니다.

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