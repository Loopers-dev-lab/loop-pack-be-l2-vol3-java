# CLAUDE.md

이 파일은 Claude Code가 프로젝트 작업 시 참조하는 컨텍스트 문서입니다.

## 프로젝트 개요

**loopers-java-spring-template** - Loopers에서 제공하는 Spring + Java 멀티모듈 템플릿 프로젝트입니다.

- **Group**: `com.loopers`
- **Language**: Java 21
- **Build Tool**: Gradle 8.13 (Kotlin DSL)
- **Framework**: Spring Boot 3.4.4

## 기술 스택

### Core
| 기술 | 버전 |
|------|------|
| Java | 21 |
| Gradle | 8.13 |
| Spring Boot | 3.4.4 |
| Spring Cloud | 2024.0.1 |
| Spring Dependency Management | 1.1.7 |

### Database & Cache
| 기술 | 용도 |
|------|------|
| Spring Data JPA | ORM |
| QueryDSL (jakarta) | 타입 세이프 쿼리 |
| MySQL | RDBMS |
| Spring Data Redis | 캐시/세션 |
| Spring Kafka | 메시지 브로커 |

### 테스트
| 기술 | 버전 |
|------|------|
| Testcontainers | 2.0.2 |
| Spring MockK | 4.0.2 |
| Mockito | 5.14.0 |
| Instancio JUnit | 5.0.2 |
| JaCoCo | 테스트 커버리지 |

### 문서화 & 모니터링
| 기술 | 버전/용도 |
|------|----------|
| SpringDoc OpenAPI | 2.7.0 |
| Micrometer + Prometheus | 메트릭 수집 |
| Micrometer Tracing (Brave) | 분산 추적 |
| Logback Slack Appender | 1.6.1 |

### 유틸리티
- Lombok
- Jackson (JSR310, Kotlin Module)

## 모듈 구조

```
Root
├── apps (실행 가능한 SpringBootApplication)
│   ├── commerce-api        # REST API 서버 (Web + OpenAPI)
│   ├── commerce-batch      # Spring Batch 애플리케이션
│   └── commerce-streamer   # Kafka 기반 스트리밍 애플리케이션
│
├── modules (재사용 가능한 Configuration)
│   ├── jpa                 # JPA + QueryDSL + MySQL 설정
│   ├── redis               # Redis 설정
│   └── kafka               # Kafka 설정
│
└── supports (부가 기능 Add-on)
    ├── jackson             # Jackson 직렬화 설정
    ├── logging             # 로깅 + Slack Appender
    └── monitoring          # Actuator + Prometheus 메트릭
```

### 모듈 의존성

| App | 의존 모듈 |
|-----|----------|
| commerce-api | jpa, redis, jackson, logging, monitoring |
| commerce-batch | jpa, redis, jackson, logging, monitoring |
| commerce-streamer | jpa, redis, kafka, jackson, logging, monitoring |

## 빌드 & 실행

### 빌드
```bash
./gradlew build
```

### 테스트
```bash
./gradlew test
```

테스트 설정:
- Timezone: `Asia/Seoul`
- Profile: `test`
- 병렬 실행: 비활성화 (`maxParallelForks = 1`)

### 로컬 환경 실행
```bash
# 인프라 (MySQL, Redis, Kafka 등)
docker-compose -f ./docker/infra-compose.yml up

# 모니터링 (Prometheus, Grafana)
docker-compose -f ./docker/monitoring-compose.yml up
```

Grafana: http://localhost:3000 (admin/admin)

## 컨벤션

### 모듈 규칙
- `apps`: BootJar 활성화, 일반 Jar 비활성화
- `modules`, `supports`: 일반 Jar 활성화, BootJar 비활성화
- `modules`, `supports`는 `java-library` 플러그인 사용
- 테스트 픽스처는 `java-test-fixtures` 플러그인으로 관리

### 버전 관리
- 프로젝트 버전 미지정 시 Git short hash 사용
- 의존성 버전은 `gradle.properties`에서 중앙 관리

## 주요 디렉토리

```
/docker          # Docker Compose 파일
/http            # HTTP 요청 테스트 파일
/gradle          # Gradle Wrapper
```

## 개발 컨벤션 (Strict Rules)

