# CLAUDE.md

## 프로젝트 개요

Loopers Template (Spring + Java) — 멀티모듈 기반 Spring Boot 커머스 템플릿 프로젝트

## 기술 스택 및 버전

| 기술 | 버전 |
|------|------|
| Java | 21 |
| Spring Boot | 3.4.4 |
| Spring Dependency Management | 1.1.7 |
| Spring Cloud Dependencies | 2024.0.1 |
| Kotlin (Gradle Scripts) | 2.0.20 |
| Querydsl | JPA (Jakarta) |
| MySQL Connector | mysql-connector-j (Spring 관리) |
| Spring Security Crypto | Spring 관리 |
| SpringDoc OpenAPI | 2.7.0 |
| Lombok | Spring 관리 |
| Jackson (JSR310) | Spring 관리 |
| Micrometer (Prometheus) | Spring 관리 |
| Micrometer Tracing (Brave) | Spring 관리 |
| Logback Slack Appender | 1.6.1 |
| TestContainers | Spring 관리 |
| SpringMockK | 4.0.2 |
| Mockito | 5.14.0 |
| Instancio JUnit | 5.0.2 |
| JaCoCo | Gradle 내장 |

## 모듈 구조

```
loopers-java-spring-template/
├── apps/
│   ├── commerce-api/              # REST API 서버
│   ├── commerce-batch/            # Spring Batch 배치
│   └── commerce-streamer/         # Kafka Consumer 스트리밍
├── modules/
│   ├── jpa/                       # JPA + Querydsl + MySQL
│   ├── redis/                     # Redis Master/Replica
│   └── kafka/                     # Kafka Producer/Consumer
├── supports/
│   ├── jackson/                   # Jackson ObjectMapper
│   ├── logging/                   # Logback + Slack Appender
│   └── monitoring/                # Actuator + Prometheus
└── docker/
    ├── infra-compose.yml          # MySQL, Redis, Kafka
    └── monitoring-compose.yml     # Prometheus, Grafana
```

### 모듈 의존성 관계

| App | modules | supports |
|-----|---------|----------|
| commerce-api | jpa, redis | jackson, logging, monitoring |
| commerce-batch | jpa, redis | jackson, logging, monitoring |
| commerce-streamer | jpa, redis, kafka | jackson, logging, monitoring |

## 패키지 구조 (commerce-api 기준)

```
com.loopers/
├── interfaces/api/         # Controller, DTO, ApiSpec
├── application/            # Facade, Info (유스케이스 조합)
├── domain/                 # Entity, Repository(interface), Service
├── infrastructure/         # Repository 구현체 (JPA)
├── config/                 # 설정 (PasswordEncoderConfig 등)
└── support/error/          # CoreException, ErrorType
```

### 요청 흐름

```
Controller → Facade → Service → Repository(interface)
                                       ↑ 구현
                                 RepositoryImpl → JpaRepository
```

## 빌드 및 테스트

```bash
# 빌드
./gradlew clean build

# 단위 테스트만 실행 (DB/Docker 불필요)
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.*"

# 전체 테스트 (Docker 필요: MySQL, Redis)
./gradlew :apps:commerce-api:test

# 애플리케이션 실행
./gradlew :apps:commerce-api:bootRun
```

## 인증 방식

세션/JWT 없음. 매 요청마다 헤더로 인증:

```
X-Loopers-LoginId: 로그인 ID
X-Loopers-LoginPw: 비밀번호
```

---

## 개발 규칙

### 진행 Workflow — 증강 코딩

- **대원칙** : 방향성 및 주요 의사 결정은 개발자가 한다. AI는 제안만 가능.
- **중간 결과 보고** : AI가 요청하지 않은 기능을 구현하거나 테스트를 삭제하면 안 된다.
- **요청한 단계만 작성** : 개발자가 요청하지 않은 단계의 코드는 작성하지 않는다.

### 개발 Workflow — TDD (Red → Green → Refactor)

모든 테스트는 **3A 원칙**으로 작성:

```java
// Arrange — 준비
String loginId = "testuser";
given(memberRepository.findByLoginId(loginId)).willReturn(Optional.of(existingMember));

// Act — 실행
CoreException exception = assertThrows(CoreException.class, () ->
    memberService.register(loginId, ...)
);

// Assert — 검증
assertThat(exception.getErrorType()).isEqualTo(ErrorType.CONFLICT);
```

#### 1. Red Phase : 실패하는 테스트 먼저 작성
#### 2. Green Phase : 테스트를 통과하는 최소한의 코드 작성
#### 3. Refactor Phase : 불필요한 코드 제거 및 품질 개선

### 테스트 분류

| 종류 | Spring | DB | 용도 |
|------|--------|----|------|
| 단위 테스트 | 사용 금지 | 사용 금지 | 엔티티 검증, 서비스 로직 (Mock/Fake) |
| 통합 테스트 | @SpringBootTest | TestContainers | 서비스 + DB 연동 |
| E2E 테스트 | @SpringBootTest(RANDOM_PORT) | TestContainers | HTTP 요청 → 응답 전체 흐름 |

---

## 주의사항

### Never Do

- 실제 동작하지 않는 코드, 불필요한 Mock 데이터를 이용하지 말 것
- null-safety 하게 작성 (Optional 활용)
- println 금지
- 개발자가 요청하지 않은 코드를 작성하지 말 것
