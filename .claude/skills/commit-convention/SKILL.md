---
name: commit-convention
description: "Use when the user asks to commit changes. Triggers: '커밋', 'commit', '커밋 해줘', '커밋 남겨줘', '변경사항 정리', 'git commit'. 변경 파일을 의미 단위로 그룹핑하여 커밋 계획을 보여주고 확인 후 실행."
---

# Commit Convention

## Overview

변경된 파일을 의미 단위로 분리하여 일관된 커밋 메시지 규칙에 따라 커밋하는 스킬. 반드시 사용자 확인 후 실행한다.

## Quick Reference

| 타입 | 용도 | 분리 기준 |
|------|------|-----------|
| `feat` | 새 기능 구현 | 도메인 단위로 묶되, 테스트와 분리 |
| `fix` | 버그 수정 | 하나의 버그 관련 파일을 하나의 커밋으로 |
| `refactor` | 기능 변경 없이 코드 개선 | feat/fix와 분리 |
| `test` | 테스트 추가/수정 | 프로덕션 코드와 분리 |
| `docs` | 문서 추가/수정 | 코드 변경과 분리 |
| `chore` | 빌드, 설정, 의존성 | 기능 변경과 분리 |

## 실행 절차

### Step 1: 변경 사항 분석

```bash
git status
git diff --stat
git diff --cached --stat
```

변경된 파일 목록과 diff를 확인한다.

### Step 2: 커밋 단위 분리

변경 파일들을 **작업 의미** 기준으로 그룹핑한다. 분리 기준:

1. **도메인 단위** — 같은 도메인의 Entity, Service, Repository, Controller, DTO는 하나의 커밋
2. **작업 성격 단위** — 기능 구현 / 리팩토링 / 테스트 / 설정 변경은 분리
3. **의존 관계** — A 커밋이 B 커밋에 의존하면 A를 먼저 커밋

분리 예시:
```
// 주문 도메인 기능 구현 + 테스트를 작성한 경우 → 2개 커밋
커밋 1: feat: 주문 생성 기능 구현
  - domain/order/Order.java
  - domain/order/OrderService.java
  - domain/order/OrderRepository.java
  - infrastructure/order/OrderRepositoryImpl.java
  - infrastructure/order/OrderJpaRepository.java
  - application/order/OrderFacade.java
  - interfaces/order/OrderController.java
  - interfaces/order/dto/OrderDto.java

커밋 2: test: 주문 생성 테스트 코드 추가
  - domain/order/OrderTest.java
  - domain/order/OrderServiceTest.java
```

### Step 3: 커밋 계획을 사용자에게 보여주기

**반드시 실행 전에 확인을 받는다.** 아래 형식으로 보여준다:

```
📋 커밋 계획 (총 N개)

1️⃣ feat: 주문 생성 기능 구현
   - domain/order/Order.java (new)
   - domain/order/OrderService.java (new)
   - application/order/OrderFacade.java (new)
   - interfaces/order/OrderController.java (new)

2️⃣ test: 주문 생성 테스트 코드 추가
   - domain/order/OrderTest.java (new)
   - domain/order/OrderServiceTest.java (new)

3️⃣ refactor: 상품 엔티티 가격 검증 로직 리팩토링
   - domain/product/Product.java (modified)

이대로 커밋할까요?
```

사용자가 수정을 요청하면 (합치기, 쪼개기, 메시지 변경 등) 반영 후 다시 보여준다.

### Step 4: 커밋 실행

사용자가 확인하면 순서대로 실행한다.

```bash
# 커밋 1
git add domain/order/Order.java domain/order/OrderService.java ...
git commit -m "feat: 주문 생성 기능 구현"

# 커밋 2
git add domain/order/OrderTest.java domain/order/OrderServiceTest.java
git commit -m "test: 주문 생성 테스트 코드 추가"
```

실행 후 결과를 보여준다:

