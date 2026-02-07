# Loopers Commerce Platform - 개발 가이드

## 프로젝트 개요

Loopers에서 제공하는 Spring Boot 기반의 멀티모듈 커머스 플랫폼입니다.

### 주요 기술 스택 및 버전

#### Core
- **Java**: 21 (LTS)
- **Spring Boot**: 3.4.4
- **Spring Cloud**: 2024.0.1
- **Gradle**: 8.x (Kotlin DSL)

#### Framework & Libraries
- **Spring Data JPA**: 3.4.4 (with QueryDSL)
- **Spring Security**: Crypto 모듈 (BCrypt 암호화)
- **Spring Batch**: 5.x
- **Spring Kafka**: 3.x
- **Redis**: Lettuce 기반
- **MySQL**: 8.x (Production), TestContainers (Test)

#### API & Documentation
- **SpringDoc OpenAPI**: 2.7.0 (Swagger UI)
- **Jakarta Validation**: Bean Validation 3.0

#### Testing
- **JUnit 5**: Jupiter
- **AssertJ**: Fluent Assertions
- **Mockito**: 5.14.0
- **SpringMockK**: 4.0.2 (Kotlin Mock 지원)
- **Instancio**: 5.0.2 (Test Fixture 생성)
- **TestContainers**: MySQL, Redis

#### Monitoring & Logging
- **Spring Actuator**: Health Check, Metrics
- **Prometheus**: Metrics 수집
- **Grafana**: 시각화 대시보드
- **Logback**: 구조화된 로깅 (JSON/Plain)
- **Slack Appender**: 1.6.1 (알림)

#### Build & Code Quality
- **Jacoco**: 코드 커버리지
- **Lombok**: 보일러플레이트 제거

---

## 모듈 구조

### 전체 구조
```
Root
├── apps (실행 가능한 Spring Boot 애플리케이션)
│   ├── commerce-api       # REST API 서버
│   ├── commerce-batch     # 배치 작업
│   └── commerce-streamer  # Kafka 스트리밍
├── modules (재사용 가능한 인프라 설정)
│   ├── jpa                # JPA, QueryDSL, DataSource 설정
│   ├── redis              # Redis Cluster 설정
│   └── kafka              # Kafka Producer/Consumer 설정
└── supports (부가 기능 모듈)
    ├── jackson            # JSON 직렬화 설정
    ├── logging            # Logback 설정 (JSON/Plain/Slack)
    └── monitoring         # Actuator, Prometheus 설정
```

### 모듈 원칙
- **apps**: 각 모듈은 독립적으로 실행 가능한 SpringBootApplication
- **modules**: 도메인에 의존하지 않는 재사용 가능한 인프라 설정
- **supports**: 로깅, 모니터링 등 부가 기능 제공

### 의존성 규칙
- apps → modules, supports (의존 가능)
- modules ↔ modules (상호 의존 금지)
- supports ↔ supports (상호 의존 금지)
- modules, supports → apps (의존 불가)

---

## 아키텍처 및 레이어 구조

### 패키지 구조 (commerce-api 기준)
```
com.loopers
├── domain                    # 도메인 레이어
│   └── {domain-name}
│       ├── {Domain}Model.java       # JPA Entity (도메인 모델)
│       ├── {Domain}Service.java     # 도메인 비즈니스 로직
│       ├── {Domain}Repository.java  # Repository 인터페이스
│       └── {ValueObject}.java       # Value Object (record)
├── application               # 애플리케이션 레이어
│   └── {domain-name}
│       ├── {Domain}Facade.java      # 여러 도메인 서비스 조합
│       └── {Domain}Info.java        # 애플리케이션 DTO
├── infrastructure            # 인프라 레이어
│   ├── {domain-name}
│   │   ├── {Domain}JpaRepository.java     # Spring Data JPA
│   │   └── {Domain}RepositoryImpl.java    # Repository 구현체
│   ├── jpa/converter
│   │   └── {ValueObject}Converter.java    # JPA AttributeConverter
│   ├── security
│   │   └── BCryptPasswordHasher.java      # 암호화 구현체
│   └── config
│       └── SecurityConfig.java            # 설정
├── interfaces                # 인터페이스 레이어
│   └── api
│       ├── {domain-name}
│       │   ├── {Domain}V1Controller.java  # REST Controller
│       │   ├── {Domain}V1ApiSpec.java     # OpenAPI 명세 (interface)
│       │   └── {Domain}V1Dto.java         # API DTO (record)
│       ├── ApiResponse.java               # 공통 응답 래퍼
│       └── ApiControllerAdvice.java       # 전역 예외 처리
└── support                   # 공통 지원
    └── error
        ├── CoreException.java             # 도메인 예외
        └── ErrorType.java                 # 에러 타입 enum
```

