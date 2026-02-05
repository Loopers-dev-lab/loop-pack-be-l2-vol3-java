# Exception Patterns

## 예외 종류별 사용

### IllegalArgumentException
- 도메인 불변식 검증 실패 시 사용
- User.create(), validate{Field}() 등에서 throw
- 글로벌 핸들러가 400 BAD_REQUEST로 변환

```java
private static void validateLoginId(String loginId) {
    if (loginId == null || loginId.isBlank()) {
        throw new IllegalArgumentException("로그인 ID는 필수입니다");
    }
}
```

### CoreException
- 비즈니스 로직 오류 시 사용
- Service 계층에서 throw
- ErrorType에 따라 HTTP 상태코드 결정

```java
// 중복 체크
if (userRepository.existsByLoginId(loginId)) {
    throw new CoreException(ErrorType.CONFLICT, "이미 사용 중인 로그인 ID입니다");
}

// 조회 실패
User user = userRepository.findByLoginId(loginId)
    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "회원을 찾을 수 없습니다"));

// 인증 실패
throw new CoreException(ErrorType.UNAUTHORIZED, "비밀번호가 일치하지 않습니다");
```

## ErrorType 선택 기준
| ErrorType | HTTP Status | 사용 상황 |
|-----------|-------------|-----------|
| BAD_REQUEST | 400 | 입력값 검증 실패 |
| UNAUTHORIZED | 401 | 인증 실패, 헤더 누락 |
| NOT_FOUND | 404 | 리소스 없음 |
| CONFLICT | 409 | 중복 리소스 |
| INTERNAL_ERROR | 500 | 예상치 못한 오류 |

## 금지 사항
- Controller/Service에서 try-catch로 예외 잡기 금지
- DataIntegrityViolationException 직접 처리 금지 (글로벌 핸들러가 처리)
