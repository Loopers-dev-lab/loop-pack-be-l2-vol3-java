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
| ktlint Plugin | 12.1.2 |
| ktlint | 1.0.1 |

## 모듈 구조

```
loopers-java-spring-template/
├── apps/                          # 실행 가능한 Spring Boot 애플리케이션 (BootJar)
│   ├── commerce-api/              # REST API 서버 (Servlet, Swagger UI)
│   ├── commerce-batch/            # Spring Batch 기반 배치 애플리케이션
│   └── commerce-streamer/         # Kafka Consumer 기반 스트리밍 애플리케이션
├── modules/                       # 재사용 가능한 인프라 모듈 (Jar, java-library)
│   ├── jpa/                       # JPA + Querydsl + MySQL DataSource 설정
│   ├── redis/                     # Redis Master/Replica 설정
│   └── kafka/                     # Kafka Producer/Consumer 설정
├── supports/                      # 공통 지원 모듈 (Jar)
│   ├── jackson/                   # Jackson ObjectMapper 설정
│   ├── logging/                   # Logback + Slack Appender + Tracing
│   └── monitoring/                # Actuator + Prometheus 메트릭
├── docker/
│   ├── infra-compose.yml          # MySQL, Redis(Master/Replica), Kafka(KRaft), Kafka UI
│   └── monitoring-compose.yml     # Prometheus, Grafana
└── http/                          # IntelliJ HTTP Client 요청 파일
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
├── interfaces/api/         # Controller, DTO, ApiSpec (Swagger)
├── application/            # Facade, Info (유스케이스 조합 계층)
├── domain/                 # Model, Repository(interface), Service
├── infrastructure/         # Repository 구현체 (JPA)
└── support/error/          # CoreException, ErrorType
```

## 빌드 및 실행

```bash
# 인프라 실행 (MySQL, Redis, Kafka)
docker compose -f docker/infra-compose.yml up -d

# 모니터링 실행 (Prometheus, Grafana)
docker compose -f docker/monitoring-compose.yml up -d

# 빌드
./gradlew clean build

# 테스트 (프로파일: test, 타임존: Asia/Seoul)
./gradlew test

# 애플리케이션 실행
./gradlew :apps:commerce-api:bootRun
./gradlew :apps:commerce-batch:bootRun -Djob.name={JOB_NAME}
./gradlew :apps:commerce-streamer:bootRun
```

## 주요 설정

- **테스트**: JUnit Platform, `spring.profiles.active=test`, `user.timezone=Asia/Seoul`, maxParallelForks=1
- **JPA**: `open-in-view=false`, `ddl-auto=none`, `default_batch_fetch_size=100`, UTC 타임존 저장
- **Redis**: Master/Replica 구조, Spring Data Redis Repository 비활성화
- **Kafka**: KRaft 모드, StringSerializer/JsonSerializer, Manual ACK
- **모니터링**: Actuator 포트 8081, Prometheus + Grafana (localhost:3000)
- **API 문서**: SpringDoc OpenAPI (localhost:8080/swagger-ui.html, prod 비활성화)

---

## 개발 규칙

### 진행 Workflow - 증강 코딩

- **대원칙** : 방향성 및 주요 의사 결정은 개발자에게 제안만 할 수 있으며, 최종 승인된 사항을 기반으로 작업을 수행.
- **중간 결과 보고** : AI 가 반복적인 동작을 하거나, 요청하지 않은 기능을 구현, 테스트 삭제를 임의로 진행할 경우 개발자가 개입.

### 개발 Workflow - TDD (Red > Green > Refactor)

- 모든 테스트는 3A 원칙으로 작성할 것 (Arrange - Act - Assert)

#### 1. Red Phase : 실패하는 테스트 먼저 작성
#### 2. Green Phase : 테스트를 통과하는 코드 작성
#### 3. Refactor Phase : 불필요한 코드 제거 및 품질 개선

---

## 주의사항

### 1. Never Do

- 실제 동작하지 않는 코드, 불필요한 Mock 데이터를 이용하지 말 것
- null-safety 하게 작성 (Optional 활용)
- println 금지
