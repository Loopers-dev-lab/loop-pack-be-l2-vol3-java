# 축 2: 테스트 품질 체크리스트 (T1~T9)

test-patterns 스킬 기준으로 테스트 코드 품질을 확인합니다.

참고: `.claude/skills/test-patterns/SKILL.md`

---

## T1: @Nested 필수

### 확인 방법
모든 `@Test` 메서드가 `@Nested` 클래스 내부에 위치하는지 확인한다.

### 위반 패턴
```java
class ProductTest {
    @Test
    void 유효한_값이면_생성된다() { ... }  // @Nested 없이 직접 배치
}
```

### 올바른 패턴
```java
class ProductTest {
    @Nested
    class 생성 {
        @Test
        void 유효한_값이면_생성된다() { ... }
    }
}
```

---

## T2: ReplaceUnderscores

### 확인 방법
테스트 클래스(최상위)에 `@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)` 어노테이션이 존재하는지 확인한다.

### 위반 패턴
```java
class ProductTest {  // @DisplayNameGeneration 누락
    @Nested
    class 생성 { ... }
}
```

### 올바른 패턴
```java
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ProductTest {
    @Nested
    class 생성 { ... }
}
```

---

## T3: @DisplayName 미사용

### 확인 방법
테스트 파일에 `@DisplayName` 어노테이션이 없는지 확인한다. (`@DisplayNameGeneration`은 허용)

### 위반 패턴
```java
@Test
@DisplayName("유효한 값이면 생성된다")
void createProduct() { ... }
```

### 올바른 패턴
```java
@Test
void 유효한_값이면_생성된다() { ... }
```

---

## T4: 한글 메서드명

### 확인 방법
`@Test` 메서드명과 `@Nested` 클래스명에 영어가 혼용되지 않는지 확인한다.

### 검출 키워드
영어 단어가 포함된 메서드명/클래스명 (예: `create_시`, `CONFIRMED`, `confirm_호출시`)

### 위반 패턴
```java
@Test
void confirm_호출시_상태가_CONFIRMED로_변경된다() { ... }
```

### 올바른 패턴
```java
@Test
void 확정하면_상태가_확정됨으로_변경된다() { ... }
```

### 예외
- 기술 용어 (API, HTTP, URL 등)는 허용
- 고유명사 (MySQL, Redis 등)는 허용

---

## T5: 단위 테스트 — Mock 미사용

### 대상 파일
`{Domain}Test.java` (단위 테스트만 해당)

### 확인 방법
다음 키워드가 없는지 확인한다:
- `@Mock`, `@InjectMocks`, `@MockBean`
- `Mockito.`, `mock(`, `when(`, `verify(`
- `import org.mockito`

### 위반 패턴
```java
class ProductTest {
    @Mock
    private ProductRepository productRepository;
}
```

### 올바른 패턴
```java
class ProductTest {
    // Mock 없이 순수 객체 테스트
    // 외부 의존성은 Fake 구현체 사용
}
```

---

## T6: 단위 테스트 — @SpringBootTest 미사용

### 대상 파일
`{Domain}Test.java` (단위 테스트만 해당)

### 확인 방법
`@SpringBootTest` 어노테이션이 없는지 확인한다.

### 위반 패턴
```java
@SpringBootTest
class ProductTest { ... }
```

### 올바른 패턴
```java
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ProductTest { ... }
```

---

## T7: 통합 테스트 — DatabaseCleanUp

### 대상 파일
`{Domain}ServiceIntegrationTest.java` (통합 테스트만 해당)

### 확인 방법
1. `DatabaseCleanUp` 필드가 `@Autowired`로 주입되는지 확인한다
2. `@AfterEach` 메서드에서 `databaseCleanUp.truncateAllTables()` 호출이 있는지 확인한다

### 위반 패턴
```java
@SpringBootTest
class ProductServiceIntegrationTest {
    // DatabaseCleanUp 없음 → 테스트 간 데이터 오염 위험
}
```

### 올바른 패턴
```java
@SpringBootTest
class ProductServiceIntegrationTest {

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }
}
```

---

## T8: E2E 테스트 — RANDOM_PORT

### 대상 파일
`{Domain}ApiE2ETest.java` (E2E 테스트만 해당)

### 확인 방법
`@SpringBootTest` 어노테이션에 `webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT`가 설정되어 있는지 확인한다.

### 위반 패턴
```java
@SpringBootTest  // webEnvironment 누락
class ProductAdminApiE2ETest { ... }
```

### 올바른 패턴
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductAdminApiE2ETest { ... }
```

---

## T9: E2E 테스트 — HTTP 상태코드 검증

### 대상 파일
`{Domain}ApiE2ETest.java` (E2E 테스트만 해당)

### 확인 방법
각 `@Test` 메서드에서 `response.getStatusCode()` 또는 이에 준하는 HTTP 상태코드 assertion이 있는지 확인한다.

### 위반 패턴
```java
@Test
void 유효한_정보로_등록하면_상품정보가_반환된다() {
    ResponseEntity<...> response = ...;
    // 상태코드 검증 없이 바디만 확인
    assertThat(response.getBody().data().name()).isEqualTo("상품A");
}
```

### 올바른 패턴
```java
@Test
void 유효한_정보로_등록하면_상품정보가_반환된다() {
    ResponseEntity<...> response = ...;
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().data().name()).isEqualTo("상품A");
}
```
