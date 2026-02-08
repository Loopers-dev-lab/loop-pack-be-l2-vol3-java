---
name: domain-model
description: 도메인 모델 설계 패턴 (팩토리 메서드, Value Object, 도메인 서비스). 새 도메인 모델을 생성하거나 기존 모델에 비즈니스 규칙을 추가할 때 사용한다.
---

# Domain Model

## 1. 팩토리 메서드 패턴

| 메서드 | 용도 | 검증 | id |
|--------|------|------|----|
| `create(...)` | 새 객체 생성 | 유효성 검증 + 정규화 | null |
| `reconstruct(...)` | DB 복원 | 검증/정규화 생략 | DB 값 |

- 생성자는 반드시 `private`으로 제한
- `create()`와 `reconstruct()`만 인스턴스 생성 가능

```
public class {Domain} {
    private {Domain}(Long id, String name) { ... }

    public static {Domain} create(String name) {
        // 유효성 검증 → 정규화
        return new {Domain}(null, normalized);
    }
    public static {Domain} reconstruct(Long id, String name) {
        return new {Domain}(id, name);
    }
}
```

## 2. 유효성 검증 순서

```
null 체크 → empty 체크 → 길이 제한 → 포맷(정규식) → 비즈니스 규칙
```

- 각 단계 실패 시 해당 `ErrorType`으로 예외 발생
- 이전 단계를 통과해야 다음 단계로 진행

## 3. 입력값 정규화 (Normalization)

- `create()` 팩토리 메서드에서**만** 수행
- Facade/Service에서 중복 정규화 **금지** (도메인 모델이 단일 책임)

| 필드 유형 | 정규화 | 예시 |
|-----------|--------|------|
| 식별자 (loginId 등) | `trim().toLowerCase()` | `"  Admin "` → `"admin"` |
| 이름, 이메일 | `trim()` | `"  홍길동 "` → `"홍길동"` |
| 비밀번호 | 정규화 없음 | 공백도 의미 있는 문자 |

## 4. Value Object (VO)

- 불변 record로 구현, 자체 유효성 검증 수행, 비즈니스 로직 캡슐화

| 메서드 | 용도 | 검증 |
|--------|------|------|
| `create(rawValue)` | 새 값 생성 | 검증 + 변환 포함 |
| `fromEncoded(encodedValue)` | 저장된 값 복원 | 검증 생략 |

```
public record Password(String value) {
    public static Password create(String raw) { /* 검증 → 인코딩 */ }
    public static Password fromEncoded(String encoded) { return new Password(encoded); }
}
```

## 5. 필드 가변성

| 유형 | 선언 | 변경 방법 | 예시 |
|------|------|-----------|------|
| 불변 | `private final` | 변경 불가, setter 없음 | loginId, name, email |
| 가변 | `private` (non-final) | `changeXxx()` 메서드 | password |

- `setXxx()` 금지 → 의미 있는 메서드명 `changeXxx()` 사용
- 가변 필드 변경 시에도 유효성 검증 수행

## 6. 도메인 서비스 (Domain Service)

**사용 시점**: 리포지토리 조회가 필요한 비즈니스 불변식 검증 (예: 중복 ID 검증)

설계 원칙:
- **순수 클래스** — 프레임워크 어노테이션 없음
- 생성자에 함수형 인터페이스 주입 (예: `Predicate<String>`)
- Config 클래스에서 `@Bean`으로 등록, 리포지토리 메서드 레퍼런스 주입

```
// 도메인 서비스 (순수 클래스)
public class LoginIdDuplicateValidator {
    private final Predicate<String> existsByLoginId;
    public LoginIdDuplicateValidator(Predicate<String> existsByLoginId) { ... }
    public void validate(String loginId) {
        if (existsByLoginId.test(loginId)) throw new CoreException(ErrorType.DUPLICATED);
    }
}

// Config 등록
@Bean LoginIdDuplicateValidator validator(UserQueryRepository repo) {
    return new LoginIdDuplicateValidator(repo::existsByLoginId);
}
```

## 7. BaseEntity 제약사항

- `id`: `final Long id = 0L` + `@GeneratedValue(IDENTITY)` → 직접 설정 불가
- `createdAt`, `updatedAt`: `@PrePersist`, `@PreUpdate`로 자동 관리
- Soft delete: `deletedAt` 필드, `delete()`/`restore()` 메서드 제공

## 8. 금지 사항

- 도메인 모델에 프레임워크 어노테이션 사용 금지 (`@Entity`, `@Service` 등)
- 도메인 모델에서 직접 DB 접근 금지
- `setXxx()` 메서드 사용 금지 → `changeXxx()` 사용
- Facade/Service에서 입력값 정규화 수행 금지 (도메인 모델 책임)
- 도메인 서비스에 프레임워크 어노테이션 사용 금지
