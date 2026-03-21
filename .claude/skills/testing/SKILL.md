---
name: testing
description: 테스트 전략 (단위/통합/E2E), 3A 패턴, TestContainers, AssertJ, Mockito. 테스트 작성 및 검증 시 참조
user-invocable: true
allowed-tools: Read, Grep, Bash
---

# 테스트 가이드라인

> 경로 prefix: `apps/commerce-api/src/test/java/com/loopers/`

---

## 테스트 피라미드

```
        /\
       /E2E\         (적음 - 전체 흐름)
      /------\
     /통합테스트\      (중간 - Service + DB)
    /----------\
   /  단위테스트  \    (많음 - 빠름, Model/VO)
  /--------------\
```

---

## 3A 패턴

**`// given / // when / // then`** — 모든 테스트(단위/통합/E2E)에서 통일

---

## @Nested 구조

```
@DisplayName("{레이어 또는 Entity}")
class XxxTest {
    @DisplayName("{행위}를 할 때,")
    @Nested
    class ContextGroup {
        @Test
        @DisplayName("{조건}이면 {결과}")
        void test_method_name() { ... }
    }
}
```

**네이밍 컨벤션**
- 클래스 DisplayName: `"{Entity} 엔티티"` 또는 `"{Domain} {레이어}"`
- @Nested 클래스명: 영어 명사/동사 (`Create`, `Delete`, `DecreaseStock`)
- @Nested DisplayName: 한글 동사구 + 쉼표 (`"재고를 차감할 때,"`)
- 메서드명: snake_case (`decreaseStock_success`)
- 메서드 DisplayName: 한글 명사구 (`"재고가 충분하면 차감 성공"`)

---

## 단위 테스트 (Unit Test)

**대상**: Value Object, Model 도메인 로직 (외부 의존성 없음)

레퍼런스:
- `domain/member/MemberModelUnitTest.java`
- `domain/brand/BrandModelTest.java`
- `domain/order/OrderModelTest.java`

---

## 통합 테스트 (Integration Test)

**대상**: Service + Repository (실제 DB 환경)

**설정**
- `@SpringBootTest`
- TestContainers (실제 MySQL 환경)
- `@AfterEach` 에서 `databaseCleanUp.truncateAllTables()` + `Mockito.reset(spy)`

**Spy 설정 패턴**
- `@TestConfiguration`에서 `Mockito.spy(new XxxRepositoryImpl(...))` 등록
- `@Bean @Primary` 로 실제 Bean 교체
- `verify(spyRepo, times(1)).save(any(...))` 로 호출 검증

레퍼런스:
- `domain/member/MemberServiceIntegrationTest.java`
- `domain/product/ProductServiceIntegrationTest.java`
- `domain/brand/BrandServiceIntegrationTest.java`

---

## E2E 테스트 (End-to-End Test)

**대상**: Controller → 전체 레이어 (실제 HTTP 요청/응답)

**설정**
- `@SpringBootTest(webEnvironment = RANDOM_PORT)`
- `TestRestTemplate` 으로 HTTP 요청
- `ParameterizedTypeReference<ApiResponse<XxxDto>>` 로 제네릭 응답 처리

레퍼런스:
- `interfaces/api/product/ProductV1ControllerE2ETest.java`
- `interfaces/api/order/OrderV1ControllerE2ETest.java`
- `interfaces/api/brand/BrandV1ControllerE2ETest.java`

---

## AssertJ 핵심 패턴

```java
// 단일 검증
assertThat(result).isNotNull();
assertThat(result.getValue()).isEqualTo("expected");

// 다중 검증 (하나 실패해도 나머지 실행)
assertAll(
    () -> assertThat(result).isNotNull(),
    () -> assertThat(result.getId()).isNotNull()
);

// 예외 검증
assertThatThrownBy(() -> new MemberId("invalid!"))
        .isInstanceOf(CoreException.class);

// 컬렉션 검증
assertThat(list).hasSize(3).extracting(Model::getId).containsExactly(...);
```

---

## Gradle 테스트 명령어

```bash
./gradlew test
./gradlew :apps:commerce-api:test
./gradlew test --tests MemberServiceIntegrationTest
```

---

## 테스트 작성 체크리스트

### 단위 테스트
- [ ] 외부 의존성 없음
- [ ] `// given / when / then` 주석
- [ ] 경계값 포함 (null, 빈값, 최대/최소)
- [ ] 명확한 `@DisplayName`

### 통합 테스트
- [ ] `@SpringBootTest`
- [ ] TestContainers 설정
- [ ] `@AfterEach` 데이터 정리
- [ ] Spy 검증 (필요 시)

### E2E 테스트
- [ ] `RANDOM_PORT` 설정
- [ ] HTTP 상태 코드 검증
- [ ] 응답 데이터 검증

---

## 핵심 규칙

```
❌ 테스트 간 실행 순서 의존
❌ Thread.sleep() 사용
❌ 프로덕션 DB 사용  →  TestContainers
❌ @Disabled 남발
❌ 과도한 Mock  →  실제 동작 검증 우선
❌ assertion 약화 (isNotNull 만으로 검증 종료)
```