### 레이어별 역할

#### 1. Domain Layer (도메인 레이어)
- **책임**: 핵심 비즈니스 로직과 규칙
- **구성요소**:
    - `{Domain}Model`: JPA Entity, BaseEntity 상속, 도메인 객체
    - `{Domain}Service`: 도메인 비즈니스 로직, 트랜잭션 관리
    - `{Domain}Repository`: 인터페이스 (구현체는 Infrastructure)
    - Value Objects: record 타입, 불변 객체, 생성자에서 검증

#### 2. Application Layer (애플리케이션 레이어)
- **책임**: 유스케이스 조합, 여러 도메인 서비스 조율
- **구성요소**:
    - `{Domain}Facade`: 여러 도메인 서비스를 조합한 유스케이스
    - `{Domain}Info`: 애플리케이션 레벨 DTO

#### 3. Infrastructure Layer (인프라 레이어)
- **책임**: 외부 시스템 연동, 기술적 구현
- **구성요소**:
    - `{Domain}RepositoryImpl`: Repository 인터페이스 구현
    - `{Domain}JpaRepository`: Spring Data JPA 인터페이스
    - JPA Converter: Value Object ↔ DB 컬럼 변환
    - 외부 API 클라이언트, 암호화 구현체 등

#### 4. Interfaces Layer (인터페이스 레이어)
- **책임**: 외부와의 통신 (REST API, gRPC 등)
- **구성요소**:
    - `{Domain}V1Controller`: REST API 엔드포인트
    - `{Domain}V1ApiSpec`: OpenAPI 명세 인터페이스 (Swagger 어노테이션)
    - `{Domain}V1Dto`: API 요청/응답 DTO (record)
    - `ApiResponse`: 공통 응답 래퍼 (meta + data)
    - `ApiControllerAdvice`: 전역 예외 처리

---

## 코드 컨벤션

### 1. 네이밍 규칙

#### 클래스/인터페이스
- **Entity**: `{Domain}Model` (예: `MemberModel`, `OrderModel`)
- **Service**: `{Domain}Service` (예: `MemberService`)
- **Repository Interface**: `{Domain}Repository` (예: `MemberRepository`)
- **Repository Impl**: `{Domain}RepositoryImpl` (예: `MemberRepositoryImpl`)
- **JPA Repository**: `{Domain}JpaRepository` (예: `MemberJpaRepository`)
- **Controller**: `{Domain}V{version}Controller` (예: `MemberV1Controller`)
- **API Spec**: `{Domain}V{version}ApiSpec` (예: `MemberV1ApiSpec`)
- **DTO**: `{Domain}V{version}Dto` (예: `MemberV1Dto`)
- **Value Object**: `{Name}` (예: `MemberId`, `Email`, `BirthDate`)
- **Facade**: `{Domain}Facade` (예: `MemberFacade`)
- **Exception**: `{Name}Exception` (예: `CoreException`)

#### 메서드
- **조회**: `get{Entity}By{Condition}` (예: `getMemberByMemberId`)
- **저장**: `save`, `register`, `create`
- **수정**: `update`, `modify`
- **삭제**: `delete`, `remove`
- **존재 확인**: `existsBy{Condition}` (예: `existsByMemberId`)
- **검증**: `validate{Target}` (예: `validatePassword`)

#### 변수
- **상수**: `UPPER_SNAKE_CASE` (예: `VALID_MEMBER_ID`, `PASSWORD_PATTERN`)
- **일반 변수**: `camelCase` (예: `memberId`, `rawPassword`)

### 2. 타입 사용 규칙

