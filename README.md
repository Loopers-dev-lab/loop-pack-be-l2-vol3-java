# Loopers Commerce Platform

Loopers에서 제공하는 Spring Boot 기반 멀티모듈 커머스 플랫폼입니다.

## 기술 스택

| 분류 | 기술 | 버전 |
|------|------|------|
| Language | Java | 21 |
| Framework | Spring Boot | 3.4.4 |
| Cloud | Spring Cloud | 2024.0.1 |
| Build | Gradle (Kotlin DSL) | 8.x |
| ORM | Spring Data JPA + QueryDSL | - |
| Database | MySQL | 8.x |
| Cache | Redis (Lettuce) | - |
| Messaging | Spring Kafka | - |
| API Docs | SpringDoc OpenAPI | 2.7.0 |
| Security | Spring Security Crypto (BCrypt) | - |
| Test | JUnit 5, AssertJ, Mockito, TestContainers | - |

## Getting Started

### 사전 요구사항

- Java 21
- Docker / Docker Compose

### 인프라 실행

```shell
# MySQL, Redis, Kafka
docker-compose -f ./docker/infra-compose.yml up

# Prometheus, Grafana (선택)
docker-compose -f ./docker/monitoring-compose.yml up
```

### 빌드 및 실행

```shell
# 전체 빌드
./gradlew build

# commerce-api 실행
./gradlew :apps:commerce-api:bootRun

# 테스트 실행
./gradlew :apps:commerce-api:test
```

### 접속 정보

| 서비스 | URL | 비고 |
|--------|-----|------|
| Swagger UI | http://localhost:8080/swagger-ui.html | local, test 프로파일 |
| Grafana | http://localhost:3000 | admin / admin |

## 모듈 구조

본 프로젝트는 멀티 모듈 프로젝트로 구성되어 있습니다.

- **apps** : 각 모듈은 실행 가능한 SpringBootApplication을 의미합니다.
- **modules** : 특정 구현이나 도메인에 의존하지 않고, 재사용 가능한 설정을 원칙으로 합니다.
- **supports** : logging, monitoring과 같이 부가적인 기능을 지원하는 모듈입니다.

```
Root
├── apps
│   ├── 📦 commerce-api         # REST API 서버
│   ├── 📦 commerce-batch       # 배치 작업
│   └── 📦 commerce-streamer    # Kafka 스트리밍
├── modules
│   ├── 📦 jpa                  # JPA, QueryDSL, DataSource 설정
│   ├── 📦 redis                # Redis Cluster 설정
│   └── 📦 kafka                # Kafka Producer/Consumer 설정
└── supports
    ├── 📦 jackson              # JSON 직렬화 설정
    ├── 📦 logging              # Logback 설정 (JSON/Plain/Slack)
    └── 📦 monitoring           # Actuator, Prometheus 설정
```

### 의존성 규칙

- apps → modules, supports (의존 가능)
- modules 간, supports 간 상호 의존 금지
- modules, supports → apps (의존 불가)

## API 엔드포인트

### Member

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| POST | `/api/v1/members/register` | 회원 가입 | - |
| GET | `/api/v1/members/me` | 내 정보 조회 | `X-Loopers-LoginId`, `X-Loopers-LoginPw` |
| PATCH | `/api/v1/members/me/password` | 비밀번호 수정 | `X-Loopers-LoginId`, `X-Loopers-LoginPw` |

### 응답 형식

```json
{
  "meta": {
    "result": "SUCCESS",
    "errorCode": null,
    "message": null
  },
  "data": { ... }
}
```

## 아키텍처

commerce-api는 레이어드 아키텍처를 따릅니다.

```
com.loopers
├── domain                  # 도메인 레이어 (Model, Service, Reader, Repository, VO)
├── application             # 애플리케이션 레이어 (Facade, Info)
├── infrastructure          # 인프라 레이어 (RepositoryImpl, JpaRepository, Converter)
├── interfaces              # 인터페이스 레이어 (Controller, ApiSpec, Dto)
└── support                 # 공통 (CoreException, ErrorType)
```

### 도메인 모델 설계

- **Value Object**: `record` 타입, Compact Constructor에서 검증
- **Entity**: `BaseEntity` 상속, `create()` 정적 팩토리로 생성 규칙 캡슐화
- **도메인 행위**: 모델이 자신의 상태를 관리 (`verifyPassword()`, `changePassword()`)
- **예외**: `CoreException(ErrorType)` 기반, HTTP 시맨틱에 맞는 ErrorType 분리

## 테스트

### 테스트 전략

| 종류 | 대상 | 인프라 | 네이밍 |
|------|------|--------|--------|
| 단위 | VO, 도메인 로직 | 없음 | `{Class}UnitTest` |
| 통합 | Service, Repository | TestContainers (MySQL) | `{Class}IntegrationTest` |
| E2E | REST API | TestContainers + TestRestTemplate | `{Class}E2ETest` |

### 실행

```shell
# 전체 테스트
./gradlew test

# commerce-api 테스트만
./gradlew :apps:commerce-api:test

# 커버리지 리포트
./gradlew test jacocoTestReport
```

## 프로파일

| 프로파일 | 용도 |
|----------|------|
| `local` | 로컬 개발 (Docker Compose 인프라) |
| `test` | 테스트 (TestContainers) |
| `dev` | 개발 서버 |
| `qa` | QA 서버 |
| `prd` | 운영 서버 |

## 참고 문서

- `CLAUDE.md` — 전체 개발 가이드 및 코드 컨벤션
- `.codeguide/loopers-1-week.md` — 1주차 구현 퀘스트
- `.claude/skills/` — 아키텍처, 테스트, 코딩 표준, PR 생성 가이드
