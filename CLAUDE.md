# CLAUDE.md

이 파일은 Claude Code가 이 저장소에서 작업할 때 참고하는 가이드입니다.

## 프로젝트 개요

Loopers에서 제공하는 Spring + Java 기반 멀티 모듈 템플릿 프로젝트입니다. 커머스 도메인을 위한 API, Batch, Streamer 애플리케이션을 포함합니다.

## 기술 스택 및 버전

| 기술 | 버전 |
|------|------|
| Java | 21 |
| Gradle | 8.13 |
| Spring Boot | 3.4.4 |
| Spring Cloud | 2024.0.1 |
| Spring Dependency Management | 1.1.7 |
| QueryDSL | (Spring Boot BOM 관리) |
| SpringDoc OpenAPI | 2.7.0 |
| Lombok | (Spring Boot BOM 관리) |
| Jackson | (Spring Boot BOM 관리) |
| MySQL Connector | (Spring Boot BOM 관리) |
| Redis | (Spring Boot BOM 관리) |
| Kafka | (Spring Boot BOM 관리) |
| Micrometer (Prometheus) | (Spring Boot BOM 관리) |
| Testcontainers | (Spring Boot BOM 관리) |
| JUnit 5 | (Spring Boot BOM 관리) |
| Mockito | 5.14.0 |
| SpringMockK | 4.0.2 |
| Instancio | 5.0.2 |

## 모듈 구조

```
Root
├── apps (실행 가능한 SpringBootApplication)
│   ├── commerce-api      - REST API 서버 (Web, JPA, Redis, OpenAPI)
│   ├── commerce-batch    - Spring Batch 애플리케이션
│   └── commerce-streamer - Kafka Consumer 애플리케이션
├── modules (재사용 가능한 설정 모듈)
│   ├── jpa   - JPA/QueryDSL 설정, MySQL 연동
│   ├── redis - Redis 설정 (Master-Replica 구조)
│   └── kafka - Kafka 설정
└── supports (부가 기능 모듈)
    ├── jackson    - Jackson 직렬화 설정
    ├── logging    - 로깅 설정 (Slack Appender 포함)
    └── monitoring - Prometheus/Micrometer 메트릭 설정
```

### 모듈 의존성

- **commerce-api**: jpa, redis, jackson, logging, monitoring
- **commerce-streamer**: jpa, redis, kafka, jackson, logging, monitoring
- **commerce-batch**: jpa, redis, jackson, logging, monitoring

## 패키지 구조 (Layered Architecture)

commerce-api 기준 패키지 구조:

```
com.loopers
├── CommerceApiApplication.java  - 애플리케이션 진입점
├── application/                 - 유스케이스, Facade 계층
│   └── example/ExampleFacade.java
├── domain/                      - 도메인 모델, 서비스, 리포지토리 인터페이스
│   └── example/
│       ├── ExampleModel.java
│       ├── ExampleService.java
│       └── ExampleRepository.java
├── infrastructure/              - 리포지토리 구현체, 외부 연동
│   └── example/
│       ├── ExampleJpaRepository.java
│       └── ExampleRepositoryImpl.java
├── interfaces/                  - API 컨트롤러, DTO
│   └── api/
│       ├── ApiControllerAdvice.java
│       ├── ApiResponse.java
│       └── example/
│           ├── ExampleV1Controller.java
│           ├── ExampleV1ApiSpec.java
│           └── ExampleV1Dto.java
└── support/                     - 공통 유틸리티, 예외 처리
    └── error/
        ├── CoreException.java
        └── ErrorType.java
```

## 빌드 및 실행 명령어

```bash
# 전체 빌드
./gradlew build

# 특정 모듈 빌드
./gradlew :apps:commerce-api:build

# 테스트 실행
./gradlew test

# 특정 모듈 테스트
./gradlew :apps:commerce-api:test

# 애플리케이션 실행
./gradlew :apps:commerce-api:bootRun
./gradlew :apps:commerce-batch:bootRun
./gradlew :apps:commerce-streamer:bootRun

# Clean
./gradlew clean
```

## 테스트

- 테스트 프레임워크: JUnit 5, Mockito, SpringMockK, Instancio
- 테스트 컨테이너: Testcontainers (MySQL, Redis, Kafka)
- 테스트 프로파일: `test` (자동 적용)
- 타임존: `Asia/Seoul`
- JaCoCo 코드 커버리지 리포트 생성 (XML 포맷)

