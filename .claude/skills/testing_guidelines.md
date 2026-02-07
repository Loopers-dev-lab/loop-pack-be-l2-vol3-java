# 테스트 가이드라인

## 테스트 전략

### 테스트 피라미드
```
        /\
       /E2E\         (적음)
      /------\
     /통합테스트\      (중간)
    /----------\
   /  단위테스트  \    (많음)
  /--------------\
```

### 테스트 원칙
1. **독립성**: 각 테스트는 독립적으로 실행 가능해야 함
2. **반복성**: 동일한 입력에 대해 항상 동일한 결과
3. **자동화**: 수동 개입 없이 자동 실행
4. **빠른 피드백**: 테스트는 빠르게 실행되어야 함
5. **명확성**: 테스트 이름과 구조로 의도 파악 가능
6. **격리성**: 테스트 간 상태 공유 금지

### 3A 패턴 (Arrange-Act-Assert)
모든 테스트는 3A 원칙을 따릅니다:
- **Arrange**: 테스트 데이터 및 환경 준비
- **Act**: 테스트 대상 실행
- **Assert**: 결과 검증

## 단위 테스트 (Unit Test)

### 대상
- **Value Object**: 유효성 검증 로직
- **도메인 로직**: 비즈니스 규칙
- **유틸리티 메서드**: 순수 함수

### 특징
- 외부 의존성 없음 (DB, 네트워크 등)
- 빠른 실행 속도
- 격리된 환경

### Value Object 테스트 예시
```java
@DisplayName("회원 모델을 생성할 때, ")
@Nested
class Create {
    @DisplayName("ID 가 영문 및 숫자 10자 이내 형식에 맞지 않으면, User 객체 생성에 실패한다.")
    @Test
    void createsMemberModel_whenIdIsInvalid() {
        // arrange
        String memberId = "invalid_id!"; // 특수 문자 포함

        // act
        CoreException result = assertThrows(CoreException.class, () ->
                new MemberModel(memberId, "password123"));

        // assert
        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);

        // second arrange
        String secondMemberId = "validID12345"; // 11자

        // second act
        CoreException secondResult = assertThrows(CoreException.class, () ->
                new MemberModel(secondMemberId, "password123"));

        // second assert
        assertThat(secondResult.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("이메일 형식이 xx@yy.zz 형식에 맞지 않으면, User 객체 생성에 실패한다.")
    @Test
    void createsMemberModel_whenEmailIsInvalid() {
        // arrange
        String invalidEmail = "invalid_email";

        // act
        CoreException result = assertThrows(CoreException.class, () ->
                new MemberModel("validID1", "password123", invalidEmail));

        // assert
        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }
}
```

### 테스트 구조
- `@Nested`: 관련 테스트 그룹화
- `@DisplayName`: 한글로 명확한 의도 표현
- `assertThrows`: 예외 검증
- `assertThat`: AssertJ 유창한 assertion

## 통합 테스트 (Integration Test)

### 대상
- **Service**: 비즈니스 로직 + Repository 연동
- **Repository**: JPA 쿼리 동작 확인
- **외부 시스템 연동**: Redis, Kafka 등

### 특징
- `@SpringBootTest`: Spring Context 로드
- **TestContainers**: 실제 DB 환경
- **DatabaseCleanUp**: 테스트 간 데이터 격리
- **Spy 검증**: 메서드 호출 확인

