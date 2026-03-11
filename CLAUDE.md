# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# 인프라 실행 (MySQL 3307, Redis 6379/6380, Kafka 19092, Kafka UI 9099)
docker compose -f ./docker/infra-compose.yml up

# 모니터링 실행 (Prometheus 9090, Grafana 3000)
docker compose -f ./docker/monitoring-compose.yml up

# 전체 빌드
./gradlew build

# 특정 모듈 빌드
./gradlew :apps:commerce-api:build

# 전체 테스트
./gradlew test

# 특정 테스트 클래스 실행
./gradlew :apps:commerce-api:test --tests ExampleV1ApiE2ETest

# 코드 커버리지 리포트
./gradlew jacoco

# Swagger UI: http://localhost:8080/swagger-ui.html
```

## Architecture

Java 21 / Spring Boot 3.4.4 멀티모듈 Gradle 프로젝트.

### Module Structure

- **apps/** — 실행 가능한 Spring Boot 애플리케이션 (BootJar)
  - `commerce-api` — REST API 서버 (Web, JPA, Redis, Swagger)
  - `commerce-batch` — 배치 처리 (web-application-type: none)
  - `commerce-streamer` — Kafka 컨슈머 스트리밍
- **modules/** — 재사용 가능한 인프라 설정 (java-library, test-fixtures 제공)
  - `jpa` — JPA/Hibernate, QueryDSL, HikariCP, BaseEntity(soft delete, audit)
  - `redis` — Master-Replica 구성, Lettuce, 읽기/쓰기 분리
  - `kafka` — 배치 리스너(3000건), Manual ACK, JsonSerializer
- **supports/** — 부가 기능 모듈
  - `jackson` — JSR310 직렬화 설정
  - `logging` — 프로파일별 logback (local: 텍스트, dev+: JSON + Slack)
  - `monitoring` — Prometheus/Micrometer, 관리 포트 8081

### Layered Architecture (commerce-api 기준)

```
interfaces/api/  → Controller, DTO, ApiResponse, ApiControllerAdvice
application/     → Facade (유스케이스 조합), Info (응답 DTO)
domain/          → Model (JPA Entity), Repository (인터페이스), Service
infrastructure/  → JpaRepository 구현체
support/error/   → CoreException, ErrorType (에러 코드 enum)
```

- 본 프로젝트는 레이어드 아키텍처를 따르며, DIP (의존성 역전 원칙) 을 준수합니다.
  - Repository 패턴: domain에 인터페이스, infrastructure에 구현체
- API request, response DTO와 응용 레이어의 DTO는 분리해 작성
- Facade 패턴: application 레이어에서 여러 도메인 서비스 조합
- API 버전닝: `/api/v1/` 경로 기반
- 글로벌 예외 처리: `ApiControllerAdvice`에서 `CoreException` → `ApiResponse` 변환

#### 도메인 & 객체 설계 전략
- 도메인 객체는 비즈니스 규칙을 캡슐화해야 합니다.
- 애플리케이션 서비스는 서로 다른 도메인을 조립해, 도메인 로직을 조정하여 기능을 제공해야 합니다.
- 규칙이 여러 서비스에 나타나면 도메인 객체에 속할 가능성이 높습니다.
- 각 기능에 대한 책임과 결합도에 대해 개발자의 의도를 확인하고 개발을 진행합니다.

## Testing

- 테스트는 `spring.profiles.active=test`, timezone `Asia/Seoul`, `maxParallelForks=1`로 실행
- **Testcontainers** 사용: MySQL, Redis, Kafka (각 모듈의 `testFixtures`에 설정 클래스 제공)
- `DatabaseCleanUp` / `RedisCleanUp`: 테스트 후 데이터 정리 유틸리티
- 테스트 종류: 단위(Model/DTO), 통합(Service+Repository), E2E(TestRestTemplate)
- 개발 완료된 API 의 경우, `.http/**.http` 에 분류해 작성

## Configuration

- 프로파일: `local`, `test`, `dev`, `qa`, `prd`
- 모듈별 설정 파일을 `spring.config.import`로 가져옴 (jpa.yml, redis.yml, kafka.yml, logging.yml, monitoring.yml)
- 로컬 DB: `localhost:3307`, 계정 `application/application`, DB명 `loopers`
- `.editorconfig`: 최대 줄 길이 130자 (테스트 파일은 제한 없음)

## Rule

### Code Writing

- 내가 코드 작성하라고 명령하기 전에 코드 작성 절대 금지
- Example이 들어가는 파일들은 수정 금지 (참고만)

### Feedback

- 내 코드/아이디어에 무작정 동의하지 말고 객관적으로 피드백 ("이건 생각해봤어?" 식으로 유도)
- 요구사항이 광범위하면 한 단계씩 점진적으로 진행

### Code Quality

- 테스트 코드 작성하기 좋은 구조로 설계
- 테스트 코드가 필요한 곳이 있다면 알려줘
- null-safety: Optional 적극 활용
- 로그: println 금지, @Slf4j 사용

### TDD Workflow (Red > Green > Refactor)

#### 1. Red Phase

- 실패하는 테스트 먼저 작성
- 요구사항을 만족하는 기능 테스트 케이스 작성

#### 2. Green Phase

- 테스트가 통과하는 최소한의 코드 작성
- 오버엔지니어링 금지

#### 3. Refactor Phase

- 불필요한 코드 제거 및 품질 개선
- 객체지향적 코드 작성, 성능 최적화
- 모든 테스트 케이스가 통과해야 함


