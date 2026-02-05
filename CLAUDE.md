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

## 2. 모듈 구조

```
apps/
├── commerce-api      # REST API 서버 (Spring Web + JPA + Redis + Kafka)
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
