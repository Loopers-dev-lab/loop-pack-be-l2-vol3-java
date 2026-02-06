# CLAUDE.md

## 1. 기술스택 및 버전

| 구분 | 기술 | 버전 |
|------|------|------|
| Language | Java | 21 |
| Language | Kotlin | 2.0.20 |
| Framework | Spring Boot | 3.4.4 |
| Framework | Spring Cloud | 2024.0.1 |
| Build Tool | Gradle (Kotlin DSL) | - |
| API Docs | SpringDoc OpenAPI | 2.7.0 |
| ORM | QueryDSL | Jakarta |
| Test | Spring MockK | 4.0.2 |
| Test | Mockito | 5.14.0 |
| Test | Instancio JUnit | 5.0.2 |
| Test | TestContainers | (MySQL, Redis, Kafka) |
| Lint | ktLint | 1.0.1 |

### 1.1 빌드 및 테스트 명령어

| 명령어 | 설명 |
|--------|------|
| `./gradlew :apps:commerce-api:test` | commerce-api 모듈 테스트 |
| `./gradlew :apps:commerce-api:build` | commerce-api 모듈 빌드 |
| `./gradlew test` | 전체 테스트 |
| `./gradlew clean build` | 클린 빌드 |
| `./gradlew :apps:commerce-api:jacocoTestReport` | commerce-api 커버리지 리포트 생성 |

## 2. 모듈 구조

```
apps/
├── commerce-api      # REST API 서버 (Spring Web + JPA + Redis)
├── commerce-streamer # Kafka 스트림 처리
└── commerce-batch    # Spring Batch 배치 작업

modules/
├── jpa               # JPA + QueryDSL + MySQL (TestContainers)
├── redis             # Spring Data Redis (TestContainers)
└── kafka             # Spring Kafka (TestContainers)

supports/
├── jackson           # Jackson 직렬화 설정
├── logging           # 로깅 설정 (Prometheus + Brave + Slack Appender)
└── monitoring        # 모니터링 설정 (Prometheus)
```

## 3. 패키지 구조 (commerce-api)

```
com.loopers
├── CommerceApiApplication.java    # Spring Boot 진입점
└── {domain}/                      # 도메인별 패키지 (예: user, product, order)
    ├── application/               # 애플리케이션 서비스 레이어
    │   └── service/               # 애플리케이션 서비스
    │   └── facade/                # 퍼사드 서비스
    │   └── repository/            # 리포지토리 인터페이스 (CQRS)
    │       └── {Domain}CommandRepository  # 명령 (save, delete)
    │       └── {Domain}QueryRepository    # 조회 (find, exists)
    │   └── dto/                   # DTO (CQRS)
    │       └── command/           # 명령 DTO
    │       └── query/             # 조회 DTO
    ├── domain/
    │   └── model/                 # 도메인 모델
    │       └── enum/              # 도메인 내 공통 Enum
    │       └── vo/                # Value Object (예: Password)
    │   └── event/                 # 도메인 이벤트
    │   └── service/               # 도메인 서비스
    ├── infrastructure/            # 인프라 레이어 (Repository 구현 등)
    │   └── jpa/                   # JPA 레포지토리
    │   └── repository/            # 애플리케이션 레포지토리 구현체 (CQRS)
    │       └── {Domain}CommandRepositoryImpl  # 명령 구현체
    │       └── {Domain}QueryRepositoryImpl    # 조회 구현체
    │   └── entity/                # JPA 엔티티
    ├── interfaces/                # 프레젠테이션 레이어 (Controller)
    │   └── event/                 # 이벤트 리스너
    │   └── controller/            # REST 컨트롤러
    │       └── request/           # 요청 객체
    │       └── response/          # 응답 객체
    └── support/                   # 도메인 내 공통 모듈
        └── common/                # 공통 유틸리티 (예: Mapper, Validator, PasswordEncoder 등)
            └── error/             # 에러 핸들링 (CoreException, ErrorType)
        └── config/                # 도메인별 설정 관련 (예: Kafka Producer/Consumer 설정 등)
```

## 4. 개발규칙

### 4.1 진행 Workflow - 증강 코딩

| 원칙 | 설명 |
|------|------|
| **대원칙** | 방향성 및 주요 의사 결정은 개발자에게 제안만 할 수 있으며, 최종 승인된 사항을 기반으로 작업을 수행 |
| **중간 결과 보고** | AI가 반복적인 동작을 하거나, 요청하지 않은 기능을 구현, 테스트 삭제를 임의로 진행할 경우 개발자가 개입 |
| **설계 주도권 유지** | AI가 임의판단을 하지 않고, 방향성에 대한 제안 등을 진행할 수 있으나 개발자의 승인을 받은 후 수행 |

### 4.2 개발 Workflow - TDD (Red > Green > Refactor)

- 모든 테스트는 **3A 원칙**으로 작성: Arrange - Act - Assert