#### Value Object
- **타입**: `record` 사용 (Java 17+)
- **검증**: Compact Constructor에서 수행
- **불변성**: 모든 필드 final (record 기본)
- **예시**:
```java
public record MemberId(String value) {
    private static final Pattern PATTERN = Pattern.compile("^[A-Za-z0-9]{1,10}$");
    
    public MemberId {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "memberId가 비어 있습니다");
        }
        value = value.trim();
        if (!PATTERN.matcher(value).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "memberId는 영문+숫자, 1~10자로 이루어져야 합니다");
        }
    }
}
```

#### DTO (Data Transfer Object)
- **타입**: `record` 사용
- **검증**: Jakarta Validation 어노테이션 사용
- **변환**: 정적 팩토리 메서드 `from()` 제공
- **예시**:
```java
public class MemberV1Dto {
    public record RegisterRequest(
        @NotBlank String memberId,
        @NotBlank String password,
        @NotBlank String email,
        @NotBlank String birthDate,
        @NotBlank String name,
        @NotNull Gender gender
    ) {}
    
    public record MemberResponse(
        Long id,
        String memberId,
        String email,
        String birthDate,
        String name,
        Gender gender
    ) {
        public static MemberResponse from(MemberModel member) {
            return new MemberResponse(
                member.getId(),
                member.getMemberId().value(),
                member.getEmail().address(),
                member.getBirthDate().asString(),
                member.getName().value(),
                member.getGender()
            );
        }
    }
}
```

#### Entity
- **타입**: `class` (JPA Entity)
- **상속**: `BaseEntity` 상속 (id, createdAt, updatedAt, deletedAt)
- **생성자**: protected 기본 생성자 + public 생성자 체이닝
- **필드**: private, @Getter 사용
- **예시**:
```java
@Entity
@Table(name = "member")
public class MemberModel extends BaseEntity {
    @Getter
    @Convert(converter = MemberIdConverter.class)
    @Column(nullable = false, unique = true, length = 10)
    private MemberId memberId;
    
    protected MemberModel() {}
    
    public MemberModel(String memberId, String password) {
        this.memberId = new MemberId(memberId);
        this.password = password;
    }
}
```

### 3. 예외 처리

#### CoreException
- **용도**: 도메인 예외 표현
- **구조**: `ErrorType` + 커스텀 메시지
- **예시**:
```java
throw new CoreException(ErrorType.BAD_REQUEST, "이미 가입된 ID 입니다.");
```

#### ErrorType
- **타입**: enum
- **필드**: `HttpStatus status`, `String code`, `String message`
- **종류**: `INTERNAL_ERROR`, `BAD_REQUEST`, `NOT_FOUND`, `CONFLICT`

#### 전역 예외 처리
- **클래스**: `ApiControllerAdvice` (@RestControllerAdvice)
- **처리 대상**:
    - `CoreException`: 도메인 예외
    - `MethodArgumentNotValidException`: Validation 실패
    - `HttpMessageNotReadableException`: JSON 파싱 실패
    - `NoResourceFoundException`: 404 Not Found
    - `Throwable`: 예상치 못한 예외

### 4. API 응답 구조

#### ApiResponse
```java
public record ApiResponse<T>(Metadata meta, T data) {
    public record Metadata(Result result, String errorCode, String message) {
        public enum Result { SUCCESS, FAIL }
    }
}
```

#### 성공 응답
```json
{
  "meta": {
    "result": "SUCCESS",
    "errorCode": null,
    "message": null
  },
  "data": {
    "id": 1,
    "memberId": "testuser1",
    "email": "test@example.com"
  }
}
```

#### 실패 응답
```json
{
  "meta": {
    "result": "FAIL",
    "errorCode": "Bad Request",
    "message": "이미 가입된 ID 입니다."
  },
  "data": null
}
```

### 5. JPA 관련

#### BaseEntity
- **필드**: `id`, `createdAt`, `updatedAt`, `deletedAt`
- **기능**:
    - `@PrePersist`: createdAt, updatedAt 자동 설정, guard() 호출
    - `@PreUpdate`: updatedAt 자동 갱신, guard() 호출
    - `delete()`: Soft Delete (멱등성 보장)
    - `restore()`: 삭제 취소 (멱등성 보장)
    - `guard()`: 엔티티 검증 (하위 클래스에서 오버라이드)

