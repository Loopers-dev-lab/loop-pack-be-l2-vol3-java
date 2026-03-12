# CLAUDE.md

이 파일은 Claude Code가 프로젝트를 이해하는 데 필요한 컨텍스트를 제공합니다.

## 프로젝트 개요

**프로젝트명**: loopers-java-spring-template
**그룹 ID**: com.loopers
**라이선스**: LICENSE 파일 참조

커머스 도메인을 위한 Java/Spring Boot 기반 멀티 모듈 백엔드 템플릿 프로젝트입니다.

## 기술 스택 및 버전

| 기술 | 버전 |
|------|------|
| Java | 21 |
| Spring Boot | 3.4.4 |
| Spring Cloud Dependencies | 2024.0.1 |
| Spring Dependency Management | 1.1.7 |
| Lombok | Spring Boot BOM |
| QueryDSL | Spring Boot BOM (Jakarta) |
| SpringDoc OpenAPI | 2.7.0 |
| Micrometer | Spring Boot BOM |
| Testcontainers | Spring Boot BOM |
| JUnit 5 | Spring Boot BOM |
| Mockito | 5.14.0 |
| SpringMockK | 4.0.2 |
| Instancio JUnit | 5.0.2 |
| Slack Appender | 1.6.1 |

## 모듈 구조

```
loopers-java-spring-template/
├── apps/                          # 실행 가능한 애플리케이션 (BootJar)
│   ├── commerce-api/              # REST API 서버 (Web, OpenAPI)
│   ├── commerce-streamer/         # Kafka 스트림 처리 서버
│   └── commerce-batch/            # Spring Batch 애플리케이션
│
├── modules/                       # 공유 라이브러리 모듈
│   ├── jpa/                       # JPA + QueryDSL + MySQL
│   ├── redis/                     # Spring Data Redis
│   └── kafka/                     # Spring Kafka
│
├── supports/                      # 횡단 관심사 지원 모듈
│   ├── jackson/                   # Jackson 직렬화 설정
│   ├── logging/                   # 로깅 + Slack Appender
│   └── monitoring/                # Prometheus + Micrometer
│
├── docker/                        # Docker 관련 설정
└── http/                          # HTTP 요청 파일 (IntelliJ HTTP Client)
```

### 모듈 의존성 관계

- **commerce-api**: jpa, redis, jackson, logging, monitoring
- **commerce-streamer**: jpa, redis, kafka, jackson, logging, monitoring
- **commerce-batch**: jpa, redis, jackson, logging, monitoring

## 빌드 및 실행

```bash
# 전체 빌드
./gradlew build

# 특정 앱 실행
./gradlew :apps:commerce-api:bootRun
./gradlew :apps:commerce-streamer:bootRun
./gradlew :apps:commerce-batch:bootRun

# 테스트 실행
./gradlew test
```

## 테스트 환경

- 테스트 시 `Asia/Seoul` 타임존 사용
- 테스트 프로파일: `test`
- Testcontainers 사용 (MySQL, Redis, Kafka)
- JaCoCo 코드 커버리지 리포트 생성 (XML 포맷)

## 주요 설정

- **버전 관리**: Git 커밋 해시를 기본 버전으로 사용
- **빌드 타입**:
  - `apps/*` 모듈: BootJar (실행 가능한 JAR)
  - `modules/*`, `supports/*` 모듈: 일반 JAR (라이브러리)

## 코드 스타일

- Lombok 사용
- Jackson JSR310 모듈로 Java Time API 직렬화
- QueryDSL Jakarta 스펙 사용


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
- 실제 동작하지 않는 코드, 불필요한 Mock 데이터를 이요한 구현을 하지 말 것
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