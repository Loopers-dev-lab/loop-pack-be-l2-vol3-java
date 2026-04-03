---
name: risk-discovery-test-cases
description: Surfaces project risks, limits, and edge cases through structured follow-up (Socratic) questioning, then turns findings into concrete test cases and refactor hints. Use when planning refactors, hardening features, reviewing design, or when the user asks for risk analysis, gap finding, exploratory testing ideas, or test cases derived from "what could go wrong."
---

# Risk Discovery → Test Cases → Refactor Hints

## When to apply

- 리팩토링 전/후 안전망이 필요할 때
- 요구사항·설계에 숨은 구멍을 찾을 때
- 회귀·동시성·실패 시나리오 테스트를 보강할 때

## Workflow (순서 고정)

1. **범위 고정**: 모듈/유스케이스/PR 범위를 한 문장으로 확정한다.
2. **1차 질문**: 아래 "질문 카테고리"에서 범위에 맞는 항목만 골라 5~8개 묻는다 (한 번에 전부가 아니라 층을 나눈다).
3. **꼬리질문**: 사용자 코드 → 가정·모호함·경계를 좁히는 **후속 질문 2~4개** (답이 "모른다/없다"면 그 자체를 리스크로 기록).
4. **리스크 레지스터**: 각 항목을 `R-번호 | 설명 | 발생 조건 | 영향 | 완화(테스트/코드)` 표로 정리한다.
5. **테스트케이스**: 레지스터 각 행을 프로젝트 규칙에 맞는 테스트로 변환한다 (아래 "테스트 매핑").
6. **리팩토링 힌트**: 테스트만으로 부족한 구조 문제는 짧은 "리팩토링 후보" 목록으로 분리한다 (구현은 사용자 승인 후).

## 질문 카테고리 (꼬리질문 재료)

한 카테고리에서 답이 나오면, **원인·데이터·동시에 일어날 때·복구** 쪽으로 한 단계 더 파고든다.

| 영역 | 예시 (표면) | 꼬리질문 방향 |
|------|-------------|----------------|
| 경계값 | null, 빈 컬렉션, 0, 최대 길이 | 스키마/DB 제약과 불일치 시 어떤 예외가 나는가 |
| 동시성 | 같은 리소스 동시 갱신 | 락 순서, 데드락, 재시도, 타임아웃 정책 |
| 실패·복구 | 외부 API, DB, Redis, Kafka | 부분 성공, 멱등성, 재처리, 사가/보상 |
| 보안·권한 | 다른 사용자 데이터 접근 | 식별자 노출, IDOR, 헤더 위조 |
| 시간 | 만료, 캐시 TTL, 배치 경계 | 시계 기준(타임존), 윤초/DST |
| 관측성 | 로그·메트릭 부재 | 장애 시 원인 추적 가능한가 |
| 운영 | 배포, 마이그레이션, 플래그 | 롤백, 호환성 |

## 리스크 레지스터 (출력 템플릿)

```markdown
| ID | Risk | Trigger | Impact | Mitigation (test / code idea) |
|----|------|---------|--------|--------------------------------|
| R1 | ... | ... | ... | ... |
```

## 테스트 매핑 (이 저장소 기준)

- **단위**: 도메인 규칙·VO·순수 로직 (`{메서드}_{조건}_{예상}` 네이밍).
- **통합**: 실제 DB/Redis 등 (`*IntegrationTest`).
- **E2E**: HTTP 전 구간 (`*E2ETest`).

각 리스크마다 최소 1개 테스트 행을 만든다:

```markdown
### TC-{id}-{seq}
- **From risk**: R{id}
- **Level**: unit | integration | e2e
- **Given**: ...
- **When**: ...
- **Then**: ... (기대 예외/상태/HTTP 코드 포함)
```

외부 연동·결제·대기열 등은 **멱등·타임아웃·중복 처리**를 Then에 명시한다.

## 리팩토링 힌트 (분리 규칙)

- 테스트로 막을 수 있는 것 = **행동 명세**로 남긴다.
- 정책이 여러 곳에 흩어지면 = **도메인 집중** 후보로만 표시하고, 큰 구조 변경은 사용자 확인 후 진행한다.

## Progressive disclosure

- 팀/도메인별 질문 체크리스트를 늘리려면 같은 디렉터리에 `reference.md`를 두고 링크한다.
- **예시(대기열 도메인)**: [reference.md](./reference.md) — 스킬로 도출한 TC 목록·Given/When/Then·설계 문서 링크.

## Anti-patterns

- 랜덤 데이터·재현 불가 테스트 제안 금지.
- Facade에 비즈니스 분기 넣기 같은 **아키텍처 위반** 리팩토링은 제안하지 말고, 규칙에 맞는 대안만 쓴다.
- TDD.md에 부합하는 테스트 케이스만 제안한다.
