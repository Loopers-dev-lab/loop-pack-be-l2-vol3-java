# API 문서화 (Swagger) 컨벤션

## 1. ApiSpec 인터페이스 패턴

Swagger 어노테이션은 ApiSpec 인터페이스에 분리하고, Controller가 이를 implements한다.

```
interfaces/
└── {domain}/
    ├── {Domain}V1ApiSpec.java          ← Swagger 어노테이션 (인터페이스)
    ├── Admin{Domain}V1ApiSpec.java     ← Admin용
    ├── {Domain}V1Controller.java       ← implements {Domain}V1ApiSpec
    ├── Admin{Domain}V1Controller.java
    └── dto/
        ├── {Domain}V1Dto.java
        └── Admin{Domain}V1Dto.java
```

### ApiSpec 구조

```java
@Tag(name = "User V1 API", description = "사용자 API 입니다.")
public interface UserV1ApiSpec {

    @Operation(
        summary = "회원가입",
        description = "새로운 사용자를 등록합니다.")
    ApiResponse<UserV1Dto.SignupResponse> signup(
        @RequestBody(description = "회원가입 요청 정보")
        UserV1Dto.SignupRequest request);

    @Operation(
        summary = "내 정보 조회",
        description = "인증된 사용자의 정보를 조회합니다. 헤더에 X-Loopers-LoginId와 X-Loopers-LoginPw를 포함해야 합니다.")
    ApiResponse<UserV1Dto.MyInfoResponse> getMyInfo(
        @Parameter(description = "로그인 ID", required = true)
        String loginId,
        @Parameter(description = "비밀번호", required = true)
        String password);
}
```

핵심 원칙:
- 반환 타입, 파라미터 타입, 메서드명은 Controller와 동일하게 맞춘다
- ApiSpec에는 Swagger 어노테이션만 둔다. Spring MVC 어노테이션(`@GetMapping`, `@PathVariable` 등)은 Controller에만 둔다

---

## 2. 네이밍 규칙

| 대상 | 네이밍 | 예시 |
|------|--------|------|
| 고객 ApiSpec | `{Domain}V1ApiSpec` | `ProductV1ApiSpec` |
| Admin ApiSpec | `Admin{Domain}V1ApiSpec` | `AdminProductV1ApiSpec` |
| 고객 DTO | `{Domain}V1Dto` | `ProductV1Dto` |
| Admin DTO | `Admin{Domain}V1Dto` | `AdminProductV1Dto` |

`V1` 포함 이유: V2 추가 시 `ProductV2ApiSpec`으로 확장.

---

## 3. 어노테이션 규칙

### 필수 어노테이션 참조 테이블

| 어노테이션 | 위치 | 필수 속성 | 예시 |
|-----------|------|----------|------|
| `@Tag` | 인터페이스 | `name`, `description` | `@Tag(name = "Product V1 API", description = "상품 API 입니다.")` |
| `@Operation` | 메서드 | `summary`(동사로 시작), `description`(상세, 인증 요구사항 포함) | `summary = "상품 목록 조회"` |
| `@Parameter` | 파라미터 | `description`(항상), `required`(필수일 때), `example`(ID/페이지 등) | `@Parameter(description = "상품 ID", required = true, example = "1")` |
| `@RequestBody`* | 파라미터 | `description` | `@RequestBody(description = "상품 등록 요청 정보")` |

*`@RequestBody`는 `io.swagger.v3.oas.annotations.parameters.RequestBody` (Spring의 것과 다른 패키지)

### @Tag name 형식

- 고객: `"{Domain} V1 API"` -- `"Product V1 API"`
- Admin: `"Admin {Domain} V1 API"` -- `"Admin Product V1 API"`

### @Parameter 선택 속성

| 속성 | 사용 시점 |
|------|----------|
| `hidden` | Swagger UI에서 숨길 파라미터 (내부용 헤더 등) |

### 복합 파라미터 예시

```java
@Operation(summary = "상품 목록 조회")
ApiResponse<PageResponse<ProductV1Dto.ListResponse>> getProducts(
    @Parameter(description = "브랜드 ID (필터)")
    Long brandId,
    @Parameter(description = "정렬 기준", example = "latest")
    String sort,
    @Parameter(description = "페이지 번호", example = "0")
    int page,
    @Parameter(description = "페이지 크기", example = "20")
    int size);
```

---

## 4. Controller 연결

Controller가 ApiSpec을 implements하고, `@Override`를 명시한다.

```java
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserV1Controller implements UserV1ApiSpec {

    private final UserFacade userFacade;

    @Override
    @PostMapping("/signup")
    public ApiResponse<UserV1Dto.SignupResponse> signup(
        @org.springframework.web.bind.annotation.RequestBody @Valid
        UserV1Dto.SignupRequest request) {
        return ApiResponse.success(
                UserV1Dto.SignupResponse.from(
                        userFacade.signup(request.toCommand())));
    }

    @Override
    @GetMapping("/me")
    public ApiResponse<UserV1Dto.MyInfoResponse> getMyInfo(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String password) {
        return ApiResponse.success(
                UserV1Dto.MyInfoResponse.from(
                        userFacade.getMyInfo(loginId, password)));
    }
}
```

어노테이션 배치 규칙:
- **Controller에만**: `@GetMapping`, `@PostMapping`, `@PathVariable`, `@RequestHeader`, `@Valid`, Spring `@RequestBody`
- **ApiSpec에만**: `@Tag`, `@Operation`, `@Parameter`, Swagger `@RequestBody`

---

## 5. DTO와 Schema 규칙

record DTO는 SpringDoc이 자동으로 Schema를 생성한다. `@Schema`는 필드명이 모호하거나 예시가 필요한 경우에만 추가한다.

### @Schema 추가 기준

| 기준 | 예시 |
|------|------|
| 필드명이 모호 | `status`, `type` 등 여러 의미 가능 |
| 단위가 중요 | 가격(원), 무게(g) |
| enum/특정 형식 | 날짜 포맷, 정렬 값 |
| example이 도움 | API 테스트 시 Swagger UI에서 바로 사용 |

```java
public record CreateRequest(
    @Schema(description = "상품명", example = "오버사이즈 코트")
    @NotBlank String name,
    @Schema(description = "판매가 (원)", example = "129000")
    @Positive int price,
    @Schema(description = "재고 수량", example = "50")
    @PositiveOrZero int stock,
    @Schema(description = "브랜드 ID", example = "1")
    @NotNull Long brandId) {}
```

Response에도 동일 기준을 적용한다.

---

## 6. 에러 응답 문서화

- 공통 에러(400, 401, 500 등)는 ControllerAdvice/글로벌 설정에서 한 번만 정의한다. 개별 ApiSpec에 반복하지 않는다.
- 해당 API 고유의 에러만 `@ApiResponse`로 명시한다.

```java
@Operation(
    summary = "상품 좋아요",
    description = "상품에 좋아요를 등록합니다.",
    responses = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "이미 좋아요한 상품")
    })
ApiResponse<Void> like(
    @Parameter(description = "상품 ID", required = true)
    Long productId,
    @Parameter(description = "사용자 ID", required = true)
    String loginId);
```