| Phase | 설명 |
|-------|------|
| **1. Red Phase** | 실패하는 테스트 먼저 작성. 요구사항을 만족하는 기능 테스트 케이스 작성 |
| **2. Green Phase** | 테스트를 통과하는 코드 작성. Red Phase의 테스트가 모두 통과할 수 있는 코드 작성. 오버엔지니어링 금지 |
| **3. Refactor Phase** | 불필요한 코드 제거 및 품질 개선. 불필요한 private 함수 지양, 객체지향적 코드 작성. unused import 제거. 성능 최적화. 모든 테스트 케이스가 통과해야 함 |

### 4.3 테스트 컨벤션

#### 테스트 커버리지 목표
- **100%에 가깝게** 테스트 커버리지를 채울 것
- 모든 public 메서드, 분기(branch), 예외 케이스를 테스트로 검증

#### @DisplayName 작성 규칙 (필수)
- **형식**: `[메서드명()] 조건 -> 결과. 상세 설명`
- **아주 자세하게 작성**: 테스트만 보고도 요구사항을 파악할 수 있어야 함
- 예시:
  - `[POST /api/v1/users] 유효한 회원가입 요청 -> 201 Created. 응답: id, loginId, name, birthday, email 포함`
  - `[UserCommandRepository.save()] 유효한 User 저장 -> ID가 할당된 User 반환`
  - `[Password.create()] 8자 미만 비밀번호 -> INVALID_PASSWORD_FORMAT 예외`

#### 예외 검증 패턴
```java
// Act
CoreException exception = assertThrows(CoreException.class,
    () -> targetMethod(args));

// Assert
assertAll(
    () -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.XXX),
    () -> assertThat(exception.getMessage()).isEqualTo(ErrorType.XXX.getMessage())
);
```

#### 통합 테스트 설정
```java
@SpringBootTest
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
class SomeIntegrationTest {
    // ...
}
```

#### E2E 테스트 패턴
- 위치: `src/test/java/com/loopers/{domain}/interfaces/{Domain}ControllerE2ETest.java` (controller 하위가 아님)
- 어노테이션: `@SpringBootTest`, `@AutoConfigureMockMvc`, `@ActiveProfiles("test")`, `@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})`
- 테스트 격리: `@AfterEach`에서 `DatabaseCleanUp.truncateAllTables()` 호출 (`@Transactional` 미사용)
- 테스트 데이터: API 호출 헬퍼 메서드로 직접 생성 (예: `signUpUser()`)
- 테스트 구조: `@Nested` 클래스로 엔드포인트별 그룹화

#### 단위 테스트 Mock 패턴
- `@ExtendWith(MockitoExtension.class)` + `@Mock` + `@BeforeEach`에서 수동 생성자 주입
- BDDMockito: `given().willReturn()`, `willDoNothing()`, `willThrow()`
- 검증: `verify()`, `never()`
- 헤더 검증 파라미터화: `@ParameterizedTest` + `@NullAndEmptySource` + `@ValueSource(strings = {"  ", "\t"})`

#### ErrorType 추가 시 체크리스트
- `ErrorType` enum에 새 값 추가
- `ErrorTypeTest.errorTypeProvider()`에 테스트 케이스 추가
- `ErrorTypeTest.enumConstantCount()`의 `hasSize(N)` 값을 N+1로 업데이트

### 4.4 커밋 메시지 컨벤션

**형식**: `{type}: {한국어 설명}`

| type | 용도 |
|------|------|
| `feat` | 새 기능 추가 |
| `fix` | 버그 수정 |
| `test` | 테스트 추가/수정 |
| `refactor` | 리팩토링 (기능 변경 없음) |
| `docs` | 문서 추가/수정 |
| `chore` | 빌드 설정, 의존성 관리 |
| `init` | 초기 설정 |

- 본문: 변경된 파일/클래스 목록을 `-` 리스트로 기술

### 4.5 에러 처리 패턴

모든 비즈니스 예외는 `CoreException` + `ErrorType` 조합으로 처리한다.

**흐름**: `throw CoreException(ErrorType.XXX)` → `GlobalExceptionHandler` → `ErrorResponse(code, message)`

**새 도메인 에러 추가 시**:
1. `ErrorType` enum에 에러 추가 (HttpStatus, code, message)
2. 도메인 코드에서 `throw new CoreException(ErrorType.XXX)`
3. `GlobalExceptionHandler`는 수정 불필요 (자동 처리)

- `@Valid` 검증 실패 → `MethodArgumentNotValidException` → BAD_REQUEST 자동 반환

#### 인증 패턴
- 인증 헤더: `X-Loopers-LoginId`, `X-Loopers-LoginPw`
- Controller: `@RequestHeader(required = false)` → null 허용
- Facade에서 null/blank 검증 → 단일 `UNAUTHORIZED` 응답 (보안: 실패 사유 미구분)
- 비밀번호 검증은 도메인 모델에 위임: `User.authenticate(rawPassword)`

