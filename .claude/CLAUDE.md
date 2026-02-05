# CLAUDE.md

이 파일은 Claude Code가 이 프로젝트를 이해하는 데 필요한 컨텍스트를 제공합니다.

## 프로젝트 개요

Loopers에서 제공하는 Spring + Java 기반 멀티 모듈 템플릿 프로젝트입니다. 커머스 도메인을 위한 API, Batch, Streamer 애플리케이션을 포함합니다.

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