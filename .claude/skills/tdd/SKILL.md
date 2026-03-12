---
name: tdd
description: "Use when developing features with TDD. Triggers: 'TDD로 개발', '테스트 먼저', 'TDD 모드', 'Red-Green-Refactor', 'TDD 시작'. DESIGN.md 기반 Red-Green-Refactor를 기능 단위 수직 슬라이스(Domain -> Application)로 실행. Solo/Pair 모드 지원."
---

# TDD Skill

DESIGN.md 기반 Red-Green-Refactor 실행 스킬. 핵심 규칙과 계층별 전략은 CLAUDE.md에 있으므로, 이 파일은 **시작 시 한 번만** 읽는다.

---

## Quick Reference

```
Red(실패 테스트 1개 작성 → 실행 → 실패 확인)
 → Green(최소 코드로 통과)
 → Refactor(개선 or skip)
 → 진행 문서 갱신
 → 다음 Red
```

- 수직 슬라이스: Domain → Application 관통 후 다음 기능
- 더블 전략: Entity/VO = 없음 | Domain Service = Fake | Facade = Mockito
- 테스트 실행: `./gradlew :apps:commerce-api:test --tests "{패키지}.{클래스}"`

---

## 진행 모드 선택

TDD 시작 시 사용자에게 진행 모드를 묻는다.

| 모드 | 설명 |
|------|------|
| **Solo** | Claude가 Red → Green → Refactor를 모두 수행. 결과를 진행 문서에 기록하며 연속 진행 |
| **Pair** | Claude가 Red(실패하는 테스트) 작성 → 사용자가 Green(통과 코드) 작성 → 함께 리뷰 + Refactor |

모드를 선택하지 않으면 **Pair를 기본값**으로 제안한다.

### Solo 모드

- Claude가 Red → Green → Refactor를 연속으로 수행
- 매 Round 후 진행 문서 갱신
- 전체 완료 후 사용자에게 결과 보고

### Pair 모드 (협력 개발)

각 Round를 다음 순서로 진행한다:

```
1. 🔴 Red: Claude가 실패하는 테스트를 작성하고, 테스트를 실행하여 실패를 확인한다
   → 사용자에게 "Red 확인. Green을 작성해주세요" 안내
   → 사용자가 직접 Green 코드를 작성하거나, Claude에게 Green 작성을 요청할 수 있다

2. 🟢 Green: 사용자(또는 사용자 요청 시 Claude)가 최소 코드로 테스트를 통과시킨다
   → 테스트 실행하여 통과 확인
   → 함께 Green 코드를 리뷰한다

3. 🔵 Refactor: 함께 리팩터링 필요 여부를 논의한다
   → 리팩터링이 필요하면 수행 후 테스트 재실행
   → 필요 없으면 skip

4. 다음 Round로 이동
```

**Pair 모드 핵심 규칙:**
- Claude는 Red만 작성하고 **멈춘다** — 사용자 턴을 기다린다
- 사용자가 "Green 해줘" / "통과시켜줘" 등 요청하면 Claude가 Green을 작성한다
- Refactor는 항상 사용자와 함께 논의 후 진행한다
- Round 사이에 사용자가 질문하거나 방향을 바꿀 수 있다

---

## 사전 조건

스킬 실행 전 반드시 Read:

1. `docs/design/{domain}/DESIGN.md` — 유즈케이스, 시퀀스, 클래스, ERD
2. `.claude/skills/project-convention/references/common/test-convention.md` — 테스트 구조, 네이밍, 더블 전략

---

## Step 1: 테스트 목록표 + 진행 문서 생성

DESIGN.md에서 테스트 케이스를 도출하여 정리한다.

```
┌──────────────────────────────────────────────────────┐
│  Feature: {기능명}                                    │
├───────────┬──────────────────┬────────────────────────┤
│  계층      │ 테스트 대상       │ 테스트 케이스           │
├───────────┼──────────────────┼────────────────────────┤
│  Domain   │ {Entity/VO}      │ {케이스}               │
│  Domain   │ {Service}        │ {케이스}               │
│  App      │ {Facade}         │ {케이스}               │
└───────────┴──────────────────┴────────────────────────┘
```

**반드시 사용자에게 보여주고 확인을 받은 후 진행한다.**

확인 후 진행 문서를 `docs/tdd/{domain}/{feature}.md`에 생성한다. (템플릿은 하단 참조)

## Step 2: Round 루프 실행

CLAUDE.md의 TDD 핵심 규칙에 따라 Red → Green → Refactor를 반복한다. 매 Round 후 진행 문서의 해당 Round 상태를 갱신한다.

- 🔴 Red 후: `🔴 Red: ✅ 실패 확인 — {요약}`
- 🟢 Green 후: `🟢 Green: ✅ 통과 — {요약}`
- 🔵 Refactor 후: `🔵 Refactor: ✅ {요약}` 또는 `skip`

## Step 3: 전체 테스트

`./gradlew :apps:commerce-api:test` 실행. 진행 문서에 결과 기록.

## Step 4: 완료 보고

상태를 `✅ 완료`로 변경, 산출물 경로 기록, 커밋 제안.

```
✅ TDD 완료
Feature: {기능명}
- 테스트: {N}개 작성, 전체 통과
- 구현: {파일 목록 요약}
커밋을 진행할까요?
```

---

## Fake 작성 규칙

Domain Service 테스트에서 Repository 대체 시 Fake 우선 사용. 기존 `FakeBrandRepository` 참고.

- `HashMap<Long, Entity>` + `AtomicLong`으로 저장소 구현
- `save()`: id 없으면 자동 생성, store에 저장
- `findById()`: store에서 조회, soft delete 필터링
- 테스트 소스(`src/test/java`)에 배치

### Fake vs Mockito 판단

```
외부 의존 없음 → 더블 불필요 (Entity, VO)
외부 의존 있음 + Domain 계층 → Fake
외부 의존 있음 + Application 계층 → Mockito mock()
```

---

## 프로덕션 코드 작성 시 참조

| 대상 | 경로 |
|------|------|
| Entity, VO | `.claude/skills/project-convention/references/domain/entity-vo-convention.md` |
| Service | `.claude/skills/project-convention/references/application/service-layer-convention.md` |
| 테스트 | `.claude/skills/project-convention/references/common/test-convention.md` |

---

## 템플릿: 진행 문서

```markdown
# TDD: {Feature 한글명}

| 항목 | 내용 |
|------|------|
| 도메인 | {domain} |
| 상태 | 🟡 진행 중 |

## 테스트 목록표

{Step 1에서 도출한 테스트 목록표}

## Round 진행 현황

### Round 1: {테스트 케이스명}
- 🔴 Red: ⏳
- 🟢 Green: ⏳
- 🔵 Refactor: ⏳

## 산출물

(완료 후 테스트/프로덕션/Fake 파일 경로 기록)
```
