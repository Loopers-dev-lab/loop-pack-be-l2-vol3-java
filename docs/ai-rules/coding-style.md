# 코딩 스타일 규칙 (Java / Spring)

## 일반 원칙
- 불변성 우선 → `final`, 불변 객체 설계
- 함수는 SRP 준수
- 매직 넘버/문자열 → 상수로 추출
- 과도한 추상화 금지 → 3줄 유사 코드가 조기 추상화보다 낫다
- 사용하지 않는 코드 완전 삭제 (주석 처리 금지)
- 코드 뎁스 1로 제한

## Java 기본
- `final` 우선 → 변수, 파라미터, 필드 모두
- null 반환 금지 → `Optional` 또는 빈 컬렉션 반환
- `Optional` → 반환 타입으로만 사용 (생성자, 수정자, 메서드 파라미터 전달 금지)
- `Optional` 변수에 null 할당 금지 → `Optional.empty()` 사용
- 단순히 값을 얻으려는 목적으로만 `Optional` 사용 금지
- 컬렉션을 `Optional`로 감싸지 말 것 → 빈 컬렉션 반환
- `record` 활용 → 불변 DTO
- Stream API 적극 활용, 단 가독성 우선

## 네이밍
- 클래스 → PascalCase
- 메서드·변수 → camelCase
- 상수 → UPPER_SNAKE_CASE
- 패키지 → lowercase

## Spring 레이어 규칙
- Facade → Application Service만 호출 (Repository/Domain Service 직접 접근 금지)
- Application Service → 자기 도메인 Repository와 Domain Service만 접근
- Application Service 간 직접 호출 금지 → 크로스 도메인 협력은 Facade 경유
- Domain Service → 순수 비즈니스 규칙만 수행 (저장/외부 I/O/트랜잭션 금지)
- Controller → 요청/응답 변환만, 비즈니스 로직 금지
- `@Transactional` → Application Service에만 (Facade/Domain Service 절대 금지)

## Validation & Consistency
- API DTO에서 기본 Bean Validation(`@NotBlank`, `@Pattern`, `@Email`)은 허용한다
- 비즈니스 규칙 검증의 최종 책임은 Domain VO/Entity에 둔다 (중복 검증 허용)
- `exists -> save`만으로 중복 방어했다고 판단하지 않는다
- 유니크 보장은 DB 제약으로 강제하고, 저장 시점 중복 키 예외를 `409(CONFLICT)`로 변환한다

## Project-specific Security/Auth Rules
- `password`, `token`, `secret` 필드가 있는 Command/DTO는 `toString()`에서 민감정보를 반드시 마스킹한다
- 평문 비밀번호를 로그/예외 메시지에 노출하지 않는다
- 비밀번호 정책 검증(예: 생년월일 포함 금지)은 raw password에서만 수행하고, encoded password는 정책 검증 대상에서 제외한다
- 비밀번호 변경은 `@AuthUser` 기반 단일 인증만 사용한다 (동일 유스케이스에서 body 재인증 금지)
- 문자열 정규화/예약어 검증 시 Locale 의존 로직을 금지하고 `Locale.ROOT`를 사용한다

## 금지
- `System.out.println` 남기지 말 것
- `@SuppressWarnings` 남용 금지
- 불필요한 `private` 함수 지양 → 객체지향적 설계로 대체
- unused import 즉시 제거
