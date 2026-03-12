---
name: analyzing-concurrency
description: "Use when analyzing concurrency issues in Spring Boot / JPA code. Triggers: '동시성 분석', 'concurrency analysis', '락 분석', 'race condition', '동시성 이슈', '동시 접근', '경합 조건'. Covers: @Version, @Lock, @Retryable, distributed locks, race condition detection, concurrency test evaluation."
---

## Overview

Spring Boot / JPA 프로젝트의 동시성 이슈를 체계적으로 분석하는 스킬이다. 공유 가변 상태 식별부터 락 전략 평가, Race Condition 탐지, 동시성 테스트 커버리지까지 6개 영역을 점검한다.

---

## Quick Reference

| 분석 항목 | 핵심 질문 |
|----------|----------|
| 1. 공유 가변 상태 | 어떤 Entity 필드가 동시 쓰기 대상이며, 보호 기법이 있는가? |
| 2. 락 전략 | 낙관적/비관적/분산 락이 적절히 설정되어 있는가? |
| 3. Race Condition | Read-then-Write, Check-then-Act, Lost Update 패턴이 보호되는가? |
| 4. Retryable+TX 조합 | AOP 프록시 순서와 트랜잭션 전파 속성이 올바른가? |
| 5. TX 범위 vs 락 유지 | 불필요한 작업이 트랜잭션에 포함되어 락 유지 시간을 늘리는가? |
| 6. 동시성 테스트 | 동시 출발 보장, 경합 강도, 데이터 정합성 검증이 충분한가? |

---

## Analysis Scope

이 스킬은 프로젝트 전체를 스캔하여 동시성 관련 코드를 식별하고 분석한다.

### 탐색 대상
- `@Version` 이 선언된 Entity
- `@Lock` 이 선언된 Repository 메서드
- `@Retryable` 이 선언된 메서드
- `synchronized`, `Lock`, `Atomic*` 사용 코드
- `@Async`, `CompletableFuture`, `ExecutorService` 사용 코드
- `@Modifying` 벌크 UPDATE/DELETE (동시성 우회 패턴)
- DB Unique 제약조건으로 동시성을 제어하는 패턴
- Redis 기반 분산 락 (Redisson, Lettuce 등)
- Kafka Consumer의 동시 소비 설정

### 탐색 순서
1. Entity 레이어: `@Version`, `@Lock` 선언 현황 전수 조사
2. Service / Facade 레이어: `@Retryable`, `@Transactional` 조합 확인
3. Repository 레이어: 벌크 연산, 비관적 락 쿼리 확인
4. Infrastructure 레이어: Redis 락, Kafka 동시 소비 설정 확인
5. Test 레이어: 동시성 테스트 존재 여부 및 구조 평가

> 특정 메서드만 떼어내어 판단하지 않는다. 컨트롤러 -> Facade -> Service -> Repository 전체 흐름을 기준으로 분석한다.

---

## Analysis Checklist

6개 분석 영역별 상세 점검 항목과 출력 형식을 정의한다.

상세 체크리스트: [analysis-checklist.md](references/analysis-checklist.md)

---

## Improvement Proposal (선택적 제안)

개선안은 강제하지 않고 선택지로 제시한다. 각 제안에 대해 trade-off를 명시한다.

제안 카테고리:
- 락 전략 변경 (낙관적 -> 비관적, 또는 그 반대)
- @Retryable 위치/설정 조정
- 트랜잭션 분리로 락 유지 시간 단축
- 분산 락 도입 (멀티 인스턴스 환경 대비)
- 누락된 동시성 테스트 추가
- Race Condition 보호 기법 추가

**제안 형식**
```markdown
### [제안 N] 제목

**현재 구조:**
(코드 또는 다이어그램)

**문제점:**
(구체적 시나리오)

**개선안:**
(코드 또는 다이어그램)

**Trade-off:**
- 장점: ...
- 단점: ...
- 고려 사항: ...
```

---

## Output

분석 결과는 아래 경로에 Markdown 파일로 생성한다:
`.claude/report/analyzing-concurrency/ANALYSIS-RESULT.md`

### 결과 문서 구조

```markdown
# Concurrency Analysis Report

> 분석 일자: YYYY-MM-DD
> 대상: (분석 대상 범위)

## 목차
1. 전체 동시성 제어 구조 요약
2. 공유 가변 상태 식별
3. 락 전략 분석
4. Race Condition 탐지
5. @Retryable + @Transactional 조합 분석
6. 트랜잭션 범위 vs 락 유지 시간
7. 동시성 테스트 평가
8. 식별된 문제점 및 위험도
9. 개선 제안
```

위험도 분류:
- `[CRITICAL]` — 데이터 정합성 손상 가능 (초과 발급, Lost Update 등)
- `[WARNING]` — 현재 동작하지만 조건 변경 시 문제 발생 가능
- `[INFO]` — 개선하면 좋지만 당장 위험하지 않음
