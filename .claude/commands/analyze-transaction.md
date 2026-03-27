---
name: analyze-transaction
description: Use when reviewing or implementing code with @Transactional, JPA persistence context, or concurrency control. Triggers on transaction boundary design, lock strategy selection, dirty checking concerns, flush timing issues, or when user says "트랜잭션 분석", "락 분석", "동시성 점검".
---

# Analyze Transaction

대상이 되는 코드 범위를 탐색하고, Spring @Transactional, JPA, QueryDSL 기반의 코드에 대해 트랜잭션 범위, 영속성 컨텍스트, 쿼리 실행 시점 관점에서 분석한다.

특히 다음을 중점적으로 점검한다:
- 트랜잭션이 불필요하게 크게 잡혀 있지는 않은지
- 조회/쓰기 로직이 하나의 트랜잭션에 혼합되어 있지는 않은지
- JPA의 지연 로딩, flush 타이밍, 변경 감지로 인해 의도치 않은 쿼리 또는 락이 발생할 가능성은 없는지

단순한 정답 제시가 아니라, 현재 구조의 의도와 trade-off를 드러내고 개선 가능 지점을 선택적으로 판단할 수 있도록 돕는다.

## Analysis Scope

이 스킬은 아래 대상에 대해 분석한다:
- @Transactional 이 선언된 클래스 / 메서드
- Service / Facade / Application Layer 코드
- JPA Entity, Repository, QueryDSL 사용 코드
- 하나의 유즈케이스(요청 흐름) 단위

> 컨트롤러 → 서비스 → 레포지토리 전체 흐름을 기준으로 분석하며 특정 메서드만 떼어내어 판단하지 않는다.

## Analysis Checklist

### 1. Transaction Boundary 분석

다음을 순서대로 확인한다:

1. **트랜잭션 시작 지점은 어디인가?** — Service / Facade / 그 외 계층?
2. **트랜잭션이 실제로 필요한 작업은 무엇인가?** — 상태 변경(쓰기) vs 단순 조회
3. **트랜잭션 내부에서 수행되는 작업 나열:**
   - 외부 API 호출
   - 복잡한 조회(QueryDSL)
   - 반복문 기반 처리
   - 락 획득 (비관적/낙관적)

**출력 형식:**

```
현재 트랜잭션 범위: {클래스.메서드()}
  ├─ {작업 1} [읽기/쓰기/락]
  ├─ {작업 2} [읽기/쓰기/락]
  └─ {작업 3} [읽기/쓰기/락]

트랜잭션이 필요한 핵심 작업:
  - {작업 A}
  - {작업 B}
```

### 2. 불필요하게 큰 트랜잭션 식별

아래 패턴이 존재하는지 점검한다:

| 패턴 | 위험도 | 설명 |
|------|--------|------|
| Controller에서 @Transactional | 높음 | 트랜잭션 범위가 HTTP 요청 전체로 확장됨 |
| 읽기 로직이 쓰기 트랜잭션에 포함 | 중간 | 불필요한 락 경합, 커넥션 점유 |
| 외부 시스템 호출이 트랜잭션 내부 | 높음 | 네트워크 지연이 트랜잭션 길이에 직결 |
| 대량 조회가 트랜잭션 내부 | 중간 | 커넥션 풀 고갈 위험 |
| 상태 변경 이후 트랜잭션이 길게 유지 | 중간 | 락 홀딩 시간 증가 |

### 3. JPA / 영속성 컨텍스트 관점 분석

다음을 중심으로 분석한다:

- **flush 타이밍**: Entity 변경이 언제 DB에 반영되는지
- **변경 감지(dirty checking)**: 조회용 Entity가 의도치 않게 변경 감지 대상이 되는지
- **지연 로딩(lazy loading)**: 트랜잭션 후반에 N+1 쿼리가 발생할 가능성
- **1차 캐시 문제**: 같은 엔티티를 락 없이 먼저 읽은 후 FOR UPDATE로 다시 읽을 때 stale 데이터 반환
- **readOnly 미적용**: 단순 조회에 `@Transactional(readOnly = true)` 누락 여부

**체크리스트:**

```
□ 단순 조회인데 Entity 반환 후 변경 가능성 존재?
□ DTO Projection 대신 Entity 조회 사용 여부
□ QueryDSL 조회 결과가 영속성 컨텍스트에 포함되는지
□ 같은 엔티티를 락 없이 읽은 후 FOR UPDATE로 재조회하는 패턴?
□ @Transactional(readOnly = true) 적용 누락?
```

### 4. 동시성 제어 분석

락 전략이 적용된 경우 추가로 점검한다:

| 점검 항목 | 설명 |
|-----------|------|
| **락 전략 적합성** | 비관적 vs 낙관적 선택이 도메인 특성에 맞는가? |
| **데드락 위험** | 여러 리소스를 락 걸 때 순서가 일관적인가? |
| **Self-invocation** | @Transactional 메서드를 같은 빈 내부에서 호출하고 있지 않은가? |
| **재시도 로직** | 낙관적 락 사용 시 ObjectOptimisticLockingFailureException 재시도가 구현되었는가? |
| **트랜잭션 전파** | 하위 서비스의 @Transactional이 상위와 의도대로 합류하는가? |

### 5. Improvement Proposal (선택적 제안)

개선안은 강제하지 않고 선택지로 제시한다:

- **트랜잭션 분리**: 조회 → 쓰기 분리, Facade에서 orchestration
- **`@Transactional(readOnly = true)` 적용**
- **DTO Projection 도입**: 변경 감지 불필요한 조회
- **외부 호출/이벤트 발행을 트랜잭션 외부로 이동**
- **락 순서 통일**: 리소스별 ID 오름차순 정렬

**제안 형식:**

```
[개선안 N]
- 현재: {현재 구조 설명}
- 제안: {변경 방향}
- 장점: {기대 효과}
- 고려사항: {트레이드오프}
```

## 톤 & 스타일

- 정답을 단정하지 않고 **현재 구조의 의도를 먼저 파악**한 뒤 개선 가능 지점을 제시
- "이렇게 해야 한다"가 아니라 **"이런 선택지가 있다"**
- 코드 레벨에서 구체적 파일:라인 을 근거로 제시
- 개발자의 설계 주도권을 존중 — 제안은 하되 결정은 개발자가 한다
