# CLAUDE.md

이 파일은 Claude Code가 이 프로젝트를 이해하는 데 필요한 컨텍스트를 제공합니다.

## 프로젝트 개요

Spring + Java 기반 멀티 모듈 이커머스 프로젝트입니다. 상품, 주문, 회원, 결제 등 커머스 도메인을 직접 설계하고 구현하며, API, Batch, Streamer 애플리케이션으로 구성됩니다.

## 기술 스택 및 버전

### Core
- **Java**: 21
- **Spring Boot**: 3.4.4
- **Spring Cloud**: 2024.0.1
- **Gradle**: 8.13

### Data
- **Spring Data JPA** + **QueryDSL** (Jakarta)
- **MySQL** (mysql-connector-j)
- **Spring Data Redis**
- **Spring Kafka**

### Serialization
- **Jackson** (jackson-datatype-jsr310, jackson-module-kotlin)

### Monitoring & Logging
- **Micrometer** + **Prometheus**
- **Micrometer Tracing** (Brave)
- **Logback Slack Appender**: 1.6.1

### Documentation
- **SpringDoc OpenAPI**: 2.7.0

### Testing
- **JUnit 5** (junit-platform-launcher)
- **Mockito**: 5.14.0
- **SpringMockK**: 4.0.2
- **Instancio**: 5.0.2
- **Testcontainers** (MySQL, Redis, Kafka)

### Build Tools
- **Lombok**
- **JaCoCo** (코드 커버리지)

## 모듈 구조

```
Root (loopers-java-spring-template)
├── apps/                          # 실행 가능한 Spring Boot 애플리케이션
│   ├── commerce-api/              # REST API 서버 (Web, Actuator, OpenAPI)
│   ├── commerce-batch/            # Spring Batch 애플리케이션
│   └── commerce-streamer/         # Kafka Consumer 애플리케이션
├── modules/                       # 재사용 가능한 설정 모듈
│   ├── jpa/                       # JPA + QueryDSL 설정
│   ├── redis/                     # Redis 설정
│   └── kafka/                     # Kafka 설정
└── supports/                      # 부가 기능 애드온 모듈
    ├── jackson/                   # Jackson 직렬화 설정
    ├── logging/                   # Prometheus + Slack Appender
    └── monitoring/                # Micrometer + Prometheus
```

### 모듈 의존성 관계
- **commerce-api**: jpa, redis, jackson, logging, monitoring
- **commerce-batch**: jpa, redis, jackson, logging, monitoring
- **commerce-streamer**: jpa, redis, kafka, jackson, logging, monitoring

## 빌드 및 실행

### 로컬 인프라 실행
```bash
docker-compose -f ./docker/infra-compose.yml up
```

### 모니터링 환경 실행
```bash
docker-compose -f ./docker/monitoring-compose.yml up
# Grafana: http://localhost:3000 (admin/admin)
```

### 빌드
```bash
./gradlew build
```

### 테스트
```bash
./gradlew test
```
- 테스트는 `test` 프로파일로 실행됨
- 타임존: `Asia/Seoul`
- Testcontainers로 MySQL, Redis, Kafka 컨테이너 자동 생성

### 특정 앱 실행
```bash
./gradlew :apps:commerce-api:bootRun
./gradlew :apps:commerce-batch:bootRun
./gradlew :apps:commerce-streamer:bootRun
```

## 프로젝트 설정

- **그룹**: `com.loopers`
- **버전**: Git hash 기반 자동 생성
- **패키지 구조**: `com.loopers.*`

## 주요 패턴

### 테스트 패턴
- `testFixtures` 플러그인 사용 (jpa, redis, kafka 모듈)
- Testcontainers 기반 통합 테스트
- E2E 테스트: `*E2ETest.java`
- 통합 테스트: `*IntegrationTest.java`

### 모듈 규칙
- **apps**: BootJar 활성화, 일반 Jar 비활성화
- **modules/supports**: 일반 Jar 활성화, BootJar 비활성화

### 필수 연관 문서
- plan.md 필수 참고

## 개발 가이드 (Skills)

코딩 컨벤션과 아키텍처 패턴은 skill로 분리되어 있다. 구현 작업 시 해당 skill을 활성화한다.

- **api-dev-guidelines**: API 구현 규칙, RESTful 컨벤션, 코드 품질 컨벤션
  - 에러 처리 철학 (검증 실패 = Response return, 예외 = 시스템 에러만)
  - DTO 분리, Lombok 금지, record 사용, 소프트 삭제, 트랜잭션 관리
  - Hexagonal Architecture 계층 구조 및 코드 패턴 (resources/)
- **test-code-guidelines**: 테스트 코드 작성 규칙, TDD Red-Green-Refactor
  - 실패 먼저/해피 나중, 단일 assert, verify 필수, 하드코딩 검증
  - 테스트 피라미드 (Unit/Integration/E2E), 테스트 더블 가이드
- **ddd-dev-guidelines**: DDD 도메인 모델링, 레이어드 아키텍처 + DIP
  - Entity/VO/Domain Service 구분, 유스케이스 기반 객체 협력
  - 계층별 책임과 패키지 구조
- **error-fix-tracker**: 오류 수정 시 AS-IS/TO-BE/Why/동작원리/검증테스트 문서화
- **project-confirmed-rules**: 멘토 의견 + 학습 결과로 확정한 프로젝트 전용 규칙
  - Blue Book 스타일 (Domain Service → Repository 직접 호출)
  - Facade 판정 확정표 (어떤 도메인에 Facade가 필요한지)
  - 행위 배치 플로우차트 (Entity vs Domain Service vs Facade)
  - 핵심 도메인 모델 상세 (Inventory 행위, Order Aggregate, Product+Brand 조합)
  - 구현 순서 (Phase 1~7, Inside-Out 패턴)
- **aggregate-rules**: Aggregate 경계 규칙, 도메인 모델링 시 참조
  - Root 통한 접근, ID 참조, Aggregate 단위 Repository, 트랜잭션 단위
  - 프로젝트 Aggregate 경계 확정표 (12개 도메인)
- **domain-boundary-rules**: Domain Service 경계 가두기, 레이어 간 호출 규칙
  - 경계 가두기 의사결정 플로우차트, 도메인별 현황표
  - What/When/How 레이어 책임 프레임워크, DIP 목적/수단 구분
- **repository-rules**: Repository Interface 설계 규칙
  - 도메인 언어만 사용, Spring 기술 의존성 금지 (Page/Pageable/Sort)
  - 조회 전용 Query 경로 분리 (CQRS), 쿼리 객체 패턴, 정렬 DIP

### 슬래시 명령어
- `/dev-docs`: 세션 간 컨텍스트 보존을 위한 3파일 패턴(plan/context/tasks) 생성