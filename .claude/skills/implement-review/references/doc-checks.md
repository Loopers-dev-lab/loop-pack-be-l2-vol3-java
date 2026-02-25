# 축 1: 문서 정합성 체크리스트 (D1~D8)

spec/design 문서와 구현 코드가 일치하는지 확인합니다.

---

## D1: AC-테스트 1:1 매핑

### 비교 방법
1. spec의 AC 목록을 추출한다
2. 테스트 코드에서 `@Test` 메서드 목록을 추출한다
3. 각 AC에 대응하는 테스트가 있는지 확인한다

### 위반 패턴
- AC가 있으나 대응하는 테스트가 없음
- 테스트가 있으나 대응하는 AC가 없음 (과잉 테스트는 위반 아님, 참고 사항으로 보고)

### 올바른 패턴
```
AC: "이름은 1~50자"           → ProductTest#이름이_51자_이상이면_예외()
AC: "이름 중복이면 409 Conflict" → ProductServiceIntegrationTest#이름이_중복이면_예외()
AC: "유효한 정보로 등록하면 200"  → ProductAdminApiE2ETest#유효한_정보로_등록하면_상품정보가_반환된다()
```

---

## D2: API 경로 일치

### 비교 방법
1. spec의 `엔드포인트` 섹션에서 HTTP 메서드 + 경로를 추출한다
2. Controller의 `@RequestMapping`, `@PostMapping`, `@GetMapping` 등과 비교한다

### 위반 패턴
```java
// spec: POST /admin/v1/products
// 실제 구현:
@PostMapping("/api/v1/products")  // 경로 불일치
```

### 올바른 패턴
```java
// spec: POST /admin/v1/products
@PostMapping("/admin/v1/products")  // 일치
```

---

## D3: 인증 유형 일치

### 비교 방법
1. spec의 `인증` 필드를 확인한다 (불필요 / User / Admin)
2. Controller 또는 ApiSpec의 인증 관련 어노테이션/파라미터를 확인한다
   - Admin: `@RequestHeader("X-Loopers-Ldap")` 또는 관리자 인증 처리
   - User: `@RequestHeader("X-Loopers-LoginId")` + `@RequestHeader("X-Loopers-LoginPw")`
   - 불필요: 인증 관련 어노테이션/파라미터 없음

### 위반 패턴
```java
// spec: 인증 Admin
// 실제: 인증 파라미터 없음
@PostMapping("/admin/v1/products")
public ResponseEntity<...> register(@RequestBody ...) { ... }
```

### 올바른 패턴
```java
// spec: 인증 Admin
@PostMapping("/admin/v1/products")
public ResponseEntity<...> register(
    @RequestHeader("X-Loopers-Ldap") String ldap,
    @RequestBody ...
) { ... }
```

---

## D4: 요청 필드 일치

### 비교 방법
1. spec의 `요청` 테이블에서 필드명, 타입, 필수 여부, 제약을 추출한다
2. Request DTO의 필드와 비교한다
   - 필드명 일치 (camelCase 변환 고려)
   - 타입 일치 (String, Long, Integer, BigDecimal 등)
   - 필수 여부 → `@NotNull`, `@NotBlank` 존재 여부
   - 제약 → `@Size`, `@Positive`, `@PositiveOrZero` 등 존재 여부

### 위반 패턴
```java
// spec: name - String - 필수 - 1~50자
public record RegisterRequest(
    String name  // @NotBlank, @Size 누락
) {}
```

### 올바른 패턴
```java
// spec: name - String - 필수 - 1~50자
public record RegisterRequest(
    @NotBlank @Size(min = 1, max = 50) String name
) {}
```

---

## D5: 응답 필드 일치

### 비교 방법
1. spec의 `응답` 테이블에서 필드명과 타입을 추출한다
2. Response DTO의 필드와 비교한다
   - 필드명 일치
   - 타입 일치
   - 누락/추가 필드 확인

### 위반 패턴
```java
// spec 응답: id, name, description, price
public record ProductResponse(
    Long id,
    String name,
    String description
    // price 누락
) {}
```

### 올바른 패턴
```java
// spec 응답: id, name, description, price
public record ProductResponse(
    Long id,
    String name,
    String description,
    BigDecimal price
) {}
```

---

## D6: 에러 메시지 일치

### 비교 방법
1. spec AC에서 에러 시나리오의 메시지를 추출한다
   - 형식: "xxx이면 NNN {에러 메시지}" 또는 AC 본문의 에러 메시지
2. Entity/Service의 `CoreException` 생성 시 전달되는 메시지 문자열과 비교한다

### 위반 패턴
```java
// spec AC: "이름이 중복이면 409 Conflict, '이미 존재하는 브랜드 이름입니다'"
throw new CoreException(ErrorType.CONFLICT, "브랜드 이름 중복");  // 메시지 불일치
```

### 올바른 패턴
```java
// spec AC: "이름이 중복이면 409 Conflict, '이미 존재하는 브랜드 이름입니다'"
throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 브랜드 이름입니다");  // 일치
```

---

## D7: ERD 스키마 일치 (design 있을 때만)

### 비교 방법
1. `docs/design/{epic}/erd.md`의 Mermaid ERD에서 테이블/컬럼을 추출한다
2. Entity 클래스의 `@Column`, `@Table` 등과 비교한다
   - 테이블명 일치
   - 컬럼명 일치 (snake_case ↔ camelCase 변환 고려)
   - 컬럼 타입 일치
   - nullable/not null 일치

### 위반 패턴
```java
// ERD: description VARCHAR(500) nullable
@Column(nullable = false)  // nullable 불일치
private String description;
```

### 올바른 패턴
```java
// ERD: description VARCHAR(500) nullable
@Column(length = 500)  // nullable 기본값 true → 일치
private String description;
```

---

## D8: Class Diagram 멤버 (design 있을 때만)

### 비교 방법
1. `docs/design/{epic}/class-diagram.md`의 Mermaid 클래스 다이어그램에서 필드/메서드를 추출한다
2. Entity 클래스의 실제 필드/메서드와 비교한다
   - 다이어그램에 있는데 코드에 없는 멤버
   - 코드에 있는데 다이어그램에 없는 멤버 (참고 사항으로 보고, FAIL은 아님)

### 위반 패턴
```java
// Class Diagram: Product { -name, -description, -price, +create(), +update() }
// 실제: update() 메서드 누락
```

### 올바른 패턴
다이어그램의 모든 필드/메서드가 코드에 존재한다.
