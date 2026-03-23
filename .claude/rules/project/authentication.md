# 인증

## 인증 유형
- **불필요**: 인증 없이 접근 가능
- **User**: 일반 사용자 인증 — `X-Loopers-LoginId` + `X-Loopers-LoginPw` 헤더
- **Admin**: 관리자 인증 — `X-Loopers-Ldap` 헤더

## 구현 방식

| 유형 | 구현체 | 역할 |
|------|--------|------|
| Admin | `HandlerInterceptor` | 전역 경로 차단 — 헤더 검증 후 통과/거부 |
| User | `HandlerMethodArgumentResolver` | 메서드 파라미터 주입 — 헤더로 인증 후 `AuthenticatedUser` 객체 반환 |

- `AuthenticatedUser`: ArgumentResolver가 인증 후 반환하는 객체, Controller 메서드 파라미터로 주입
- 인증 실패 시 `CoreException(ErrorType.UNAUTHORIZED, ...)` 발생
