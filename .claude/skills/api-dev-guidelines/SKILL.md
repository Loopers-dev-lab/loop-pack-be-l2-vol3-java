---
name: api-dev-guidelines
description: "API 구현 규칙, RESTful 컨벤션, 코드 품질 컨벤션, 아키텍처 패턴. API나 도메인 코드를 구현할 때 활성화한다."
---

이 스킬은 프로젝트의 API 구현 규칙과 코드 품질 컨벤션을 정의한다.
코드 패턴과 아키텍처 상세는 resources/ 파일을 참조한다.

## 에러 처리 철학

1. 검증 실패는 예외가 아닌 **Response return**으로 처리한다
2. `throw`는 예상치 못한 시스템 에러에만 사용한다
3. 의미 없는 `try/catch`(잡아서 다시 던지기만)는 작성하지 않는다
4. 에러 코드는 시스템 예외에만 사용하고, 검증 실패는 메시지로 전달한다

## 로깅 및 에러 메시지 분리

- **시스템 로그**: 구체적 정보(요청 파라미터, 상태값) 기록. `log.error("error", e)` 금지
- **클라이언트 응답**: 사용자 친화적 메시지만 반환. `e.getMessage()` 노출 금지
- **예외 감쌀 때**: 정확한 메시지 + cause 보존. `e.toString()` 노출 금지

```java
// Good
log.error("사용자 조회 실패 - userId: {}, reason: {}", userId, e.getMessage(), e);
return ApiResponse.error("사용자 정보를 불러올 수 없습니다.");

// Good: cause 보존
catch (DateTimeParseException e) {
    throw new CoreException(UserErrorType.INVALID_BIRTH_DATE,
            "생년월일은 YYYY-MM-DD 형식이어야 합니다.", e);
}
```

## 주석 규칙

- **정책/도메인 규칙**: 다른 개발자가 반드시 알아야 하는 비즈니스 정책은 상세 주석 필수
- **일반 코드**: 코드로 의도가 명확하면 생략

## RESTful API 컨벤션

- URI에 **동사 금지**, 명사만 사용. 행위는 HTTP Method로 표현
- 긴 URI는 **하이픈(`-`)** 구분. 언더스코어/camelCase 금지
- 필터링은 기존 GET API에 **쿼리 파라미터** 추가 (별도 API 금지)
- Path Variable은 노출 가능한 식별자만 사용 (민감 정보 금지)

```
# Good
GET /users?status=active
GET /order-histories

# Bad
GET /getUsers
GET /user_addresses
```

## 함수명 네이밍

| HTTP Method | Controller/Service | Repository |
|-------------|-------------------|------------|
| GET (목록) | `getUsers` | `findAll` |
| GET (단건) | `getUser` | `findByUserNo` |
| POST | `createUser` | `save` |
| PUT | `updateUser` | `save` |
| DELETE | `deleteUser` | `save` (소프트 삭제) |

- 단수/복수 혼용 금지. 일관되게 사용

## 코드 품질 규칙

1. **DTO 분리**: Entity 직접 노출 금지. Request/Response record DTO 필수
2. **계층 의존성**: Controller -> Service -> Repository 단방향만 허용
3. **트랜잭션**: 조회는 `@Transactional(readOnly = true)`, 변경만 `@Transactional`
4. **매직 넘버 금지**: `static final` 상수 또는 enum으로 관리
5. **메서드 단일 책임**: 길어지면 private 메서드로 분리
6. **Null 안전성**: Optional 활용 (Entity 필드 제외). null 체크 후 즉시 return
7. **순환 참조 금지**: 공통 로직을 별도 Service/Facade로 분리
8. **Lombok 금지**: 생성자, getter 직접 작성. record 활용
9. **생성자 주입**: `private final` + `this.` 접두어 필수
10. **DTO는 record**: 불변성 보장
11. **Entity 외부 노출 금지**: Response DTO로 변환 필수
12. **Null 체크 필수**: 조회 결과, 외부 입력, 파라미터 모두 검증

## 삭제는 소프트 삭제

- `deleted_at` 컬럼 업데이트로 논리 삭제
- 조회 시 `deleted_at IS NULL` 조건 필터링

## 소프트웨어 설계 원칙

- 기술 도입 전 문제 정의 우선
- 서비스 경계 명확화, 상태 동기화 문서화
- 개선/실험은 테스트로 검증
- 설계 의도를 코드와 문서에 함께 남김

## 코드 패턴 참조

구현 시 아래 리소스의 코드 예제를 참조한다:
- `resources/architecture.md`: 계층별 책임, 파일 구조, Hexagonal Architecture
- `resources/code-patterns.md`: Entity, VO, DTO, ApiResponse, ErrorType 코드 패턴