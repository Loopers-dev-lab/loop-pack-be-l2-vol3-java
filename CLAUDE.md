# CLAUDE.md

## 프로젝트 개요

Spring Boot 기반 커머스 멀티모듈 템플릿 프로젝트 (`loopers-java-spring-template`).
REST API, 배치 처리, 이벤트 스트리밍을 위한 마이크로서비스 아키텍처 패턴을 제공한다.

## 기술 스택 및 버전

| 구분 | 기술 | 버전 |
|------|------|------|
| Language | Java | 21 |
| Language | Kotlin | 2.0.20 |
| Framework | Spring Boot | 3.4.4 |
| Framework | Spring Cloud | 2024.0.1 |
| Dependency Mgmt | spring-dependency-management | 1.1.7 |
| Database | MySQL | 8.0 |
| ORM | Spring Data JPA + QueryDSL | (Spring 관리) |
| Cache | Redis (Master-Replica) | 7.0 |
| Messaging | Apache Kafka (KRaft) | 3.5.1 |
| API Docs | SpringDoc OpenAPI | 2.7.0 |
| Monitoring | Micrometer + Prometheus | (Spring 관리) |
| Tracing | Micrometer Brave | (Spring 관리) |
| Logging | Logback + Slack Appender | 1.6.1 |
| Testing | JUnit 5, Mockito 5.14.0, SpringMockK 4.0.2, Instancio 5.0.2 |
| Testing Infra | TestContainers (MySQL, Redis, Kafka) | (Spring 관리) |
| Code Coverage | JaCoCo | (Gradle 관리) |
| Build Tool | Gradle (Kotlin DSL) | Wrapper 포함 |

## 모듈 구조

```
root
├── apps/                          # 실행 가능한 Spring Boot 애플리케이션
│   ├── commerce-api               # REST API 서버 (Tomcat)
│   ├── commerce-batch             # Spring Batch 배치 처리
│   └── commerce-streamer          # Kafka Consumer 스트리밍 서비스
├── modules/                       # 재사용 가능한 인프라 모듈 (java-library)
│   ├── jpa                        # JPA + MySQL + QueryDSL + HikariCP
│   ├── redis                      # Redis Master-Replica (Lettuce)
│   └── kafka                      # Kafka Producer/Consumer 설정
├── supports/                      # 횡단 관심사 모듈
│   ├── jackson                    # Jackson ObjectMapper 커스터마이징
│   ├── logging                    # 구조화 로깅 + Slack 연동
│   └── monitoring                 # Prometheus 메트릭 + Health Probe
└── docker/
    ├── infra-compose.yml          # MySQL, Redis, Kafka, Kafka UI
    └── monitoring-compose.yml     # Prometheus, Grafana
```

## 아키텍처 레이어 (commerce-api 기준)

```
interfaces/api/       → REST Controller, DTO, ApiSpec
application/          → Facade (유즈케이스 오케스트레이션), Info DTO
domain/               → Entity, Repository 인터페이스, Service (비즈니스 로직)
infrastructure/       → Repository 구현체
support/error/        → CoreException, ErrorType
```

## 빌드 및 실행

```bash
# 인프라 구동
docker compose -f docker/infra-compose.yml up -d

# 모니터링 스택 (Grafana: localhost:3000, admin/admin)
docker compose -f docker/monitoring-compose.yml up -d

# 빌드
./gradlew clean build

# 실행
./gradlew :apps:commerce-api:bootRun
./gradlew :apps:commerce-batch:bootRun --args='--job.name=demoJob'
./gradlew :apps:commerce-streamer:bootRun

# 테스트
./gradlew test

# 코드 커버리지
./gradlew test jacocoTestReport
```

## 테스트 설정

- JUnit 5 기반, 테스트 프로파일: `test`, 타임존: `Asia/Seoul`
- TestContainers로 MySQL/Redis/Kafka 통합 테스트
- 모듈별 `testFixtures`로 테스트 유틸리티 공유 (`DatabaseCleanUp`, `RedisCleanUp` 등)
- 테스트 병렬 실행 없음 (`maxParallelForks = 1`)

## 프로파일

`local`, `test`, `dev`, `qa`, `prd` — 환경별 설정은 각 모듈의 yml 파일에서 프로파일 그룹으로 관리.
운영 환경은 환경변수로 주입: `MYSQL_HOST`, `REDIS_MASTER_HOST`, `BOOTSTRAP_SERVERS` 등.

## 주요 패턴

- **BaseEntity**: ID 자동생성, `createdAt`/`updatedAt` 감사, `deletedAt` 소프트 삭제
- **ApiResponse**: 통일된 응답 래퍼 (`meta.result`, `meta.errorCode`, `data`)
- **CoreException + ErrorType**: 타입 기반 에러 처리 (400, 404, 409, 500)
- **별도 관리 포트**: 메트릭/헬스체크는 8081 포트로 분리
- **Kafka 배치 소비**: 3000건 배치, 수동 커밋 (Manual ACK)
- **Redis 읽기 분산**: Master 쓰기, Replica 읽기 분리

## 문서 작성
### 다이어그램 작성
- ERD, 시퀀스 다이어그램, 클래스 다이어그램 등 작성 시 mermaid를 이용한 마크다운으로 작성.

## 개발 규칙
### 진행 Workflow - 증강 코딩
- **대원칙** : 방향성 및 주요 의사 결정은 개발자에게 제안만 할 수 있으며, 최종 승인된 사항을 기반으로 작업을 수행.
- **중간 결과 보고** : AI 가 반복적인 동작을 하거나, 요청하지 않은 기능을 구현, 테스트 삭제를 임의로 진행할 경우 개발자가 개입.
- **설계 주도권 유지** : AI 가 임의판단을 하지 않고, 방향성에 대한 제안 등을 진행할 수 있으나 개발자의 승인을 받은 후 수행.

### 개발 Workflow - TDD (Red > Green > Refactor)
- 모든 테스트는 3A 원칙으로 작성할 것 (Arrange - Act - Assert)
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

## 주의사항
### 1. Never Do
- 실제 동작하지 않는 코드, 불필요한 Mock 데이터를 이용한 구현을 하지 말 것
- null-safety 하지 않게 코드 작성하지 말 것 (Java 의 경우, Optional 을 활용할 것)
- println 코드 남기지 말 것
- 객체지향 5원칙을 어기지 말 것

### 2. Recommendation
- 실제 API 를 호출해 확인하는 E2E 테스트 코드 작성
- 재사용 가능한 객체 설계
- 성능 최적화에 대한 대안 및 제안
- 개발 완료된 API 의 경우, `.http/**.http` 에 분류해 작성
- Domain Entity와 Persistence Entity는 구분하여 구현
- 필요한 의존성은 적절히 관리하여 최소화
- 통합 테스트는 테스트 컨테이너를 이용해 진행

### 3. Priority
1. 실제 동작하는 해결책만 고려
2. null-safety, thread-safety 고려
3. 테스트 가능한 구조로 설계
4. 기존 코드 패턴 분석 후 일관성 유지

## 깃 커밋 컨벤션
- feat: 새로운 기능 추가
- fix: 버그 수정
- docs: 문서만 수정 (예: README, 주석은 아님)
- style: 코드 포맷팅 (공백, 세미콜론 등 기능 변화 없음)
- refactor: 기능 변화 없이 코드 개선
- test: 테스트 코드 추가/수정
- chore: 빌드/패키지 설정 등 기능과 직접 관련 없는 작업
- 커밋 메세지는 한국어로 작성할 것