### Service 통합 테스트 예시
```java
@SpringBootTest
class MemberServiceIntegrationTest {
    @Autowired
    private MemberService memberService;

    @Autowired
    private MemberJpaRepository memberJpaRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private MemberRepository spyMemberRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        Mockito.reset(spyMemberRepository);
    }

    @DisplayName("회원 가입 시,")
    @Nested
    class Post {
        private static final String VALID_MEMBER_ID = "testuser1";
        private static final String VALID_PASSWORD = "Test1234!";
        private static final String VALID_EMAIL = "test@example.com";
        private static final String VALID_BIRTH_DATE = "1995-05-20";
        private static final String VALID_NAME = "테스트유저";
        private static final Gender VALID_GENDER = Gender.MALE;

        @DisplayName("유저 저장이 정상적으로 이루어진다.")
        @Test
        void testUserSave() {
            // arrange & act
            MemberModel savedMember = memberService.register(
                VALID_MEMBER_ID, VALID_PASSWORD, VALID_EMAIL, 
                VALID_BIRTH_DATE, VALID_NAME, VALID_GENDER
            );

            // assert - spy 객체를 통해 save 메서드 호출 검증
            verify(spyMemberRepository, times(1)).save(any(MemberModel.class));

            // assert - 저장된 회원 정보 검증
            assertAll(
                () -> assertThat(savedMember).isNotNull(),
                () -> assertThat(savedMember.getId()).isNotNull(),
                () -> assertThat(savedMember.getMemberId().value()).isEqualTo(VALID_MEMBER_ID),
                () -> assertThat(savedMember.getEmail().address()).isEqualTo(VALID_EMAIL),
                () -> assertThat(savedMember.getBirthDate().asString()).isEqualTo(VALID_BIRTH_DATE),
                () -> assertThat(savedMember.getName().value()).isEqualTo(VALID_NAME),
                () -> assertThat(savedMember.getGender()).isEqualTo(VALID_GENDER),
                // 비밀번호가 암호화되어 저장되었는지 검증
                () -> assertThat(savedMember.getPassword()).isNotEqualTo(VALID_PASSWORD),
                () -> assertThat(passwordHasher.matches(VALID_PASSWORD, savedMember.getPassword())).isTrue()
            );

            // DB에서 직접 조회하여 검증
            MemberModel foundMember = memberJpaRepository.findById(savedMember.getId()).orElseThrow();
            assertAll(
                () -> assertThat(foundMember.getMemberId().value()).isEqualTo(VALID_MEMBER_ID),
                () -> assertThat(foundMember.getEmail().address()).isEqualTo(VALID_EMAIL),
                () -> assertThat(passwordHasher.matches(VALID_PASSWORD, foundMember.getPassword())).isTrue()
            );
        }

        @DisplayName("이미 가입된 ID 로 회원가입 시도 시, 실패한다.")
        @Test
        void testDuplicateMemberId() {
            // arrange
            memberService.register(
                VALID_MEMBER_ID, VALID_PASSWORD, VALID_EMAIL, 
                VALID_BIRTH_DATE, VALID_NAME, VALID_GENDER
            );

            // act
            CoreException exception = assertThrows(CoreException.class,
                () -> memberService.register(
                    VALID_MEMBER_ID, VALID_PASSWORD, VALID_EMAIL, 
                    VALID_BIRTH_DATE, VALID_NAME, VALID_GENDER
                ));
            
            // assert
            assertAll(
                () -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(exception.getMessage()).isEqualTo("이미 가입된 ID 입니다.")
            );
        }
    }
}
```

### Spy 설정 (TestConfiguration)
```java
@TestConfiguration
static class SpyConfig {
    @Bean
    @Primary
    public MemberRepository spyMemberRepository(MemberJpaRepository memberJpaRepository) {
        return Mockito.spy(new MemberRepository() {
            @Override
            public MemberModel save(MemberModel member) {
                return memberJpaRepository.save(member);
            }

            @Override
            public boolean existsByMemberId(MemberId memberId) {
                return memberJpaRepository.existsByMemberId(memberId);
            }

            @Override
            public Optional<MemberModel> findByMemberId(MemberId memberId) {
                return memberJpaRepository.findByMemberId(memberId);
            }
        });
    }
}
```

### DatabaseCleanUp 사용
```java
@Component
public class DatabaseCleanUp {
    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void truncateAllTables() {
        entityManager.flush();
        entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 0").executeUpdate();
        
        List<String> tableNames = entityManager.createNativeQuery(
            "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE()"
        ).getResultList();
        
        for (String tableName : tableNames) {
            entityManager.createNativeQuery("TRUNCATE TABLE " + tableName).executeUpdate();
        }
        
        entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1").executeUpdate();
    }
}
```

## E2E 테스트 (End-to-End Test)

### 대상
- **REST API**: 전체 요청-응답 흐름
- **실제 시나리오**: 사용자 관점 테스트

### 특징
- `@SpringBootTest(webEnvironment = RANDOM_PORT)`: 실제 서버 구동
- **TestRestTemplate**: HTTP 요청 전송
- **ParameterizedTypeReference**: 제네릭 타입 응답 처리
- 전체 레이어 통합 검증