### Entity & Domain
- **Entity**: `@Setter` 사용 금지. 변경 로직은 도메인 메서드(예: `updatePassword()`)로 구현.
- **Lombok**: `@Getter`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)` 기본 사용.
- **Validation**: 생성자 시점에 `CoreException`을 사용하여 유효성 검증 수행.

### API & Exception
- **Response**: 모든 Controller 응답은 `com.loopers.interfaces.api.ApiResponse<T>`로 감싸서 반환.
- **Exception**: 예외 발생 시 `com.loopers.support.error.CoreException`과 `ErrorType`을 사용. Java 표준 예외(`IllegalArgumentException` 등) 사용 지양.

### Coding Style
- **Null Safety**: `Optional`을 적극 활용. `null`을 직접 반환하거나 파라미터로 받지 않음.
- **DI**: 생성자 주입(`@RequiredArgsConstructor`) 사용. Field Injection(`@Autowired`) 금지.

## 테스트 전략 (TDD Workflow)

**대원칙: Red(실패) -> Green(구현) -> Refactor(개선)** 순서를 반드시 준수한다.

### 1. 단위 테스트 (Unit Test)
- **대상**: Domain Entity, POJO
- **도구**: JUnit5, AssertJ
- **특징**: Spring Context 로딩 금지. 순수 자바 코드로 검증.
- **위치**: `apps/commerce-api/src/test/java/com/loopers/domain/**`

### 2. 통합 테스트 (Integration Test)
- **대상**: Service, Repository
- **도구**: `@SpringBootTest`, Testcontainers (MySQL)
- **규칙**: `com.loopers.utils.DatabaseCleanUp`을 사용하여 매 테스트 종료 후 데이터 초기화.
- **위치**: `apps/commerce-api/src/test/java/com/loopers/application/**`

### 3. E2E 테스트 (API Test)
- **대상**: Controller (HTTP 요청/응답)
- **도구**: `TestRestTemplate`
- **검증**: 실제 HTTP Status Code와 `ApiResponse` 본문 검증.

## 아키텍처 검증 (ArchUnit) — 절대 규칙

모든 코드는 `ArchitectureTest.java`의 **15개 룰을 무조건 통과**해야 한다. 위반 시 빌드 실패.
- DB/외부 연동 로직 추가 시: **구현체는 `infrastructure`에, 인터페이스는 `domain` 또는 `application`에 배치** (DIP 절대 준수).
- 실행: `./gradlew :apps:commerce-api:test --tests "com.loopers.architecture.ArchitectureTest"`

### 계층 의존성 (4개)
| 룰 | 설명 |
|-----|------|
| Domain Purity | domain → application/infrastructure/interfaces 의존 금지 |
| DIP (App→Infra) | application → infrastructure 의존 금지 |
| DIP (Interfaces→Infra) | interfaces → infrastructure 의존 금지 |
| Layered Architecture | 전체 계층 방향 검증 |

### 코드 품질 (5개)
| 룰 | 설명 |
|-----|------|
| No Field Injection | @Autowired 필드 주입 절대 금지. @RequiredArgsConstructor만 사용 |
| No Cyclic Dependencies | 패키지 간 순환 참조 완전 차단 |
| No Standard Output | System.out/err, printStackTrace 사용 금지. @Slf4j 사용 |
| Transactional Enforced | AppService public 메서드는 반드시 @Transactional 명시 |
| No Entity Leakage | Controller가 도메인 엔티티를 직접 반환 금지. ApiResponse<DTO>만 허용 |

### 네이밍 & 어노테이션 (3개)
| 룰 | 설명 |
|-----|------|
| Controller Convention | *Controller → interfaces 패키지 + @RestController |
| AppService Convention | *AppService → application 패키지 + @Service |
| Facade Convention | *Facade → application 패키지 + @Component |

### 접근 제어 (3개)
| 룰 | 설명 |
|-----|------|
| Controller→Repository 차단 | Controller에서 Repository 직접 사용 금지 |
| Facade→Repository 차단 | Facade에서 Repository 직접 사용 금지. AppService만 사용 |
| ApiResponse 강제 | Controller public 메서드 반환 타입은 반드시 ApiResponse |

## AI 페르소나 및 행동 지침
- **언어**: 한국어 (기술 용어는 영어 병기 가능)
- **우선순위**:
    1. 실제 실행 가능한 코드 제공
    2. 테스트 코드 우선 작성 (TDD)
    3. 기존 프로젝트 구조(Multi-module) 준수
- **금지사항**:
    - `System.out.println` 사용 금지 (로깅은 `@Slf4j` 사용)
    - 불필요한 주석이나 설명으로 답변 길게 하지 말 것.
    - 존재하지 않는 라이브러리를 임의로 추가하지 말 것.

## 아키텍처 및 계층별 책임 분리 (Layered + DIP)
이 프로젝트는 **Rich Domain Entity** 방식을 채택한다. Domain 클래스가 JPA `@Entity`를 직접 보유하여 별도의 JpaEntity 계층을 두지 않는다.

### 엔티티 분류

| 분류 | 대상 | ID 선언 방식 |
|------|------|-------------|
| **핵심 자산** | `Member`, `Brand`, `Product`, `Option`, `Order` | `extends BaseEntity` |
| **임시·매핑** | `CartItem`, `Like`, `OrderItem` | 클래스 내 직접 `@Id @GeneratedValue` |

- **핵심 자산 엔티티**: `of(id, ...)` 팩토리 없음. `create(...)` 팩토리만 사용. 단위 테스트에서는 `mock(Xxx.class)`로 대체.
- **임시·매핑 엔티티**: `of(id, ...)` / `create(...)` 팩토리 모두 유지.
- **Money/VO 변환**: VO 클래스(`Money`, `MemberId`, `Password` 등)에 `@Embeddable` 적용. 엔티티에서는 `@Embedded` + `@AttributeOverride`로 컬럼명 지정. `@Convert` 방식 사용 금지.
- **물리적 FK 제약 지양:** DB 레벨 FK 없이 논리적 연관관계만 ID로 가진다.
- **상태 관리:** 객체 상태 변경은 도메인 엔티티 내부 메서드로 구현하며, 실패 시 `CoreException`을 던진다.

### 계층별 책임

1. **Domain Layer (Entity, VO, Repository 인터페이스):** 비즈니스 규칙 검증과 객체 상태 변경 담당. Repository 인터페이스도 이곳에 위치한다.
2. **Application Layer (`~AppService`):** `@Transactional` 트랜잭션 제어. Repository로 데이터를 꺼내고 도메인 메서드를 호출한 뒤 저장한다.
3. **Orchestration Layer (`~Facade`):** Repository 직접 호출 금지. 복잡한 유스케이스에서 여러 `AppService`를 조립(Orchestration)한다.
4. **Infrastructure Layer:** Repository 구현체(`~RepositoryImpl`)와 JpaRepository 인터페이스가 위치한다. `BaseEntity`는 핵심 자산 Domain 클래스에서 상속받아 사용한다.