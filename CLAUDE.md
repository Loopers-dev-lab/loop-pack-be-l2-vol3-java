# CLAUDE.md

이 파일은 Claude Code가 이 프로젝트를 이해하는 데 필요한 컨텍스트를 제공합니다.

## 프로젝트 개요

Loopers에서 제공하는 Spring Boot + Java 기반 멀티모듈 커머스 템플릿 프로젝트입니다.

## 기술 스택 및 버전

| 기술 | 버전 |
|------|------|
| Java | 21 |
| Spring Boot | 3.4.4 |
| Spring Cloud | 2024.0.1 |
| Spring Dependency Management | 1.1.7 |
| Kotlin (일부 지원) | 2.0.20 |
| SpringDoc OpenAPI | 2.7.0 |
| QueryDSL | Jakarta |
| Testcontainers | (Spring Boot BOM) |
| JUnit 5 | (Spring Boot BOM) |
| Mockito | 5.14.0 |
| SpringMockK | 4.0.2 |
| Instancio JUnit | 5.0.2 |
| Micrometer (Prometheus) | (Spring Boot BOM) |
| Slack Appender | 1.6.1 |
| KtLint | 1.0.1 |

## 모듈 구조

```
Root (loopers-java-spring-template)
├── apps (실행 가능한 Spring Boot Application)
│   ├── commerce-api      # REST API 서버 (Web, OpenAPI, JPA, Redis)
│   ├── commerce-batch    # Spring Batch 애플리케이션
│   └── commerce-streamer # Kafka 스트리밍 애플리케이션
│
├── modules (재사용 가능한 설정 모듈)
│   ├── jpa    # Spring Data JPA + QueryDSL + MySQL
│   ├── redis  # Spring Data Redis
│   └── kafka  # Spring Kafka
│
└── supports (부가 기능 지원 모듈)
    ├── jackson    # Jackson 직렬화 설정
    ├── logging    # Slack Appender, Micrometer Tracing
    └── monitoring # Prometheus 메트릭
```

## 빌드 및 실행

```bash
# 전체 빌드
./gradlew build

# 테스트 실행
./gradlew test

# 특정 앱 실행
./gradlew :apps:commerce-api:bootRun
./gradlew :apps:commerce-batch:bootRun
./gradlew :apps:commerce-streamer:bootRun
```

## 로컬 개발 환경

```bash
# 인프라 (MySQL, Redis, Kafka 등) 실행
docker-compose -f ./docker/infra-compose.yml up

# 모니터링 (Prometheus, Grafana) 실행
docker-compose -f ./docker/monitoring-compose.yml up
```

Grafana: http://localhost:3000 (admin/admin)

## 테스트

- 테스트 프로필: `test`
- 타임존: `Asia/Seoul`
- Testcontainers 사용 (MySQL, Redis, Kafka)
- 각 모듈은 `testFixtures`를 제공하여 테스트 유틸리티 공유

## 코드 컨벤션

- 그룹 ID: `com.loopers`
- KtLint 사용 (버전 1.0.1)
- Jacoco 코드 커버리지 리포트 (XML 형식)

## 주요 의존성 (공통)

- `spring-boot-starter`: 기본 Spring Boot 설정
- `spring-boot-starter-validation`: 유효성 검증
- `jackson-datatype-jsr310`: Java 8 날짜/시간 직렬화
- `lombok`: 보일러플레이트 코드 감소

---

## 개발 방법론: TDD (Test-Driven Development)

### 1. Red Phase : 실패하는 테스트 작성
- 구현 전에 테스트 케이스를 먼저 작성
- 테스트가 실패하는 것을 확인

### 2. Green Phase : 테스트 통과를 위한 최소한의 코드 작성
- 테스트를 통과할 수 있는 코드 작성
- 오버엔지니어링 금지

### 3. Refactor Phase : 불필요한 코드 제거 및 품질 개선
- 불필요한 private 함수 지양, 객체지향적 코드 작성
- unused import 제거
- 성능 최적화
- 모든 테스트 케이스가 통과해야 함

---

## 주의사항

### 1. Never Do
- 실제 동작하지 않는 코드, 불필요한 Mock 데이터를 이용한 구현을 하지 말 것
- null-safety 하지 않게 코드 작성하지 말 것 (Java 의 경우, Optional 을 활용할 것)
- println 코드 남기지 말 것
- 테스트 없이 코드 작성하지 말 것

### 2. Recommendation
- 실제 API 를 호출해 확인하는 E2E 테스트 코드 작성
- 재사용 가능한 객체 설계
- 성능 최적화에 대한 대안 및 제안
- 개발 완료된 API 의 경우, `.http/**.http` 에 분류해 작성
- Bean Validation 활용 (`@Valid`, `@NotBlank`, `@Email` 등)

### 3. Priority
1. 실제 동작하는 해결책만 고려
2. null-safety, thread-safety 고려
3. 테스트 가능한 구조로 설계
4. 기존 코드 패턴 분석 후 일관성 유지

---

## 아키텍처 패턴 (commerce-api)

```
interfaces/api/     # Controller, DTO, ApiSpec
application/        # Facade, Info (DTO)
domain/             # Entity, Service, Repository (interface)
infrastructure/     # Repository 구현체, JpaRepository
support/error/      # CoreException, ErrorType
```

### 계층별 책임

| 계층 | 역할 |
|------|------|
| Interfaces | HTTP 요청/응답 처리, DTO 변환, API 문서화 |
| Application | 비즈니스 로직 조율, 트랜잭션 관리 |
| Domain | 핵심 비즈니스 로직, 엔티티 유효성 검증 |
| Infrastructure | 외부 시스템 연동 (DB, 외부 API 등) |