### E2E 테스트 예시
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class MemberV1ApiE2ETest {

    private static final String ENDPOINT_REGISTER = "/api/v1/members/register";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api/v1/members/register")
    @Nested
    class Register {
        @DisplayName("회원 가입이 성공할 경우, 생성된 유저 정보를 응답으로 반환한다.")
        @Test
        void successfulRegistration_returnsCreatedUserInfo() {
            // arrange
            MemberV1Dto.RegisterRequest request = new MemberV1Dto.RegisterRequest(
                "testuser1",
                "Test1234!",
                "test@example.com",
                "1995-05-20",
                "테스트유저",
                Gender.MALE
            );

            // act
            ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>> responseType = 
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<MemberV1Dto.MemberResponse>> response =
                testRestTemplate.exchange(
                    ENDPOINT_REGISTER, 
                    HttpMethod.POST, 
                    new HttpEntity<>(request), 
                    responseType
                );

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat(response.getBody().data()).isNotNull(),
                () -> assertThat(response.getBody().data().id()).isNotNull(),
                () -> assertThat(response.getBody().data().memberId()).isEqualTo("testuser1"),
                () -> assertThat(response.getBody().data().email()).isEqualTo("test@example.com"),
                () -> assertThat(response.getBody().data().birthDate()).isEqualTo("1995-05-20"),
                () -> assertThat(response.getBody().data().name()).isEqualTo("테스트유저"),
                () -> assertThat(response.getBody().data().gender()).isEqualTo(Gender.MALE)
            );
        }

        @DisplayName("회원 가입 시에 성별이 없을 경우, 400 Bad Request 응답을 반환한다.")
        @Test
        void missingGender_returnsBadRequest() {
            // arrange
            MemberV1Dto.RegisterRequest request = new MemberV1Dto.RegisterRequest(
                "testuser2",
                "Test1234!",
                "test2@example.com",
                "1995-05-20",
                "테스트유저2",
                null  // gender가 null
            );

            // act
            ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>> responseType = 
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<MemberV1Dto.MemberResponse>> response =
                testRestTemplate.exchange(
                    ENDPOINT_REGISTER, 
                    HttpMethod.POST, 
                    new HttpEntity<>(request), 
                    responseType
                );

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError(),
                    "Expected 4xx status but got: " + response.getStatusCode()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)
            );
        }
    }
}
```

## 테스트 데이터 생성

### 상수 활용
```java
private static final String VALID_MEMBER_ID = "testuser1";
private static final String VALID_PASSWORD = "Test1234!";
private static final String VALID_EMAIL = "test@example.com";
```

### Instancio 활용 (복잡한 객체)
```java
@Test
void testWithInstancio() {
    // arrange
    MemberModel member = Instancio.of(MemberModel.class)
        .set(field(MemberModel::getMemberId), new MemberId("testuser1"))
        .set(field(MemberModel::getEmail), new Email("test@example.com"))
        .create();
    
    // act & assert
}
```

## 테스트 어노테이션

### 클래스 레벨
- `@SpringBootTest`: 전체 Spring Context 로드 (통합/E2E)
- `@WebMvcTest`: Controller 레이어만 테스트
- `@DataJpaTest`: JPA Repository 테스트
- `@TestConfiguration`: 테스트용 Bean 설정

### 메서드 레벨
- `@Test`: 일반 테스트
- `@ParameterizedTest`: 파라미터화된 테스트
- `@RepeatedTest`: 반복 테스트
- `@BeforeEach`: 각 테스트 전 실행
- `@AfterEach`: 각 테스트 후 실행
- `@DisplayName`: 테스트 설명 (한글 권장)
- `@Nested`: 테스트 그룹화

### 조건부 실행
- `@EnabledIf`: 조건부 활성화
- `@DisabledIf`: 조건부 비활성화
- `@EnabledOnOs`: 특정 OS에서만 실행

## Assertion 라이브러리

### AssertJ (권장)
```java
// 단일 검증
assertThat(member.getMemberId().value()).isEqualTo("testuser1");
assertThat(member.getEmail().address()).isEqualTo("test@example.com");

// 다중 검증
assertAll(
    () -> assertThat(member).isNotNull(),
    () -> assertThat(member.getId()).isNotNull(),
    () -> assertThat(member.getMemberId().value()).isEqualTo("testuser1")
);

// 예외 검증
CoreException exception = assertThrows(CoreException.class, 
    () -> new MemberId("invalid!"));
assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);