### 테스트 Fixtures

modules 하위 모듈들은 `java-test-fixtures` 플러그인을 사용하여 테스트 픽스처 제공:
- `modules:jpa`: `MySqlTestContainersConfig`, `DatabaseCleanUp`
- `modules:redis`: `RedisTestContainersConfig`, `RedisCleanUp`
- `modules:kafka`: Kafka Testcontainers 설정

## 로컬 개발 환경

### 인프라 실행

```bash
# MySQL, Redis (Master-Replica), Kafka, Kafka-UI 실행
docker-compose -f ./docker/infra-compose.yml up

# 모니터링 (Prometheus, Grafana) 실행
docker-compose -f ./docker/monitoring-compose.yml up
```

### 인프라 포트

| 서비스 | 포트 |
|--------|------|
| MySQL | 3306 |
| Redis Master | 6379 |
| Redis Replica | 6380 |
| Kafka | 9092 (내부), 19092 (외부) |
| Kafka UI | 9099 |
| Grafana | 3000 (admin/admin) |

## 코드 스타일

- 빌드 시스템: Gradle Kotlin DSL
- Java 21 toolchain 사용
- Lombok 적극 활용
- QueryDSL로 타입 안전한 쿼리 작성
- API 버전닝: URL 기반 (`/v1/...`)
- OpenAPI(Swagger) 문서화 (`springdoc-openapi`)
---
## Git 브랜치 전략

### 브랜치 구조

```
main
 └── week{N} (주차 브랜치, PR 대상)
      ├── feature/{기능명1}
      ├── feature/{기능명2}
      └── feature/{기능명3}
```

### Workflow

1. `main`에서 `week{N}` 브랜치 생성
2. `week{N}`에서 각 기능별 `feature/*` 브랜치 생성
3. 기능 완료 시 `feature/*` → `week{N}`로 머지
4. 주차 전체 기능 완료 후 `week{N}` → `main`으로 PR

### 브랜치 생성/병합/커밋 시점

#### 브랜치 생성
- `week{N}` 브랜치: 해당 주차 작업 시작 전 `main`에서 생성
- `feature/*` 브랜치: 해당 기능 구현 시작 전 `week{N}`에서 생성

#### 커밋 시점
- TDD 사이클 완료 시 (Red → Green → Refactor 한 사이클)
- 의미 있는 단위의 작업 완료 시
- 테스트가 모두 통과하는 상태에서만 커밋

#### 병합 시점
- `feature/*` → `week{N}`: 해당 기능의 모든 테스트 통과 후
- `week{N}` → `main`: 주차 전체 기능 완료 및 테스트 통과 후 PR

### 주차별 기능 목록

| 주차 | 기능 | 브랜치 |
|------|------|--------|
| 1주차 | 회원가입 | `feature/sign-up` |
| 1주차 | 내 정보 조회 | `feature/my-info` |
| 1주차 | 비밀번호 수정 | `feature/change-password` |

---
## 개발 규칙
### 진행 Workflow - 증강 코딩
- **대원칙** : 방향성 및 주요 의사 결정은 개발자에게 제안만 할 수 있으며, 최종 승인된 사항을 기반으로 작업을 수행.
- **중간 결과 보고** : AI 가 반복적인 동작을 하거나, 요청하지 않은 기능을 구현, 테스트 삭제를 임의로 진행할 경우 개발자가 개입.
- **설계 주도권 유지** : AI 가 임의판단을 하지 않고, 방향성에 대한 제안 등을 진행할 수 있으나 개발자의 승인을 받은 후 수행.
- **적극적인 질문** : 구현 중 불확실한 사항, 여러 선택지가 있는 경우, 요구사항이 모호한 경우 반드시 개발자에게 질문하여 확인할 것.

### 개발 Workflow - TDD (Red > Green > Refactor)
- 모든 테스트는 3A 원칙으로 작성할 것 (Arrange - Act - Assert)
- **매 테스트/구현마다 아래 내용을 설명할 것:**
  1. **목적**: 무엇을 검증하려는지
  2. **구현 방법**: 테스트를 어떻게 작성했는지
  3. **결과**: 테스트 실행 결과 (성공/실패)
  4. **다음 단계**: 결과에 따라 어떻게 진행할지

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
---
## 주의사항
### 1. Never Do
- 실제 동작하지 않는 코드, 불필요한 Mock 데이터를 이용한 구현을 하지 말 것
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