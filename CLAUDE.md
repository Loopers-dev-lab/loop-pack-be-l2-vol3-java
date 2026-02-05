# CLAUDE.md

이 문서는 Claude Code (claude.ai/code)가 이 저장소의 코드를 다룰 때 참고하는 가이드입니다.

## 기술 스택 및 버전

| 분류 | 기술 | 버전 |
|------|------|------|
| **언어** | Java | 21 |
| **프레임워크** | Spring Boot | 3.4.4 |
| **의존성 관리** | Spring Dependency Management | 1.1.7 |
| **클라우드** | Spring Cloud | 2024.0.1 |
| **ORM** | Spring Data JPA + QueryDSL | (managed) |
| **데이터베이스** | MySQL | 8.x |
| **캐시** | Redis (Spring Data Redis) | (managed) |
| **메시징** | Apache Kafka (Spring Kafka) | (managed) |
| **API 문서** | SpringDoc OpenAPI | 2.7.0 |
| **빌드 도구** | Gradle (Kotlin DSL) | 8.x |
| **테스트** | JUnit 5, Mockito 5.14.0, SpringMockK 4.0.2, Instancio 5.0.2 |
| **테스트 인프라** | TestContainers | (managed) |

## 빌드 및 테스트 명령어

```bash
# 빌드
./gradlew build                    # 테스트 포함 전체 빌드
./gradlew build -x test            # 테스트 제외 빌드

# 테스트
./gradlew test                     # 전체 테스트 실행
./gradlew test --tests "ClassName" # 특정 테스트 클래스 실행
./gradlew :apps:commerce-api:test  # 특정 모듈 테스트 실행

# 애플리케이션 실행
./gradlew :apps:commerce-api:bootRun
./gradlew :apps:commerce-batch:bootRun --args='--spring.batch.job.name=demoJob --requestDate=2024-01-01'
./gradlew :apps:commerce-streamer:bootRun

# 인프라 (로컬 개발 시 필요)
docker-compose -f ./docker/infra-compose.yml up      # MySQL, Redis, Kafka
docker-compose -f ./docker/monitoring-compose.yml up # Prometheus, Grafana (localhost:3000, admin/admin)
```

## 아키텍처

멀티 모듈 Spring Boot 프로젝트 (Java 21, Spring Boot 3.4.4):

```
apps/                    # 실행 가능한 Spring Boot 애플리케이션
├── commerce-api         # REST API (포트 8080)
├── commerce-batch       # Spring Batch 작업
└── commerce-streamer    # Kafka 컨슈머 서비스

modules/                 # 재사용 가능한 설정 모듈
├── jpa                  # JPA/Hibernate + QueryDSL + HikariCP
├── redis                # Redis Master-Replica 설정
└── kafka                # Kafka producer/consumer

supports/                # 부가 유틸리티
├── jackson              # JSON 직렬화 설정
├── logging              # Logback + Slack 알림
└── monitoring           # Prometheus + Micrometer
```

## 패키지 구조

- `com.loopers.config.*` - 설정 클래스
- `com.loopers.domain.*` - 도메인 엔티티 (`BaseEntity` 사용하여 감사 기능 적용)
- `com.loopers.infrastructure.*` - JPA 리포지토리 (엔티티 스캔 활성화)
- `com.loopers.interfaces.*` - 컨트롤러, Kafka 컨슈머
- `com.loopers.batch.*` - 배치 작업 설정

## 테스트

- **스택**: JUnit 5, AssertJ, Mockito, SpringMockK, Instancio, TestContainers
- **테스트 픽스처**: `src/testFixtures/java/`에 재사용 가능한 설정 (MySqlTestContainersConfig, RedisTestContainersConfig, DatabaseCleanUp, RedisCleanUp)
- **프로파일**: 테스트는 자동으로 `test` 프로파일로 실행

## 주요 패턴

- **BaseEntity**: `createdAt`, `updatedAt`, `deletedAt` (소프트 삭제) 포함 추상 기본 클래스
- **QueryDSL**: 어노테이션 프로세싱으로 Q-클래스 생성
- **배치 작업**: 파라미터에 `@JobScope`/`@StepScope` 사용, 활성화에 `@ConditionalOnProperty` 사용
- **Kafka 컨슈머**: 수동 확인 모드(`AckMode.MANUAL`)의 배치 리스너
- **타임존**: 모든 앱에서 `@PostConstruct`로 `Asia/Seoul` 설정