#### JPA Converter
- **용도**: Value Object ↔ DB 컬럼 변환
- **어노테이션**: `@Converter(autoApply = false)` (명시적 적용)
- **null-safety**: null 체크 필수
- **예시**:
```java
@Converter(autoApply = false)
public class MemberIdConverter implements AttributeConverter<MemberId, String> {
    @Override
    public String convertToDatabaseColumn(MemberId attribute) {
        return attribute == null ? null : attribute.value();
    }
    
    @Override
    public MemberId convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new MemberId(dbData);
    }
}
```

### 6. 의존성 주입
- **방식**: 생성자 주입 (Constructor Injection)
- **어노테이션**: `@RequiredArgsConstructor` (Lombok)
- **필드**: `private final` 사용

### 7. 트랜잭션
- **Service 레이어**: `@Transactional` 사용
- **읽기 전용**: `@Transactional(readOnly = true)`
- **쓰기**: `@Transactional` (기본)

---

## 테스트 전략

### 테스트 피라미드 (역할 분리)

| 레벨 | 대상 | 환경 | 목적 |
|------|------|------|------|
| **Unit** | 도메인 모델, VO | Spring 없이 JVM | 순수 로직/규칙 검증 |
| **Integration** | Service, Facade | @SpringBootTest + TestContainers | 비즈니스 흐름 검증 |
| **E2E** | REST API | TestRestTemplate | HTTP 요청/응답 시나리오 |

### 테스트 레벨

#### 1. 단위 테스트 (Unit Test)
- **대상**: Value Object, 도메인 로직 (Spring 없이 순수 JVM)
- **명명**: `{ClassName}UnitTest`
- **어노테이션**: `@Test`, `@DisplayName`, `@Nested`
- **패턴**: 3A (Arrange - Act - Assert)
- **예시**:
```java
@DisplayName("회원 모델을 생성할 때, ")
@Nested
class Create {
    @DisplayName("ID 가 영문 및 숫자 10자 이내 형식에 맞지 않으면, User 객체 생성에 실패한다.")
    @Test
    void createsMemberModel_whenIdIsInvalid() {
        // arrange
        String memberId = "invalid_id!";
        
        // act
        CoreException result = assertThrows(CoreException.class, () ->
            new MemberModel(memberId, "password123"));
        
        // assert
        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }
}
```

#### 2. 통합 테스트 (Integration Test)
- **대상**: Service, Facade (여러 컴포넌트 연결 상태에서 비즈니스 흐름 검증)
- **명명**: `{ClassName}IntegrationTest`
- **어노테이션**: `@SpringBootTest`
- **인프라**: TestContainers (MySQL, Redis)
- **격리**: `DatabaseCleanUp.truncateAllTables()` (@AfterEach)
- **예시**:
```java
@SpringBootTest
class MemberServiceIntegrationTest {
    @Autowired
    private MemberService memberService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    void register_withValidInfo_createsMember() {
        // arrange
        String memberId = "testuser1";

        // act
        MemberModel result = memberService.register(...);

        // assert
        assertThat(result.getMemberId().value()).isEqualTo(memberId);
    }
}
```

