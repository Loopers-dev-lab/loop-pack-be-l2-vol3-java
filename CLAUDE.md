# CLAUDE.md

## 프로젝트 개요

Spring Boot 기반 멀티 모듈 Java 프로젝트 (`loopers-java-spring-template`).
REST API, Batch, Kafka Streaming 3개의 실행 가능한 앱과 재사용 가능한 인프라/지원 모듈로 구성된다.

## 기술 스택 및 버전

| 구성 요소 | 버전 |
|-----------|------|
| Java | 21 (Toolchain) |
| Spring Boot | 3.4.4 |
| Spring Cloud Dependencies | 2024.0.1 |
| Spring Dependency Management | 1.1.7 |
| Kotlin | 2.0.20 |
| springdoc-openapi | 2.7.0 |
| QueryDSL | Jakarta |
| MySQL Connector/J | Spring Boot 관리 |
| Mockito | 5.14.0 |
| spring-mockk | 4.0.2 |
| instancio-junit | 5.0.2 |

## 모듈 구조

```
root
├── apps/                          # 실행 가능한 Spring Boot 애플리케이션
│   ├── commerce-api/              # REST API (포트 8080, 관리 포트 8081)
│   ├── commerce-batch/            # Spring Batch 처리
│   └── commerce-streamer/         # Kafka 스트리밍 처리
├── modules/                       # 재사용 가능한 인프라 모듈
│   ├── jpa/                       # JPA/Hibernate + HikariCP 설정
│   ├── redis/                     # Redis Master-Replica 설정 (Lettuce)
│   └── kafka/                     # Kafka Producer/Consumer 설정
└── supports/                      # 부가 기능 모듈
    ├── jackson/                   # JSON 직렬화 설정
    ├── logging/                   # Logback + Slack 알림
    └── monitoring/                # Prometheus + Micrometer 메트릭
```

- `apps/` 모듈만 `bootJar` 활성화, 나머지는 `jar` 태스크만 활성화

## 아키텍처 패턴

Layered Hexagonal Architecture (Ports & Adapters):

```
interfaces/    → Controller, API Spec, DTO (HTTP 계층)
application/   → Facade (유스케이스 조율)
domain/        → Model, Service, Repository 인터페이스 (비즈니스 로직)
infrastructure/→ Repository 구현체, JPA Repository (기술 어댑터)
support/       → 공통 에러, 유틸리티
```

의존성 방향: `interfaces → application → domain ← infrastructure`

## 빌드 및 실행

```bash
# 빌드
./gradlew build

# commerce-api 실행
./gradlew :apps:commerce-api:bootRun

# commerce-batch 실행 (job 이름 지정 필수)
./gradlew :apps:commerce-batch:bootRun --args='--spring.batch.job.name=demoJob'

# 테스트
./gradlew test

# 인프라 (MySQL, Redis, Kafka)
docker compose -f infra-compose.yml up -d

# 모니터링 (Prometheus, Grafana)
docker compose -f monitoring-compose.yml up -d
```

## 주요 컨벤션

### 엔티티
- `BaseEntity`를 상속하여 `id`, `created_at`, `updated_at`, `deleted_at` 자동 관리
- Soft delete 패턴 (`delete()`, `restore()` 메서드)
- Protected 기본 생성자, `@PrePersist`/`@PreUpdate` 훅 사용
- 타임존: UTC 저장, Asia/Seoul 표시

### API 응답
```json
{
  "meta": { "result": "SUCCESS|FAIL", "errorCode": "...", "message": "..." },
  "data": { }
}
```

### 에러 처리
- `CoreException(ErrorType, message)` → `ApiControllerAdvice`에서 일괄 처리
- ErrorType: `BAD_REQUEST(400)`, `NOT_FOUND(404)`, `CONFLICT(409)`, `INTERNAL_ERROR(500)`

### DTO 매핑 흐름
```
Entity → ExampleInfo (application DTO) → ExampleV1Dto (API DTO) → ApiResponse
```
- Java Record로 불변 DTO 정의, `from()` 팩토리 메서드 사용

### 네이밍
- 패키지: `com.loopers.*`
- API 버전: 클래스명에 `V1`, `V2` 접미사 (예: `ExampleV1Controller`)
- DTO: `Dto` 또는 `Info` 접미사
- Repository: `Repository` (인터페이스), `JpaRepository` (JPA)

### 테스트
- JUnit 5, `@Nested` + `@DisplayName`(한국어) 사용
- 통합 테스트: `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate`
- Testcontainers: MySQL, Redis, Kafka
- `DatabaseCleanUp` 픽스처로 테스트 간 데이터 정리 (`@AfterEach`)
- JaCoCo 코드 커버리지 (XML 리포트)
- 프로파일: `test`, 순차 실행 (`maxParallelForks = 1`)
- 테스트 데이터 중 여러 테스트에서 반복 사용되는 값은 클래스 레벨 상수(`private static final`)로 선언한다

### 코드 스타일
- Lombok: `@RequiredArgsConstructor`, `@Getter`, `@Slf4j`
- Jackson: `NON_NULL` 직렬화, 빈 문자열 → null 역직렬화
- 비즈니스 컨텍스트 주석은 한국어 사용

## 환경 프로파일

`local`, `test`, `dev`, `qa`, `prd`

| 설정 | local/test | dev/qa/prd |
|------|-----------|------------|
| DDL auto | create | none |
| SQL 로깅 | 활성화 | 비활성화 |
| Swagger | 활성화 | prd만 비활성화 |
| Batch 스키마 | always | never |

## 환경 변수

| 변수 | 용도 |
|------|------|
| `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_USER`, `MYSQL_PWD` | MySQL 접속 |
| `REDIS_MASTER_HOST`, `REDIS_MASTER_PORT` | Redis Master |
| `REDIS_REPLICA_1_HOST`, `REDIS_REPLICA_1_PORT` | Redis Replica |
| `BOOTSTRAP_SERVERS` | Kafka 브로커 |
