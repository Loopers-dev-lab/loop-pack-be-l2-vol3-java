# 검증 및 예외 처리

## 검증 위치와 역할

| 위치 | 역할 | 예시 |
|---|---|---|
| `@Valid` (Request) | Fail-Fast 형식 검증 | `@NotNull`, `@NotBlank`, `@Size`, `@Positive`, `@PositiveOrZero` |
| Facade (`@Validated`) | Request 검증 경계 | 클래스 레벨 `@Validated`로 `@Valid` 트리거 |
| Entity | 자기 데이터의 모든 비즈니스 검증 | 길이, 범위, 상태 전이 규칙, 불변 조건 |
| ApplicationService | DB 조회가 필요한 검증 | 유일성, 존재 여부, 권한 |

- `@Valid`는 Entity 검증 중 일부를 앞단에서 선처리하는 것 (중복 검증 허용)
- Entity가 검증의 최종 방어선 — Request 검증이 빠져도 Entity에서 반드시 잡아야 함
- Facade에 `@Validated`를 선언하여, `@Valid` 파라미터를 Facade 진입 시점에 검증한다

## 예외 처리

- 비즈니스 예외는 `CoreException`으로 통일
- `CoreException(ErrorType, message)` 형태로 사용
- 글로벌 핸들러(`@RestControllerAdvice`)에서 일괄 처리
- Controller에서 try-catch 금지
- ErrorType은 HTTP 상태코드와 매핑되는 enum으로 관리 (`BAD_REQUEST`, `NOT_FOUND`, `FORBIDDEN` 등)
