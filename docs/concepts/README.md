# 개념 정리

> 개념 학습 후 저장하는 공간입니다. 각 파일은 개념 설명 + 트레이드오프 + 퀴즈 Q&A를 포함합니다.

## 파일 목록

| 파일 | 개념 | 퀴즈 통과 |
|------|------|----------|
| `01-sorted-set-basic.md` | Redis Sorted Set 기본 동작 (ZADD, ZRANK, ZREM, ZCARD) | ✅ |
| `02-zadd-nx.md` | ZADD NX 중복 방지 원리, 멱등성 보장 | ✅ |
| `03-toctou.md` | TOCTOU race condition → ZADD NX로 원자적 처리 | ✅ |
| `04-back-pressure.md` | Back-pressure — 대기열의 본질 | ✅ |
| `05-queue-vs-rate-limiting.md` | 대기열 vs Rate Limiting 차이 | ✅ |
| `06-thundering-herd.md` | Thundering Herd — 동시 진입 폭발 대응 | ✅ |
| `07-batch-size.md` | 배치 크기 N 산정 공식 | ✅ |
| `08-token-ttl.md` | 입장 토큰 & TTL 설계 | ✅ |
| `09-ttl-criteria.md` | TTL 기준 산정 (P95 기반) | ✅ |
| `10-token-validation-location.md` | 토큰 검증 위치 — Filter vs Interceptor vs AOP | ✅ |
| `11-polling-vs-sse.md` | Polling vs SSE 비교 | ✅ |
| `12-polling-load-reduction.md` | Polling 부하 완화 — Redis only, Adaptive Polling | ✅ |
| `13-estimated-wait-time.md` | 예상 대기 시간 계산 공식과 한계 | ✅ |
| `14-hikaricp.md` | HikariCP 커넥션 풀 고갈 원리 | ✅ |
| `15-redis-failure.md` | Redis 장애 시나리오 — 503 vs Fallback | ✅ |