# AGENTS Router

이 파일은 상세 규칙을 직접 나열하지 않고, 작업 유형에 따라 참조할 규칙 파일을 지정한다.

## Always (항상 적용)
- 개발 방향/의사결정은 제안까지만 하고, 최종 결정은 사용자 승인 후 반영한다.
- 요청 범위를 임의로 확장하지 않는다.
- 실제 동작하는 코드만 작성한다. 임시 목업/가짜 동작으로 완료 처리하지 않는다.
- 오버엔지니어링을 피한다.

## Core Project Rules (항상 먼저 확인)
- `/Users/anseonghun/Documents/project/loop-pack-be-l2-vol3-java/docs/ai-rules/coding-style.md`
- `/Users/anseonghun/Documents/project/loop-pack-be-l2-vol3-java/docs/ai-rules/git-workflow.md`

## Task-based Rules (작업 유형별 추가 적용)
- 테스트 작성/수정: `/Users/anseonghun/Documents/project/loop-pack-be-l2-vol3-java/docs/ai-rules/testing.md`
- 성능 이슈/병목 개선: `/Users/anseonghun/Documents/project/loop-pack-be-l2-vol3-java/docs/ai-rules/performance.md`
- 보안 관련 변경(인증/인가/입력 검증): `/Users/anseonghun/Documents/project/loop-pack-be-l2-vol3-java/docs/ai-rules/security.md`

## Non-negotiable Architecture
- Facade는 여러 Application Service를 조합/오케스트레이션할 때만 사용한다
- Facade -> Application Service only (Repository/Domain Service 직접 접근 금지)
- 단일 Application Service 호출만 필요한 유스케이스는 Facade를 만들지 않고 Controller -> Application Service로 직접 연결한다
- Application Service -> 자기 도메인 Repository + Domain Service only
- Application Service 간 직접 호출 금지 (크로스 도메인 협력은 Facade에서 조정)
- Domain Service는 순수 비즈니스 규칙만 담당 (저장/외부 I/O/트랜잭션 금지)
- `@Transactional`은 Application Service에만 위치 (Facade/Domain Service 금지)
- 유니크 보장은 DB 제약으로 강제하고, 저장 시점 중복키 예외는 409로 변환

## Domain Constraints
- 결제 시스템 없음: 주문 완료 = 결제 완료
- 주문 시점 상품 정보 스냅샷 저장
- 어드민 인증은 `X-ROOPERS-LDAP` 헤더 기반
- 포인트 충전 기능은 Scope-out

## Project-specific Guardrails
- `password`/`token`/`secret` 필드는 `toString()`에서 마스킹
- 비밀번호 정책 검증은 raw password에서만 수행 (encoded password 제외)
- 비밀번호 변경은 `@AuthUser` 단일 인증 사용 (body 재인증 금지)
- 예약어/문자열 케이스 변환은 `Locale.ROOT` 사용