// 컬렉션 검증
assertThat(members)
    .hasSize(3)
    .extracting(MemberModel::getMemberId)
    .containsExactly(memberId1, memberId2, memberId3);
```

### JUnit Assertions
```java
// 기본 검증
assertEquals(expected, actual);
assertNotNull(object);
assertTrue(condition);

// 예외 검증
assertThrows(CoreException.class, () -> {
    // 예외 발생 코드
});

// 다중 검증
assertAll(
    () -> assertEquals(expected1, actual1),
    () -> assertEquals(expected2, actual2)
);
```

## Mock 사용

### Mockito
```java
@ExtendWith(MockitoExtension.class)
class ServiceTest {
    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private MemberService memberService;

    @Test
    void testWithMock() {
        // arrange
        MemberId memberId = new MemberId("testuser1");
        when(memberRepository.existsByMemberId(memberId)).thenReturn(false);

        // act
        memberService.register(/* ... */);

        // assert
        verify(memberRepository, times(1)).save(any(MemberModel.class));
    }
}
```

### Spy (실제 객체 + 부분 모킹)
```java
@Autowired
private MemberRepository spyMemberRepository;

@Test
void testWithSpy() {
    // act
    memberService.register(/* ... */);

    // assert - 실제 동작 + 호출 검증
    verify(spyMemberRepository, times(1)).save(any(MemberModel.class));
}
```

## 테스트 격리

### @AfterEach로 데이터 정리
```java
@AfterEach
void tearDown() {
    databaseCleanUp.truncateAllTables();
    Mockito.reset(spyMemberRepository);
}
```

### @DirtiesContext (비권장 - 느림)
```java
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
```

## 테스트 실행

### Gradle 명령어
```bash
# 전체 테스트
./gradlew test

# 특정 모듈 테스트
./gradlew :apps:commerce-api:test

# 특정 테스트 클래스
./gradlew test --tests MemberServiceIntegrationTest

# 특정 테스트 메서드
./gradlew test --tests MemberServiceIntegrationTest.testUserSave

# 병렬 실행 비활성화 (기본 설정)
maxParallelForks = 1
```

### 커버리지 리포트
```bash
./gradlew test jacocoTestReport
```

## 테스트 네이밍

### 메서드 네이밍 패턴
1. **Given-When-Then**: `givenValidMemberId_whenRegister_thenSuccess`
2. **Should-When**: `shouldThrowException_whenInvalidEmail`
3. **한글 @DisplayName**: "회원 가입이 성공할 경우, 생성된 유저 정보를 응답으로 반환한다."

### 권장: @DisplayName + 간단한 메서드명
```java
@DisplayName("회원 가입이 성공할 경우, 생성된 유저 정보를 응답으로 반환한다.")
@Test
void successfulRegistration_returnsCreatedUserInfo() {
    // ...
}
```

## 테스트 작성 체크리스트

### 단위 테스트
- [ ] 3A 패턴 준수
- [ ] 외부 의존성 없음
- [ ] 빠른 실행 (< 100ms)
- [ ] 명확한 @DisplayName
- [ ] 경계값 테스트 포함

### 통합 테스트
- [ ] @SpringBootTest 사용
- [ ] TestContainers 설정
- [ ] DatabaseCleanUp 적용
- [ ] Spy 검증 (필요 시)
- [ ] 실제 DB 동작 확인

### E2E 테스트
- [ ] RANDOM_PORT 설정
- [ ] TestRestTemplate 사용
- [ ] 전체 시나리오 검증
- [ ] HTTP 상태 코드 확인
- [ ] 응답 데이터 검증

## 금지 사항

### ❌ Never Do
1. **테스트 간 의존성**: 테스트 실행 순서에 의존 금지
2. **Thread.sleep()**: 시간 기반 대기 금지
3. **하드코딩된 포트**: RANDOM_PORT 사용
4. **프로덕션 DB 사용**: TestContainers 사용
5. **테스트 무시**: `@Disabled` 남발 금지
6. **과도한 Mock**: 실제 동작 검증 우선

### ✅ Best Practices
1. **실패하는 테스트 먼저 작성** (TDD)
2. **한 테스트는 한 가지만 검증**
3. **테스트 이름으로 의도 표현**
4. **Given-When-Then 명확히 구분**
5. **assertAll로 다중 검증**
6. **테스트 데이터는 상수로 관리**