## 개발 규칙 및 컨벤션

### 레이어별 책임

| 레이어 | 책임 | 예시 |
|--------|------|------|
| **Controller** | HTTP 요청/응답 매핑, 입력 형식 검증 | `@NotBlank`, `@Email`, `@Pattern` |
| **Service** | 비즈니스 로직, 트랜잭션 관리 | 중복 검사, 권한 검증, 암호화 |
| **Domain** | 도메인 불변식 보호, 상태 변경 | 엔티티 메서드 |
| **Validator** | 복잡한 비즈니스 규칙 검증 | 비밀번호 규칙 (8~16자, 특수문자 등) |

### 엔티티 작성 규칙

```java
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {
    // 필드는 private, @Column으로 제약조건 명시
    @Column(nullable = false, length = 10, unique = true)
    private String loginId;

    // 생성자에 @Builder 적용
    @Builder
    public User(String loginId, ...) { }

    // 상태 변경은 메서드로 캡슐화
    public void changePassword(String newPassword) {
        this.password = newPassword;
    }
}
```

### DTO 작성 규칙

```java
// record 사용, Bean Validation 적용
public record CreateUserRequest(
    @NotBlank
    @Pattern(regexp = "^[a-zA-Z0-9]+$")
    String loginId,

    @NotBlank
    String password,

    @Email @NotBlank
    String email
) {}

// 응답 DTO는 정적 팩토리 메서드 사용
public record CreateUserResponse(Long id, String loginId) {
    public static CreateUserResponse from(User user) {
        return new CreateUserResponse(user.getId(), user.getLoginId());
    }
}
```

### 예외 처리

```java
// 커스텀 예외 클래스 생성
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException() {
        super("사용자를 찾을 수 없습니다.");
    }
}

// GlobalExceptionHandler에서 HTTP 상태 매핑
@ExceptionHandler(UserNotFoundException.class)
public ResponseEntity<Map<String, String>> handle(UserNotFoundException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of("message", e.getMessage()));
}
```

### 테스트 작성 규칙

```java
// 한글 메서드명 사용
@Test
void 올바른_비밀번호로_변경에_성공한다() {
    // given - 테스트 준비

    // when - 실행

    // then - 검증
}

// @ParameterizedTest로 여러 케이스 커버
@ParameterizedTest(name = "{1} 누락 시 400 반환")
@MethodSource("필수값_누락_케이스")
void 필수값_누락_시_400_반환(Request request, String fieldName) { }
```

### 네이밍 컨벤션

| 구분 | 규칙 | 예시 |
|------|------|------|
| **패키지** | 소문자, 단수형 | `user`, `order` |
| **클래스** | PascalCase | `UserService`, `CreateUserRequest` |
| **메서드** | camelCase, 동사로 시작 | `createUser()`, `changePassword()` |
| **테스트 메서드** | 한글, 행위_결과 형식 | `비밀번호_변경에_성공한다()` |
| **상수** | UPPER_SNAKE_CASE | `LOGIN_ID_HEADER` |

### API 설계 규칙

| HTTP 메서드 | 용도 | 성공 응답 |
|-------------|------|----------|
| `POST` | 리소스 생성 | 201 Created |
| `GET` | 리소스 조회 | 200 OK |
| `PATCH` | 리소스 부분 수정 | 200 OK |
| `DELETE` | 리소스 삭제 | 204 No Content |

### 헤더 기반 인증

```java
public static final String LOGIN_ID_HEADER = "X-Loopers-LoginId";
public static final String LOGIN_PW_HEADER = "X-Loopers-LoginPw";
```

## 코드 리뷰 기준 (.coderabbit.yaml 참조)

- Null 안전성 최우선; Kotlin에서 `!!` 사용 지양
- JPA 엔티티: data class 사용 금지, identity 기반 equals/hashCode 사용
- N+1 문제 주의; fetch join/EntityGraph 적절히 사용
- 트랜잭션 경계: `@Transactional`의 scope, propagation, readOnly 확인
- 컨트롤러: 유효성 검사만 담당, 비즈니스 로직은 서비스에 위임
- 요청/응답 DTO와 엔티티 분리
