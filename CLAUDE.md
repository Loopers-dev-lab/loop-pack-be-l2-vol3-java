# CLAUDE.md

이 파일은 Claude Code가 프로젝트를 이해하는 데 필요한 정보를 제공합니다.

## 프로젝트 개요

**loopers-java-spring-template** - 커머스 백엔드 서비스를 위한 멀티모듈 Java/Spring Boot 템플릿 프로젝트

- **그룹**: `com.loopers`
- **Java 버전**: 21
- **빌드 도구**: Gradle (Kotlin DSL)

## 기술 스택 및 버전

### Core
| 기술 | 버전 |
|------|------|
| Java | 21 |
| Spring Boot | 3.4.4 |
| Spring Cloud | 2024.0.1 |
| Spring Dependency Management | 1.1.7 |

### Database & Messaging
| 기술 | 버전 |
|------|------|
| MySQL | 8.0 |
| Redis | 7.0 |
| Kafka | 3.5.1 (Bitnami) |
| QueryDSL | Jakarta |

### API & Docs
| 기술 | 버전 |
|------|------|
| SpringDoc OpenAPI | 2.7.0 |

### Testing
| 기술 | 버전 |
|------|------|
| JUnit Platform | 5.x |
| Mockito | 5.14.0 |
| SpringMockk | 4.0.2 |
| Instancio JUnit | 5.0.2 |
| Testcontainers | (Spring Boot 관리) |

### Monitoring & Logging
| 기술 | 버전 |
|------|------|
| Micrometer (Prometheus) | (Spring Boot 관리) |
| Micrometer Tracing (Brave) | (Spring Boot 관리) |
| Logback Slack Appender | 1.6.1 |

## 모듈 구조

```
loopers-java-spring-template/
├── apps/                          # 실행 가능한 애플리케이션
│   ├── commerce-api/              # REST API 서버 (Web, Swagger, Actuator)
│   ├── commerce-batch/            # Spring Batch 애플리케이션
│   └── commerce-streamer/         # Kafka Consumer 애플리케이션
│
├── modules/                       # 공통 인프라 모듈
│   ├── jpa/                       # JPA, QueryDSL, MySQL 설정
│   ├── redis/                     # Spring Data Redis 설정
│   └── kafka/                     # Spring Kafka 설정
│
├── supports/                      # 지원 모듈
│   ├── jackson/                   # Jackson 직렬화 설정
│   ├── logging/                   # Logback, Slack Appender 설정
│   └── monitoring/                # Micrometer, Prometheus 설정
│
└── docker/                        # Docker Compose 파일
    ├── infra-compose.yml          # MySQL, Redis, Kafka
    └── monitoring-compose.yml     # Grafana, Prometheus
```

## 아키텍처 패턴 (commerce-api)

Layered Architecture를 따르며, 패키지 구조는 다음과 같습니다:

```
com.loopers/
├── CommerceApiApplication.java    # 메인 애플리케이션
├── application/                   # Application Layer (Facade)
├── domain/                        # Domain Layer (Service, Model, Repository Interface)
├── infrastructure/                # Infrastructure Layer (Repository 구현체)
├── interfaces/                    # Interface Layer (Controller, DTO)
└── support/                       # Support (Error handling 등)
```

## 빌드 및 실행

### 빌드
```bash
./gradlew build
```

### 테스트
```bash
./gradlew test
```
- 테스트 프로파일: `test`
- 타임존: `Asia/Seoul`
- Testcontainers를 사용하여 MySQL, Redis, Kafka 통합 테스트 지원

### 로컬 인프라 실행
```bash
# 인프라 (MySQL, Redis, Kafka)
docker compose -f docker/infra-compose.yml up -d

# 모니터링 (Grafana, Prometheus)
docker compose -f docker/monitoring-compose.yml up -d
```

### 애플리케이션 실행
```bash
# commerce-api
./gradlew :apps:commerce-api:bootRun

# commerce-batch
./gradlew :apps:commerce-batch:bootRun

# commerce-streamer
./gradlew :apps:commerce-streamer:bootRun
```

## 환경 프로파일

- `local` - 로컬 개발 환경
- `test` - 테스트 환경
- `dev` - 개발 서버
- `qa` - QA 서버
- `prd` - 운영 서버

## 주요 엔드포인트

### commerce-api
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Actuator: `http://localhost:8080/actuator`

### 로컬 인프라
- MySQL: `localhost:3306` (user: application / password: application / database: loopers)
- Redis Master: `localhost:6379`
- Redis Readonly: `localhost:6380`
- Kafka: `localhost:19092`
- Kafka UI: `http://localhost:9099`

## 코드 컨벤션

- Lombok 사용
- QueryDSL Jakarta 기반
- JaCoCo 코드 커버리지 활성화
- 테스트는 JUnit 5 + Mockito + Testcontainers 조합 사용