```
✅ 커밋 완료 (3개)
  1. feat: 주문 생성 기능 구현 (6 files)
  2. test: 주문 생성 테스트 코드 추가 (2 files)
  3. refactor: 상품 엔티티 가격 검증 로직 리팩토링 (1 file)
```

---

## 커밋 메시지 규칙

### 형식

```
{type}: {한글 설명}
```

### 커밋 타입

| 타입 | 용도 | 예시 |
|------|------|------|
| `feat` | 새 기능 구현 | `feat: 주문 생성 기능 구현` |
| `fix` | 버그 수정 | `fix: 재고 차감 시 음수 허용 버그 수정` |
| `refactor` | 기능 변경 없이 코드 개선 | `refactor: 장바구니 엔티티 리팩토링` |
| `test` | 테스트 추가/수정 | `test: 주문 생성 테스트 코드 추가` |
| `docs` | 문서 추가/수정 | `docs: API 명세서 업데이트` |
| `chore` | 빌드, 설정, 의존성 등 | `chore: QueryDSL 의존성 추가` |

### 메시지 작성 규칙

- **한글**로 작성한다
- **명확한 동사 + 작업 대상** 구조: `{무엇을} {어떻게 했다}`
- 메시지만 보고 어떤 작업인지 파악 가능해야 한다
- 마침표 붙이지 않는다

좋은 예시:
```
feat: 브랜드 CRUD API 구현
feat: 상품 좋아요 등록/취소 기능 구현
fix: 주문 취소 시 재고 미복원 버그 수정
refactor: ProductService 조회 로직 분리
test: 브랜드 생성 유효성 검증 테스트 추가
docs: README에 프로젝트 실행 방법 추가
chore: Spring Boot 3.2 버전 업그레이드
```

나쁜 예시:
```
feat: 수정                          ← 무엇을 수정했는지 모름
feat: Order 관련 작업                ← 어떤 작업인지 모호
fix: 버그 수정                       ← 어떤 버그인지 모름
refactor: 리팩토링                   ← 무엇을 리팩토링했는지 모름
```

---

## 커밋 분리 판단 기준

### 하나로 합치는 경우

- 같은 도메인의 **기능 구현** 관련 파일들 (Entity + Service + Repository + Controller + DTO)
- **하나의 버그 수정**에 관련된 여러 파일 변경
- **하나의 리팩토링** 목적으로 변경된 파일들

### 분리하는 경우

- **기능 구현 vs 테스트** — 프로덕션 코드와 테스트 코드는 분리
- **서로 다른 도메인** — 주문 기능과 상품 기능은 분리
- **서로 다른 성격** — 기능 구현(feat)과 리팩토링(refactor)은 분리
- **설정 변경** — build.gradle, application.yml 등은 별도 커밋 (chore)

### 커밋이 1개만 나오는 경우

변경이 단일 작업이면 굳이 쪼개지 않는다. 1개 커밋도 정상이다.

---

## Common Mistakes

| 실수 | 해결 |
|------|------|
| 기능 구현 + 테스트를 하나의 커밋으로 묶음 | `feat`과 `test` 커밋을 분리 |
| 서로 다른 도메인 변경을 하나의 커밋으로 묶음 | 도메인별로 커밋을 분리 |
| 커밋 메시지가 모호함 (`feat: 수정`) | 구체적인 동사 + 대상 사용 (`feat: 주문 생성 기능 구현`) |
| 사용자 확인 없이 바로 커밋 실행 | 반드시 커밋 계획을 보여주고 확인 후 실행 |
| 설정 변경을 기능 커밋에 포함 | `chore`로 별도 분리 (build.gradle, application.yml 등) |

---

## 주의사항

- **반드시 커밋 계획을 먼저 보여주고 확인받는다** — 절대 무확인 커밋하지 않는다
- `git push`는 하지 않는다 — 커밋만 수행한다
- 이미 staged된 파일이 있으면 먼저 알려주고 처리 방법을 물어본다
- untracked 파일이 있으면 포함 여부를 물어본다
- 충돌이나 에러가 발생하면 즉시 중단하고 알린다
