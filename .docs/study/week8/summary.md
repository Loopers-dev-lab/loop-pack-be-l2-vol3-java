# Week 8 학습 정리

## 핵심 주제: Redis 기반 주문 대기열 시스템

트래픽 폭증(블랙 프라이데이) 시 시스템을 보호하면서 유저에게 공정한 대기 경험을 제공하는 구조 설계 및 구현.

---

## 학습 개념

| 개념 | 핵심 내용 |
|------|----------|
| **Back-pressure** | 하류 시스템(DB, PG)이 감당할 수 있는 속도만큼만 요청을 흘려보내는 원리 |
| **Rate Limiting vs Queuing** | 거부(429) vs 보관(대기열). 유저가 기다릴 의사가 있을 때는 Queuing |
| **Redis Sorted Set** | `ZADD/ZRANK/ZCARD/ZPOPMIN` — score=timestamp로 선착순 순서 보장 + 중복 방지 |
| **입장 토큰 (Entry Token)** | TTL이 있는 Redis key. 토큰 소유자만 주문 API 진입 가능 |
| **Thundering Herd** | 토큰 발급 직후 N명이 동시 요청 → 발급 간격 분산 또는 Jitter로 완화 |
| **Polling vs SSE** | Polling은 단순하지만 서버 부하 ↑, SSE는 서버 Push 방식으로 불필요한 요청 감소 |
| **Graceful Degradation** | Redis 장애 시 전략(전면 차단 / 우회 / Fallback 큐)을 사전에 정의 |

---

## 구현 과제 (Must-Have)

```
Step 1 — 대기열
  POST /queue/enter    → Redis Sorted Set에 userId + timestamp 저장
  GET  /queue/position → 현재 순번 + 예상 대기 시간 반환

Step 2 — 입장 토큰 & 스케줄러
  - 100ms마다 ~18명씩 토큰 발급 (Thundering Herd 완화)
  - 토큰 TTL 5분 설정
  - 주문 API 진입 시 토큰 검증 → 완료 후 삭제

Step 3 — 처리량 산정
  DB 커넥션 풀 50 / 평균 처리 200ms → 최대 250 TPS → 안전 마진 70% → 175 TPS
```

**Nice-To-Have**: SSE 실시간 Push, 순번 구간별 Polling 주기 동적 조절, Thundering Herd 완화 (Jitter), Graceful Degradation 구현

---

## 검증 포인트

- **동시 진입 테스트**: 대기열 순서가 정확히 보장되는지
- **토큰 만료 테스트**: TTL 초과 시 토큰이 무효화되는지
- **처리량 초과 테스트**: 스케줄러 배치 크기 이상의 요청에도 시스템이 안정적인지

---

## Technical Writing (블로그)

"무엇을 했다"보다 **"왜 그렇게 판단했는가"** 중심으로 작성.

작성 권장 질문:
- Rate Limiting vs Queuing — 어떤 상황에서 어떤 전략?
- 스케줄러 배치 크기 산정 근거
- Thundering Herd를 어떻게 완화했는가?
- Redis 장애 시 서비스는 어떻게 동작해야 하는가?
- Polling vs SSE — 선택 이유
- 토큰 TTL 설정 기준

---

## R7과의 연결

대기열은 **주문 API 앞단의 관문**이고, 주문 이후의 이벤트 발행 → Kafka → Metrics 집계 파이프라인은 **R7 구조 그대로** 활용.

```
[대기열] → POST /orders → ApplicationEvent → Kafka → collector (Metrics 집계)
```