#### 3. E2E 테스트 (End-to-End Test)
- **대상**: REST API (Controller → Service → DB 전체 흐름)
- **명명**: `{ClassName}E2ETest`
- **어노테이션**: `@SpringBootTest(webEnvironment = RANDOM_PORT)`
- **클라이언트**: `TestRestTemplate`
- **예시**:
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class MemberV1ApiE2ETest {
    @Autowired
    private TestRestTemplate testRestTemplate;

    @Test
    void register_withValidRequest_returnsCreatedMember() {
        // arrange
        var request = new MemberV1Dto.RegisterRequest("testuser1", "Pass1234!", ...);

        // act
        var response = testRestTemplate.postForEntity("/api/v1/members/register", request, ApiResponse.class);

        // assert
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }
}
```

### 테스트 원칙
1. **3A 패턴 준수**: Arrange - Act - Assert
2. **@DisplayName 필수**: 한글로 명확한 테스트 의도 표현
3. **@Nested 활용**: 테스트 그룹화 (예: Create, Get, Update, Delete)
4. **AssertJ 사용**: `assertThat()`, `assertAll()` 활용
5. **테스트 격리**: 각 테스트는 독립적으로 실행 가능해야 함
6. **실제 동작 검증**: Mock 최소화, 실제 DB/API 호출 우선

---

## 개발 규칙

### 핵심 원칙: 속도보다 통제
- AI는 코드의 의도, 변경영향, 책임에 대한 컨텍스트를 지속 유지할 수 없음
- 개발자가 의도를 정의하고, AI가 승인된 범위 내에서만 구현

### Claude 역할 제한
| 허용 | 금지 |
|------|------|
| 제안, 대안 제시 | 임의 설계 결정 |
| 승인된 범위 내 구현 | 요구사항 확장/범위 초과 |
| 테스트 작성 | 테스트 삭제, @Disabled, assertion 약화 |
| 승인 후 리팩토링 | 동작 변경, 기능 추가 |

### 진행 Workflow - 증강 코딩
- **대원칙**: 방향성 및 주요 의사 결정은 개발자에게 제안만 할 수 있으며, 최종 승인된 사항을 기반으로 작업을 수행
- **중간 결과 보고**: AI가 반복적인 동작을 하거나, 요청하지 않은 기능을 구현, 테스트 삭제를 임의로 진행할 경우 개발자가 개입
- **설계 주도권 유지**: AI가 임의판단을 하지 않고, 방향성에 대한 제안 등을 진행할 수 있으나 개발자의 승인을 받은 후 수행

---

### TDD Workflow (Red → Green → Refactor)

> 테스트는 구현 검증이 아니라 **설계 단위 검증**이다.

모든 테스트는 3A 원칙으로 작성할 것 (Arrange - Act - Assert)

#### 🔴 Red Phase: 실패하는 테스트 먼저 작성
- 요구사항을 테스트 케이스로 정의
- **반드시 실패 확인** (컴파일 에러가 아닌 Assertion 실패)
- 프로덕션 코드 없으면 최소 껍데기만 생성
- **이 단계에서 로직 구현 금지**

#### 🟢 Green Phase: 테스트를 통과하는 최소 코드 작성
- Red의 테스트가 **딱** 통과하는 코드만 작성
- **오버엔지니어링 금지**: 미래 요구사항 예측 구현 금지
- 기존 테스트도 모두 통과해야 함

#### 🔵 Refactor Phase: 코드 품질 개선 (동작 변경 없이)
- 중복 제거, 네이밍 개선, unused import 제거
- **모든 테스트 케이스가 통과해야 함**
- **새 기능 추가 금지** (새 기능은 다시 Red부터)

---

## 주의사항

### 1. Never Do (절대 금지)
- ❌ **실제 동작하지 않는 코드 작성 금지**
    - Mock 데이터로만 동작하는 구현 금지
    - 실제 DB, API 호출 없이 가짜 응답 반환 금지
- ❌ **null-safety 위반 금지**
    - Java의 경우 `Optional` 활용 필수
    - Value Object는 생성자에서 null 검증
    - JPA Converter에서 null 체크
- ❌ **println 코드 남기지 말 것**
    - 디버깅용 `System.out.println()` 제거
    - 로깅이 필요하면 `@Slf4j` 사용
- ❌ **테스트 임의 삭제/수정 금지**
    - 실패하는 테스트를 삭제하지 말 것
    - `@Disabled`, `@Ignore` 사용 금지
    - 테스트를 통과시키기 위해 assertion 약화 금지

### 2. Recommendation (권장사항)
- ✅ **실제 API를 호출해 확인하는 E2E 테스트 코드 작성**
    - TestRestTemplate 사용
    - 실제 HTTP 요청/응답 검증
- ✅ **재사용 가능한 객체 설계**
    - Value Object 활용
    - 불변 객체 우선
    - 정적 팩토리 메서드 제공
- ✅ **성능 최적화에 대한 대안 및 제안**
    - N+1 문제 해결 (Fetch Join, Batch Size)
    - 인덱스 설계
    - 캐싱 전략 (Redis)
- ✅ **개발 완료된 API의 경우, `.http/**.http`에 분류해 작성**
    - IntelliJ HTTP Client 파일 작성
    - 환경별 변수 관리 (`http-client.env.json`)

### 3. Priority (우선순위)
1. **실제 동작하는 해결책만 고려**
    - 이론적 해결책보다 실제 동작하는 코드 우선
2. **null-safety, thread-safety 고려**
    - Optional 활용
    - 불변 객체 사용
    - 동시성 이슈 고려
3. **테스트 가능한 구조로 설계**
    - 의존성 주입
    - 인터페이스 분리
    - Spy 패턴 활용
4. **기존 코드 패턴 분석 후 일관성 유지**
    - 네이밍 규칙 준수
    - 레이어 구조 준수
    - 기존 코드 스타일 따르기

---

## 도메인 분석 (User) - Week 1 범위

### 필드 검증
- **loginId**: 영문+숫자만 허용
- **email**: 이메일 형식 검증
- **birthDate**: yyyy-MM-dd 형식
- **name**: 1~50자 (조회 시 마지막 글자 `*` 마스킹)

### 비즈니스 규칙
- **비밀번호**: 8~16자, 영문 대소문자+숫자+특수문자 모두 포함
- **비밀번호 제약**: 생년월일 포함 불가
- **중복 가입 방지**: loginId 중복 체크
- **암호화**: BCrypt

### API 엔드포인트 (Week 1)
| API | 설명 | 인증 |
|-----|------|------|
| `POST /api/v1/users/register` | 회원가입 | 없음 |
| `GET /api/v1/users/me` | 내 정보 조회 | `X-Loopers-LoginId`, `X-Loopers-LoginPw` |
| `PATCH /api/v1/users/me/password` | 비밀번호 수정 | `X-Loopers-LoginId`, `X-Loopers-LoginPw` |

---

## 환경 설정

### 프로파일
- **local**: 로컬 개발 환경
- **test**: 테스트 환경 (TestContainers)
- **dev**: 개발 서버
- **qa**: QA 서버
- **prd**: 운영 서버

### 인프라 실행
```bash
# MySQL, Redis, Kafka 실행
docker-compose -f ./docker/infra-compose.yml up

# Prometheus, Grafana 실행
docker-compose -f ./docker/monitoring-compose.yml up
```

### Swagger UI
- **URL**: http://localhost:8080/swagger-ui.html
- **활성화**: local, test 프로파일에서만

### Grafana
- **URL**: http://localhost:3000
- **계정**: admin / admin

---

## 참고사항

### Lombok 사용
- `@Getter`: 필드별 적용 (클래스 레벨 지양)
- `@RequiredArgsConstructor`: 생성자 주입
- `@Slf4j`: 로깅

### QueryDSL
- Q-Type 자동 생성
- `build/generated/sources/annotationProcessor` 경로

### TestFixtures
- `modules:jpa`: `DatabaseCleanUp`, `MySqlTestContainersConfig`
- `modules:redis`: `RedisCleanUp`, `RedisTestContainersConfig`

### Jacoco
- 테스트 커버리지 측정
- XML 리포트 생성 (CI/CD 연동)

---

## 추가 리소스

### 프로젝트 파일
- `README.md`: 프로젝트 개요 및 시작 가이드
- `.codeguide/loopers-1-week.md`: 1주차 구현 퀘스트
- `http/commerce-api/example-v1.http`: API 테스트 예시

### 설정 파일
- `gradle.properties`: 버전 관리
- `build.gradle.kts`: 빌드 설정
- `settings.gradle.kts`: 멀티모듈 설정
- `application.yml`: 애플리케이션 설정
- `jpa.yml`, `redis.yml`, `kafka.yml`: 모듈별 설정

---

## 버전 관리

### Git 전략
- 버전: Git Hash 기반 (`getGitHash()`)
- 브랜치: feature, develop, main

### 빌드
```bash
# 전체 빌드
./gradlew build

# 특정 모듈 빌드
./gradlew :apps:commerce-api:build

# 테스트 실행
./gradlew test

# 커버리지 리포트
./gradlew jacocoTestReport
```

---

이 문서는 프로젝트의 코드베이스를 분석하여 작성되었으며, 실제 구현된 패턴과 규칙을 반영합니다.