### 4.6 도메인 모델 패턴

#### 팩토리 메서드
- `create(...)`: 새 객체 생성 (유효성 검증 포함, id = null)
- `reconstruct(...)`: DB에서 복원 (검증 생략, id 포함)
- 생성자는 `private`으로 제한

#### 유효성 검증 순서
null 체크 → empty 체크 → 길이 제한 → 포맷(정규식) → 비즈니스 규칙

#### Value Object
- Java `record`로 구현 (예: `Password`)
- `create()` + `fromEncoded()` 팩토리 메서드 패턴 동일 적용
- 비즈니스 로직(검증, 변환)을 VO 내부에 캡슐화

#### BaseEntity 제약사항
- `id` 필드는 `final Long id = 0L` + `@GeneratedValue(IDENTITY)` → 엔티티에서 직접 id 설정 불가
- `createdAt`, `updatedAt`은 `@PrePersist`, `@PreUpdate`로 자동 관리
- Soft delete: `deletedAt` 필드, `delete()`/`restore()` 메서드 제공

#### 필드 가변성
- 변경 가능 필드: `private` (non-final) → `changeXxx()` 메서드 제공 (예: `password`)
- 불변 필드: `private final` → 변경 불가 (예: `loginId`, `name`, `birthday`, `email`)

### 4.7 CQRS 레이어 흐름

Controller → Facade(@Transactional) → Service → Repository(interface) → RepositoryImpl → JpaRepository + Entity ↔ Domain

#### 레이어 규칙
- **호출 순서 준수**: Controller → Facade → Service → Repository 순서를 반드시 지켜야 하며, 계층을 건너뛰는 호출(예: Controller → Service 직접 호출)을 금지한다.
- **비즈니스 로직과 서비스 로직 분리**: 비즈니스 로직(도메인 규칙, 검증, 계산)은 Domain Model 또는 Domain Service에서 작성하고, 서비스 로직(유스케이스 오케스트레이션, 트랜잭션 관리, 외부 시스템 연동)은 Facade와 Service에서 작성한다.

| 레이어 | 클래스 | 어노테이션 | 역할 |
|--------|--------|-----------|------|
| Controller | `{Domain}Controller` | `@RestController` | 요청 수신, Facade 호출 |
| Facade | `{Domain}CommandFacade` | `@Component`, `@Transactional` | 명령 유스케이스 오케스트레이션, 트랜잭션 경계 |
| Facade | `{Domain}QueryFacade` | `@Component`, `@Transactional(readOnly = true)` | 조회 유스케이스 오케스트레이션 |
| Service | `{Domain}CommandService` | `@Service`, `@Transactional` | 단일 도메인 비즈니스 로직 |
| Repository(I) | `{Domain}Command/QueryRepository` | (인터페이스) | 명령(save,delete) / 조회(find,exists) 계약 |
| RepositoryImpl | `{Domain}Command/QueryRepositoryImpl` | `@Repository` | Entity ↔ Domain 변환 후 JPA 호출 |
| Entity | `{Domain}Entity` | `@Entity` | `from(Domain)` + `toDomain()` 변환 |

#### Entity 업데이트 패턴
- **신규 생성**: `Entity.from(domain)` → `jpaRepository.save(entity)` → `entity.toDomain()`
- **기존 수정**: `jpaRepository.findById(id)` → `existingEntity.updateXxx(...)` → JPA dirty checking → `entity.toDomain()`
- ⚠️ `Entity.from(domain)`은 항상 새 엔티티(id 없음)를 생성하므로, 업데이트 시 절대 사용 금지

### 4.8 DTO 패턴

모든 DTO는 Java `record`로 구현한다.

| DTO 유형 | 위치 | 변환 메서드 |
|----------|------|-----------|
| Request | `interfaces/controller/request/` | `toCommand()` → Command 반환 |
| Command | `application/dto/command/` | 불변 record, 변환 없음 |
| Response | `interfaces/controller/response/` | `from(Domain)` static 팩토리 |
| Entity | `infrastructure/entity/` | `from(Domain)` + `toDomain()` 양방향 |

- Request에 `@NotBlank`, `@NotNull` 등 Jakarta Validation 적용
- Response에 민감정보(password) 포함 금지

## 5. 주의사항

### Never Do
- 실제 동작하지 않는 코드, 불필요한 Mock 데이터를 이용한 구현 금지
- null-safety 하지 않게 코드 작성 금지 (Java의 경우, Optional 활용)
- println 코드 남기지 말 것

### Recommendation
- 실제 API를 호출해 확인하는 E2E 테스트 코드 작성
- 재사용 가능한 객체 설계
- 성능 최적화에 대한 대안 및 제안
- 개발 완료된 API의 경우, `.http/**.http`에 분류해 작성

### Priority
1. 실제 동작하는 해결책만 고려
2. null-safety, thread-safety 고려
3. 테스트 가능한 구조로 설계
4. 기존 코드 패턴 분석 후 일관성 유지
