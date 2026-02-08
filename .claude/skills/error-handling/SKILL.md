---
name: error-handling
description: 에러 처리 패턴 및 에러 타입 추가 절차. 새 비즈니스 예외를 추가하거나 에러 응답 구조를 변경할 때 사용한다.
---

# Error Handling

## 1. 에러 처리 흐름

```
도메인/서비스 코드
  → throw CoreException(ErrorType.XXX)
    → GlobalExceptionHandler가 자동 캐치
      → ErrorResponse(code, message) 반환
```

- 모든 비즈니스 예외는 **공통 예외 클래스 + 에러 타입 enum** 조합으로 처리
- GlobalExceptionHandler는 수정 불필요 — 새 에러 타입 추가만으로 자동 처리됨

## 2. 에러 응답 스키마

```json
{
  "code": "<에러 코드 문자열>",
  "message": "<사용자 향 에러 메시지>"
}
```

- HTTP 상태 코드는 에러 타입 enum에서 관리
- 응답 본문에 stack trace **절대 노출 금지**
- 에러 메시지에 내부 구현 상세 노출 금지

## 3. 에러 타입 enum 구조

```
ErrorType {
    HttpStatus status;    // HTTP 상태 코드
    String code;          // 에러 코드 (예: "E001")
    String message;       // 기본 에러 메시지
}
```

## 4. 새 에러 타입 추가 절차

### 4.1 추가 단계

1. `ErrorType` enum에 새 값 추가
   ```
   NEW_ERROR_TYPE(HttpStatus.BAD_REQUEST, "E999", "에러 메시지")
   ```
2. 도메인 코드에서 사용
   ```
   throw new CoreException(ErrorType.NEW_ERROR_TYPE)
   ```
3. (선택) 커스텀 메시지가 필요한 경우
   ```
   throw new CoreException(ErrorType.NEW_ERROR_TYPE, "커스텀 메시지")
   ```

### 4.2 테스트 체크리스트

- [ ] `ErrorType` enum에 새 값 추가
- [ ] `ErrorTypeTest.errorTypeProvider()`에 테스트 케이스 추가
- [ ] `ErrorTypeTest.enumConstantCount()`의 `hasSize(N)` 값을 N+1로 업데이트
- [ ] 해당 에러를 발생시키는 비즈니스 로직의 단위 테스트 작성
- [ ] E2E 테스트에서 에러 응답 검증 추가

## 5. Validation 에러 처리

- `@Valid` 어노테이션 검증 실패 시 자동 처리
- `MethodArgumentNotValidException` → `400 BAD_REQUEST` 자동 반환
- 별도 코드 작성 불필요 — 프레임워크가 자동 처리

## 6. 인증 에러 패턴

```
Controller: @RequestHeader(required = false) → null 허용
  → Facade에서 null/blank 검증
    → 단일 UNAUTHORIZED 응답 반환
```

- **보안 원칙**: 인증 실패 사유를 구분하지 않음 (ID 없음, PW 틀림 모두 동일 응답)
- 비밀번호 검증은 도메인 모델에 위임

## 7. 에러 처리 계층별 역할

| 계층 | 역할 | 예시 |
|------|------|------|
| Domain Model | 비즈니스 규칙 위반 시 예외 발생 | 비밀번호 형식 검증 실패 |
| Domain Service | 리포지토리 의존 불변식 검증 | 중복 ID 검증 |
| Facade | 인증/인가 검증, 유스케이스 예외 | 인증 헤더 누락 |
| Controller | `@Valid` 요청 바디 검증 위임 | 필수 필드 누락 |
| GlobalExceptionHandler | 공통 예외를 HTTP 응답으로 변환 | 자동 처리 |

## 8. 금지 사항

- stack trace를 응답에 포함시키기 금지
- 에러 메시지에 내부 클래스명/패키지 구조 노출 금지
- `try-catch`로 예외를 삼키고(swallow) 무시하기 금지
- 비즈니스 예외에 `RuntimeException`을 직접 사용 금지 (공통 예외 클래스 사용)
- `Exception` 또는 `Throwable`을 catch-all로 남용 금지

## 9. 에러 코드 네이밍 규칙

- enum 상수명: `UPPER_SNAKE_CASE` (예: `INVALID_PASSWORD_FORMAT`)
- 에러 코드 문자열: 프로젝트 규약에 따름 (예: `E001`, `USER_001`)
- 메시지: 한국어로 사용자 친화적으로 작성 (내부 용어 사용 금지)
