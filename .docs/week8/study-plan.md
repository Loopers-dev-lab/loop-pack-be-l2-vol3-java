# Week 8 학습 계획: Redis 기반 대기열 시스템

> **TL;DR** — Redis Sorted Set으로 대기열을 구현하고, 입장 토큰과 스케줄러로 처리량을 제어하며, Thundering Herd/Graceful Degradation까지 실무 수준의 설계 판단을 내것으로 만드는 학습 계획

---

## 목차

| Phase | 주제 | 핵심 키워드 |
|-------|------|------------|
| **Phase 0** | 개념 기반 다지기 | Back-pressure, Rate Limiting vs Queuing, Sorted Set |
| **Phase 1** | 핵심 설계 결정 | Sorted Set vs List, 토큰 설계, TTL 전략, 스케줄러 설계, 원자성 |
| **Phase 2** | 엣지 케이스 & 장애 시나리오 | Failover, 스케줄러 크래시, 중복 진입, Clock Skew, Overflow |
| **Phase 3** | 프로덕션 수준 고려사항 | 모니터링, 부하 테스트, Anti-Bot, 우선순위 큐, Circuit Breaker |
| **Phase 4** | 테스트 전략 | 동시성 테스트, 스케줄러 테스트, 토큰 라이프사이클 테스트 |
| **Phase 5** | 비교 분석 | Gmarket, AWS Virtual Waiting Room, Cloudflare, Queue-it |

---

## Phase 0: 개념 기반 다지기

> 코드를 작성하기 전, "왜 대기열이 필요한지"와 "어떤 자료구조가 적합한지"를 깊이 이해하는 단계.
> Week 7에서 이미 사용한 패턴들을 참조점으로 삼아 학습한다.

### 0-1. Back-pressure 이해

#### 학습 목표
- [ ] Back-pressure, Throttling, Rate Limiting의 차이 이해
- [ ] 하류 시스템(DB, PG)이 처리 속도를 결정한다는 원리 이해
- [ ] 서버 스케일링으로 해결되지 않는 이유 (DB 커넥션 풀 천장, PG Rate Limit)
- [ ] 재시도 폭풍(Retry Storm)이 원래 트래픽보다 위험한 이유

#### 우리 코드에서 이미 쓰고 있는 곳 (참조점)

| 위치 | 패턴 | Back-pressure 유형 |
|------|------|-------------------|
| `OutboxRelayScheduler` | 배치 100건 + 연속 실패 3회 시 중단 | 처리량 제한 + Circuit Breaker |
| `PaymentRecoveryScheduler` | fixedDelay=30s, 건별 try/catch | 속도 조절 + 장애 격리 |
| Kafka Consumer (`KafkaConfig`) | `MAX_POLLING_SIZE=3000`, `MAX_POLL_INTERVAL_MS=120000` | Consumer-side back-pressure |
| `CouponFacade.requestIssue()` | Redis Lua → Kafka → Consumer 순차처리 | 버퍼링을 통한 back-pressure |

> 💡 `OutboxRelayScheduler.java`의 `MAX_CONSECUTIVE_FAILURES = 3` 패턴을 읽어보자. 대기열 스케줄러에도 동일한 안전장치가 필요하다.

#### 깊이 있는 고민 질문

1. **Week 7 쿠폰 파이프라인과 이번 대기열의 본질적 차이는?**
   - 쿠폰: fire & forget (유저가 결과를 나중에 확인). 대기열: 유저가 화면 앞에서 실시간으로 기다림.
   - 이 차이가 시스템 설계에 어떤 영향을 주는가? (Polling 부하, 이탈률 관리, 토큰 TTL 설정 등)

2. **서버를 10배 늘려도 안 되는 이유를 수치로 설명할 수 있는가?**
   - HikariCP 50개 × 10서버 = 500 커넥션. MySQL `max_connections` 기본값은 151.
   - 서버를 늘리면 DB가 먼저 터진다.

3. **Rate Limiting과 대기열을 동시에 쓴다면, 어떤 순서로 배치하는가?**
   - Rate Limiting → 봇/비정상 트래픽 차단 → 정상 유저만 대기열 진입
   - 대기열 자체에도 최대 인원 제한이 필요한가?

---

### 0-2. Redis Sorted Set 깊이 파기

#### 학습 목표
- [ ] Sorted Set 내부 구조 이해 (Skip List + Hash Table)
- [ ] 핵심 명령어별 시간 복잡도 완벽히 이해
- [ ] `ZADD` 플래그 (`NX`, `XX`, `GT`, `LT`) 각각의 용도 이해
- [ ] 메모리 사용량 추정 능력 (10만 유저 → 약 10~15MB)
- [ ] 대안 자료구조들과의 비교를 통해 "왜 Sorted Set인지" 설명 가능

#### 핵심 명령어와 시간 복잡도

| 명령어 | 복잡도 | 대기열에서의 용도 |
|--------|--------|-----------------|
| `ZADD key NX score member` | O(log N) | 대기열 진입 (중복 방지) |
| `ZRANK key member` | O(log N) | 순번 조회 (0-based) |
| `ZCARD key` | O(1) | 전체 대기 인원 |
| `ZPOPMIN key count` | O(log N × count) | 앞에서 N명 꺼내기 (스케줄러) |
| `ZSCORE key member` | O(1) | 유저가 이미 큐에 있는지 확인 |
| `ZREM key member` | O(log N) | 유저 자발적 이탈 |

#### 대안 자료구조 비교

| 자료구조 | 순서 보장 | 순번 조회 | 중복 방지 | 확장성 |
|----------|----------|----------|----------|--------|
| **Redis Sorted Set** | score 기반 | O(log N) ZRANK | Set 특성으로 자동 | 단일 노드 수백만 |
| Redis List (LPUSH/RPOP) | FIFO 삽입순 | O(N) 스캔 필요 | 수동 중복체크 필요 | 순번 조회 불가능 |
| Kafka Topic | 파티션 내 offset순 | offset 계산 복잡 | 외부 dedup 필요 | 수평 확장 가능 |
| MySQL 테이블 | auto_increment/timestamp | SELECT COUNT 필요 | UNIQUE 제약 | 고빈도 쓰기에 병목 |
| In-Memory Queue | FIFO | O(N) 또는 수동 추적 | 수동 | 단일 인스턴스, 재시작 시 유실 |

#### 깊이 있는 고민 질문

1. **`ZADD NX` 없이 `ZADD`만 쓰면 어떤 문제가 생기는가?**
   - 이미 큐에 있는 유저가 다시 요청하면 score(timestamp)가 갱신됨 → 큐 맨 뒤로 밀림
   - 이것이 "공정성"을 깨뜨리는 이유를 설명할 수 있는가?

2. **ZPOPMIN으로 유저를 꺼낸 후, 남은 유저들의 ZRANK는 어떻게 변하는가?**
   - 모든 순번이 자동으로 앞당겨진다 (0-based index). 폴링 중인 유저 입장에서는 "순번이 줄었다" → 좋은 UX

3. **100만 명이 동시에 큐에 있을 때 Redis 메모리는?**
   - member(userId 10자) + score(8바이트) + 오버헤드 ≈ 100바이트/entry
   - 100만 × 100바이트 = ~100MB. Redis가 충분히 감당 가능.
   - 하지만 **폴링 부하가 문제**: 100만 명 × 2초 간격 = 초당 50만 ZRANK 호출
   - Redis 단일 코어 처리량: ~10만~30만 ops/s → 초과. 이것이 SSE나 동적 폴링이 필요한 이유.

#### 실습 추천
- Redis CLI에서 직접 `ZADD`, `ZRANK`, `ZPOPMIN`, `ZCARD` 조합을 실행해보기
- `ZADD NX`와 `ZADD` 동작 차이를 직접 확인
- `docker exec -it redis-master redis-cli` 로 로컬 Redis에 접속

#### 검색 키워드
- `"Redis sorted set internals skip list"` — 내부 구조
- `"Redis ZADD NX XX GT LT"` — 플래그 상세
- `"Redis sorted set memory overhead benchmark"` — 메모리 벤치마크

---

## Phase 1: 핵심 설계 결정

> 각 결정에 대해 "왜 이것을 선택했는지" 근거를 설명할 수 있어야 한다.
> 면접에서 "대안은 고려하셨나요?"라고 물으면 바로 대답할 수 있는 수준이 목표.

### 1-1. 토큰 설계: UUID vs JWT vs HMAC

| 방식 | 장점 | 단점 | 적합도 |
|------|------|------|--------|
| **UUID (추천)** | 단순, 라이브러리 불필요 | Redis 조회 필수 (stateful) | ★★★ |
| JWT | 자체 검증 가능 (stateless) | 폐기 어려움, 오버엔지니어링 | ★ |
| HMAC 서명 | 위변조 방지, 가벼움 | TTL 강제는 여전히 Redis 필요 | ★★ |

#### 왜 UUID + Redis가 적합한가?
- 토큰 수명이 5분으로 극히 짧음 → JWT의 stateless 장점이 불필요
- 토큰 단일 사용 보장 필요 → 어차피 Redis에서 삭제해야 함 (stateful)
- 우리 코드의 `coupon:issue-lock` 패턴과 동일 구조 → 검증된 패턴 재사용

#### 우리 코드에서 참조할 곳
- `RedisCouponStockRepository.java` — `coupon:issue-lock:{templateId}:{userId}` 키 구조
- `coupon_issue_request.lua` — `SET key '1' EX 300` 패턴 → 토큰에서도 `SET entry-token:{userId} {uuid} EX 300`

#### 깊이 있는 고민 질문
1. 토큰을 userId 기반 키에 저장하면 (`entry-token:{userId}`), 한 유저는 동시에 하나의 토큰만 가질 수 있다. 이것이 왜 올바른 설계인가?
2. 만약 토큰을 `entry-token:{token}` 키로 저장하면 어떤 차이가 생기는가? (힌트: 조회 방향이 달라짐)

---

### 1-2. TTL 전략: 고정 vs 적응형

#### 고정 TTL (먼저 구현)
- 5분 (300초) — 우리 코드의 `LOCK_TTL_SECONDS = 300`과 동일
- `SET entry-token:{userId} {token} EX 300`
- 단순하고 예측 가능

#### 적응형 TTL (심화)
```
TTL = max(120, min(600, 300 × (1000 / queueDepth)))
```
- 대기 인원 많을 때 → TTL 짧게 (미사용 토큰 빠르게 회수)
- 대기 인원 적을 때 → TTL 길게 (유저에게 여유 부여)

#### 핵심 모니터링 지표

| TTL이 너무 짧으면 | TTL이 너무 길면 |
|------------------|----------------|
| Token Expiry Rate > 30% | Token Conversion Rate < 50% |
| 유저가 주문 페이지 작성 중 만료 | Dead 유저가 슬롯 점유 |
| 유저 불만, 이탈률 증가 | 큐 처리량이 시스템 용량 아래로 하락 |

#### 깊이 있는 고민 질문
1. "유저가 결제 정보를 입력하는 평균 시간"이 TTL 설정의 핵심 기준이다. 이 데이터가 없을 때 어떻게 초기값을 정하는가?
2. TTL을 동적으로 변경하면, 이미 발급된 토큰의 TTL도 변경해야 하는가?

---

### 1-3. 스케줄러 설계

#### fixedDelay vs fixedRate

| 방식 | 동작 | 리스크 |
|------|------|--------|
| `fixedDelay = 100` | 이전 실행 완료 후 100ms 대기 | 실행 시간만큼 간격 늘어남 |
| `fixedRate = 100` | 100ms마다 실행 시도 | 실행이 밀리면 즉시 연속 실행 |

> 우리 코드의 모든 스케줄러가 `fixedDelay`를 사용한다. 일관성과 안전성을 위해 `fixedDelay` 권장.

#### 배치 크기 산정 근거

```
DB 커넥션 풀: 50개 (HikariCP)
주문 1건 평균 처리 시간: 200ms (비관적 락 + 쿠폰 검증 + 이벤트 발행)
이론적 최대 TPS: 50 / 0.2 = 250 TPS
안전 마진 70%: 175 TPS
스케줄러 간격: 100ms (초당 10회 실행)
배치 크기: 175 / 10 = ~18명/회
```

#### 다중 인스턴스 문제

commerce-api를 2대 운영하면, 두 스케줄러가 동시에 `ZPOPMIN`을 호출한다.
- ZPOPMIN은 Redis에서 atomic → 두 인스턴스가 각각 다른 18명을 가져감
- 결과: 초당 36명 토큰 발급 → 주문 시스템에 2배 부하

**해결 방안:**
1. 분산 락 (`SETNX queue-scheduler-lock 1 EX 1`) → 하나의 인스턴스만 실행
2. 배치 크기를 인스턴스 수로 나눔 (18 → 9)
3. 스케줄러를 별도 프로세스로 분리 (e.g., `commerce-batch` 활용)

#### 깊이 있는 고민 질문
1. `fixedDelay=100`이면 실제 실행 간격은 "ZPOPMIN + N개 SET 소요시간 + 100ms"이다. ZPOPMIN + 18 SET은 약 1ms. 실질 간격은 ~101ms. 이 정도 오차는 허용 가능한가?
2. 스케줄러가 "큐가 비어있을 때"에도 계속 ZPOPMIN을 호출해야 하는가? 불필요한 Redis 호출을 줄이려면?
3. 빅테크에서는 스케줄러 대신 "활성 유저 수 기반 Pull 모델"을 쓴다. 토큰 사용(주문 완료) 시 다음 유저를 입장시키는 방식. 이것의 장단점은?

---

### 1-4. 원자성: ZPOPMIN + 토큰 발급 사이의 갭

#### 문제 시나리오
```
1. ZPOPMIN → 18명을 큐에서 제거 ✅
2. (여기서 스케줄러 크래시) 💥
3. SET entry-token:{userId} → 실행 안 됨 ❌
→ 18명은 큐에서 사라졌지만 토큰도 없음 (유저 유실)
```

#### 해결 방안 비교

| 방안 | 설명 | 복잡도 | 우리 코드 참조 |
|------|------|--------|--------------|
| **Lua 스크립트 (추천)** | ZPOPMIN + SET을 하나의 Lua로 | 낮음 | `coupon_issue_request.lua` |
| Two-phase + 중간 상태 | "processing" 상태 도입, 실패 시 복구 | 높음 | — |
| 리스크 수용 + 모니터링 | 최대 18명 유실 허용, 알림으로 대응 | 낮음 | `OutboxRelayScheduler` 패턴 |

#### Lua 스크립트 원자성의 트레이드오프
- Lua 실행 중 Redis는 **다른 모든 명령을 블로킹**
- ZPOPMIN + 18개 SET ≈ 0.5~1ms → 무시할 수준
- 하지만 배치 크기가 1000이면? → 수십ms 블로킹 → 다른 클라이언트 영향

#### 깊이 있는 고민 질문
1. `coupon_issue_request.lua`는 2개 키를 다루는 간단한 스크립트다. 대기열 Lua는 1개 ZPOPMIN + N개 SET으로 키가 N+1개. Redis Cluster에서 이게 문제가 될 수 있는가? (힌트: hash slot)
2. Lua 스크립트의 최대 실행 시간은? (`lua-time-limit` 기본값 5초) 이것을 초과하면 어떤 일이 발생하는가?

---

## Phase 2: 엣지 케이스 & 장애 시나리오

> "동작하는 코드"와 "프로덕션에서 살아남는 코드"의 차이는 엣지 케이스 처리에서 갈린다.
> 각 시나리오를 "발생 확률 × 영향도"로 우선순위를 매겨보자.

### 2-1. Redis Master Failover (Split-Brain)

#### 현재 인프라 분석
- `RedisConfig.java`에서 `RedisStaticMasterReplicaConfiguration` 사용
- Sentinel이나 Cluster 없음 → 자동 failover 불가
- Master 다운 시 ZADD, ZPOPMIN 모두 실패

#### 시나리오별 영향

| 시나리오 | 영향 | 대응 |
|----------|------|------|
| Master 다운 | 큐 진입/처리 불가 | Graceful Degradation 전환 |
| Replica 다운 | 읽기 폴백 to Master | 큰 영향 없음 |
| Network Partition (Sentinel 사용 시) | Split-brain: 양쪽에서 ZPOPMIN | 중복 토큰 발급 가능 |

#### 깊이 있는 고민 질문
1. Sentinel을 추가하면 자동 failover는 되지만 split-brain 위험이 생긴다. 대기열에서 split-brain의 최악 시나리오는? (동일 유저에게 2번 토큰 발급, 2명이 같은 "슬롯"을 차지)
2. Redis 비동기 복제 특성상, failover 시 최근 수초 데이터가 유실된다. 대기열에서 이 데이터 유실은 어떤 의미인가?

---

### 2-2. 스케줄러 크래시 복구

#### 현재 코드의 보호 패턴

`OutboxRelayScheduler.java` 참조:
```java
// 건별 try/catch → 하나의 이벤트 실패가 전체 배치를 죽이지 않음
for (OutboxEvent event : events) {
    try {
        kafkaTemplate.send(...).get(5, TimeUnit.SECONDS);
        event.markPublished();
    } catch (Exception e) {
        event.markFailed();
        consecutiveFailures++;
    }
}
```

#### 대기열 스케줄러에 적용할 교훈
- ZPOPMIN으로 꺼낸 각 유저의 토큰 발급을 개별 try/catch로 감싸기
- 연속 실패 시 배치 중단 (circuit breaker 패턴)
- `@Scheduled` 메서드 전체를 try/catch로 감싸서 스케줄러 스레드 사망 방지

#### Spring `@Scheduled`의 함정
- 기본적으로 단일 스레드 (`TaskScheduler` 스레드 풀 크기 = 1)
- 스케줄러 메서드에서 uncaught exception이 발생하면 **해당 스레드가 죽고, 이후 스케줄 실행 안 됨**
- 반드시 최외곽 try/catch 필수

---

### 2-3. 중복 진입 방지

#### Sorted Set의 자연스러운 중복 방지
- `ZADD NX` → member가 이미 있으면 추가 안 됨 (반환값 0)
- userId를 member로 사용 → 동일 유저는 절대 중복 진입 불가

#### 비로그인 유저 문제
- 우리 시스템은 `AuthInterceptor`로 userId 확인 → 비로그인 대기열 진입 불가
- 하지만 한 사람이 여러 계정(loginId)으로 중복 진입하면? → 방지 불가 (비즈니스 정책 영역)

#### 깊이 있는 고민 질문
1. 유저가 대기열에 진입한 후, 자발적으로 이탈(취소)할 수 있어야 하는가? API가 필요한가?
2. 취소 후 재진입하면 다시 맨 뒤로 가야 하는가? (공정성 vs 사용자 편의)

---

### 2-4. Clock Skew (서버 간 시각 차이)

#### 문제
- score = `System.currentTimeMillis()` → 서버마다 시각이 다르면 순서가 뒤바뀜
- commerce-api 2대의 시각이 100ms 차이나면, 서버 B를 통해 진입한 유저가 서버 A보다 먼저 온 것처럼 보일 수 있음

#### 해결 방안

| 방안 | 설명 | 트레이드오프 |
|------|------|------------|
| Redis `TIME` 명령 사용 | Redis 서버 시각을 score로 사용 | 추가 RTT 1회 (μs 수준) |
| `INCR` 카운터 사용 | Redis 원자 카운터로 순번 부여 | 예상 대기 시간 계산 시 별도 로직 필요 |
| NTP 동기화 신뢰 | 서버 시각 차이를 ~ms 이내로 관리 | 운영 의존, 완벽 보장 불가 |

#### 깊이 있는 고민 질문
1. score에 timestamp 대신 카운터를 쓰면, "예상 대기 시간"은 어떻게 계산하는가?
   - `예상 대기 시간 = 내 순번 / 초당 처리량` → timestamp 없이도 가능
2. 빅테크에서는 보통 어떤 방식을 쓰는가? (대부분 NTP + 허용 오차 범위 설정)

---

### 2-5. 큐 오버플로우 (최대 수용)

#### 단순히 Redis 메모리만의 문제가 아니다
- 100만 명 큐: 메모리 ~100MB (문제없음)
- 100만 명 폴링: 초당 50만 ZRANK → Redis 처리 한계 초과

#### 계층적 완화 전략
```
순번 1~100:    1초마다 조회 (곧 입장)
순번 100~1000: 3초마다 조회
순번 1000+:    5초마다 조회
```
- 클라이언트에 `recommendedPollingInterval`을 응답에 포함시켜 동적 조절
- 또는 SSE로 전환하여 폴링 자체를 제거

#### 대기열 최대 인원 제한
- `ZCARD` 확인 후, 최대 인원 초과 시 429 또는 503 반환
- "대기열이 가득 찼습니다. 잠시 후 다시 시도해주세요" → Rate Limiting과 Queuing의 결합

---

### 2-6. Graceful Degradation (Redis 장애 시)

#### 세 가지 전략 비교

| 전략 | 서비스 연속성 | 안전성 | 복잡도 |
|------|-------------|--------|--------|
| **전면 차단** | ✗ | ★★★ | 낮음 |
| **대기열 우회 (bypass)** | ★★★ | ✗ (과부하 위험) | 낮음 |
| **Fallback 큐 (로컬/Kafka)** | ★★ | ★★ | 높음 |

#### 실무에서의 판단 기준
- **트래픽이 낮은 시간대**: bypass 허용 가능 (주문 TPS < 시스템 한계)
- **트래픽 폭증 시간대**: 전면 차단이 안전 (bypass하면 DB 터짐)
- 이 판단을 자동화하려면 "현재 주문 TPS" 모니터링이 전제

#### 우리 코드 참조
- `ProductCacheRepositoryImpl`의 패턴: Redis 장애 시 모든 예외를 catch → cache miss로 처리 (bypass)
- 대기열에서는 이 패턴을 무조건 따라하면 안 된다. 캐시 miss ≠ 대기열 bypass.

#### 깊이 있는 고민 질문
1. Redis가 복구되면, 이미 bypass로 진입한 유저와 큐에서 기다리던 유저 사이의 공정성은?
2. "Redis 장애 시 우리 서비스는 어떻게 동작해야 하는가?"를 사전에 문서화하는 것 자체가 핵심이다. 정답이 아닌 "합의된 결정"이 중요한 이유는?

---

## Phase 3: 프로덕션 수준 고려사항

> 빅테크 면접에서 자주 물어보는 "그래서 프로덕션에 배포하면 어떻게 운영하실 건가요?" 에 대답하기 위한 섹션

### 3-1. 모니터링 지표 설계

| 지표 | 수집 방법 | 경고 기준 | 왜 중요한가 |
|------|----------|----------|------------|
| **Queue Depth** | `ZCARD` (스케줄러 틱마다) | > 10,000: warn, > 100,000: critical | 유입 > 처리량 신호 |
| **Avg Wait Time** | (현재시각 - 가장 오래된 entry의 score) | > 5분: warn | 유저 체감 품질 |
| **P99 Wait Time** | 토큰 발급 시각 - 진입 시각 히스토그램 | > 15분: critical | 꼬리 지연 감지 |
| **Token Conversion Rate** | 사용된 토큰 / 발급된 토큰 | < 50%: investigate | TTL 또는 UX 문제 |
| **Token Expiry Rate** | 만료된 토큰 / 발급된 토큰 | > 30%: TTL 재검토 | 유저 이탈 지표 |
| **Scheduler Lag** | 마지막 성공적 ZPOPMIN 시각 | > 1초: warn, > 10초: critical | 스케줄러 사망 감지 |
| **Order API Latency** | P95 of POST /orders | > 2초: 배치 크기 줄이기 | 하류 부하 감지 |

#### 비즈니스 지표 vs 시스템 지표
- Queue Depth, Scheduler Lag → 시스템 지표 (엔지니어가 봄)
- Token Conversion Rate, Token Expiry Rate → **비즈니스 지표** (PM/기획자도 관심)
- 우리 코드의 `product_metrics` 테이블처럼, 큐 관련 비즈니스 메트릭도 별도 집계 고려

#### 우리 코드 참조
- `supports/monitoring/` — Prometheus + Micrometer 이미 설정됨
- Grafana 대시보드 (`docker/grafana/provisioning/dashboards/`) — 큐 메트릭용 패널 추가 가능

---

### 3-2. Thundering Herd 완화 전략

#### 문제 다시 정리
```
스케줄러: 매 100ms마다 18명에게 토큰 발급
→ 18명이 거의 동시에 POST /orders 호출
→ 18개 DB 커넥션 동시 점유
→ 비관적 락 경합 증가
```

#### 완화 방법 비교

| 방법 | 설명 | 효과 | 복잡도 |
|------|------|------|--------|
| **발급 간격 분산** | 100ms마다 18명 → 10ms마다 ~2명 | 높음 | 스케줄러 변경 |
| **토큰 Jitter** | 토큰 활성화 시점에 0~2초 랜덤 딜레이 | 중간 | 클라이언트 협력 필요 |
| **주문 API Rate Limit** | 토큰 있어도 초당 N건 제한 | 높음 (최종 안전장치) | 인터셉터 추가 |

#### 깊이 있는 고민 질문
1. 스케줄러 간격을 10ms로 줄이면 `@Scheduled(fixedDelay = 10)`이 되는데, 이렇게 빈번한 스케줄링이 Spring의 `TaskScheduler`에서 문제가 되지 않는가?
2. 주문 API에 Rate Limit을 걸면, 토큰을 가진 유저가 주문에 실패할 수 있다. 이 경우 재시도 로직은 어떻게 설계하는가?
3. 실제 Thundering Herd 문제를 로컬에서 재현하려면 어떤 테스트를 작성해야 하는가?

---

### 3-3. Anti-Bot & 어뷰징 방지

#### 우리 시스템의 현재 방어선
- `AuthInterceptor` → userId 기반 인증. 비로그인 유저는 큐 진입 불가.
- `ZADD NX` → 동일 userId 중복 진입 자동 방지

#### 추가 고려 사항

| 공격 벡터 | 대응 | 현재 상태 |
|----------|------|----------|
| 동일 유저 중복 진입 | `ZADD NX` | ✅ 자동 방지 |
| 다중 계정 (1인 N계정) | IP 기반 Rate Limiting | ❌ 미구현 |
| 봇 자동 진입 | CAPTCHA / Proof-of-work | ❌ 미구현 |
| 스크립트 폴링 | 폴링 빈도 제한 | ❌ 고려 필요 |

---

### 3-4. 우선순위 큐 (VIP Lane)

#### Sorted Set으로 자연스럽게 구현 가능
```
일반 유저: score = System.currentTimeMillis()
VIP 유저:  score = System.currentTimeMillis() - 60_000  (가상으로 1분 먼저 진입)
```
- Sorted Set은 score가 낮은 순서대로 정렬 → VIP가 자연스럽게 앞으로
- 비즈니스 정책 결정이지 기술적 문제가 아님

#### 깊이 있는 고민 질문
1. VIP 우선순위를 두면 일반 유저의 대기 시간이 늘어난다. "예상 대기 시간"을 VIP 비율을 반영해서 계산해야 하는가?
2. VIP가 전체의 30%일 때, 일반 유저에게 보여주는 "예상 대기 시간"은 실제보다 더 길어야 정확한가?

---

### 3-5. 부하 테스트 방법론

#### 테스트 시나리오 설계
```
Phase 1: Ramp-up (10초)
  → 0명 → 10,000명 동시에 POST /queue/enter
  → 검증: 큐 진입 레이턴시, Redis CPU, 앱 스레드 풀

Phase 2: Polling (5분)
  → 10,000명이 2초마다 GET /queue/position
  → 검증: Redis ops/sec, ZRANK 레이턴시

Phase 3: Token + Order (60초)
  → 스케줄러가 175명/초 토큰 발급
  → 토큰 보유자가 POST /orders 호출
  → 검증: DB 커넥션 풀, 주문 성공률, P95 레이턴시

Phase 4: Drain (큐 완전 소진까지)
  → 검증: 전체 end-to-end 소요 시간, 이탈률
```

#### 도구
- k6, Gatling, JMeter 중 선택
- 우리 `docker/infra-compose.yml` 환경에서 실행 가능

---

## Phase 4: 테스트 전략

> 우리 코드베이스의 기존 테스트 패턴을 최대한 재사용한다.

### 4-1. 동시성 테스트

#### 기존 패턴 참조
- `CouponIssueConcurrencyTest.java` — ExecutorService + CountDownLatch 패턴
- `OrderConcurrencyTest.java` — 재고 소진 동시성 검증

#### 대기열 동시성 테스트 시나리오

| 테스트 | 조건 | 기대 결과 |
|--------|------|----------|
| 100명 동시 진입 | 100 threads × POST /queue/enter | ZCARD = 100, 중복 없음 |
| 동일 유저 10회 진입 | 10 threads × 같은 userId | ZCARD = 1, 순번 변동 없음 |
| 스케줄러 + 진입 동시 | 진입과 ZPOPMIN 동시 실행 | 데이터 정합성 유지 |

### 4-2. 스케줄러 테스트

#### 핵심 원칙
- `@Scheduled` 어노테이션 자체를 테스트하지 않음
- 스케줄러 메서드를 **직접 호출**해서 로직만 검증

#### 기존 패턴 참조
- `PaymentRecoveryScheduler` 테스트 방식 참고
- Redis에 테스트 데이터 세팅 → 스케줄러 메서드 호출 → 결과 검증

#### 시간 기반 테스트의 어려움
- score에 `System.currentTimeMillis()` 직접 사용하면 테스트 비결정적
- `java.time.Clock` 주입 패턴 또는 고정 timestamp 사용 고려

### 4-3. 토큰 라이프사이클 테스트

```
1. 유저 큐 진입 (ZADD)
2. 스케줄러 실행 (ZPOPMIN → SET token EX 300)
3. 토큰으로 주문 API 호출 → 성공
4. 토큰 삭제 확인
5. 같은 토큰으로 재호출 → 실패
```

TTL 만료 테스트:
```
1. 토큰 발급 (EX 1 — 테스트용 짧은 TTL)
2. Thread.sleep(1500)
3. 만료된 토큰으로 주문 시도 → 실패
```

### 4-4. Testcontainers 활용

- `RedisTestContainersConfig.java` — 이미 Redis Testcontainer 설정 완료
- `RedisCleanUp.truncateAll()` — `@AfterEach`에서 Redis 초기화
- 주의: 테스트 컨테이너는 단일 Redis (master-replica 아님) → 복제 지연 테스트 불가

---

## Phase 5: 비교 분석

> "우리가 만드는 것"과 "실무에서 쓰는 것"을 비교하며 설계 안목을 높인다.

### 5-1. Gmarket 대기열 시스템

- 참조: https://dev.gmarket.com/46
- **핵심 인사이트**: "활성 유저 수"를 별도 추적. 활성 유저가 빠지면 그만큼 새 유저 입장.
- 우리 설계(스케줄러가 고정 N명/초)와의 차이: Gmarket은 demand-driven (수요 기반)
- 활성 유저 추적이 추가되지만, 더 정밀한 처리량 제어 가능

### 5-2. AWS Virtual Waiting Room

- API Gateway + SQS + DynamoDB + CloudFront
- 대기 페이지를 CDN에서 정적으로 서빙 → 백엔드 부하 제로
- 우리의 `GET /queue/position`도 Redis만 조회하면 되므로, DB 부하는 없어야 한다

### 5-3. Cloudflare Waiting Room

- CDN 레벨에서 대기열 처리 → 요청이 origin 서버에 도달하기 전에 차단
- 애플리케이션 코드 변경 없음
- 우리의 구현은 **애플리케이션 레벨** → 최대한의 제어력, 하지만 부하 관리 책임도 우리 몫

### 5-4. 핵심 비교표

| | 우리 구현 | Gmarket | AWS | Cloudflare |
|-|----------|---------|-----|------------|
| 큐 위치 | 앱 레벨 (Redis) | 앱 레벨 | 인프라 (SQS) | 엣지 (CDN) |
| 입장 기준 | 고정 N명/초 | 활성 유저 기반 | Lambda 처리 | CDN 설정 |
| 대기 UI | 앱 서빙 | 앱 서빙 | CDN 정적 | CDN 정적 |
| 순번 조회 | ZRANK Polling | Push | DynamoDB | Cloudflare 내부 |
| 확장성 | Redis 한계 | Redis 한계 | SQS 무제한 | Cloudflare 글로벌 |

---

## 학습 일정 제안

| 일차 | Phase | 주요 활동 | 예상 소요 |
|------|-------|----------|----------|
| Day 1 | Phase 0 | Back-pressure, Rate Limiting vs Queuing, Redis Sorted Set 실습 | 3~4시간 |
| Day 1 | Phase 1-1, 1-2 | 토큰 설계 결정, TTL 전략 정리 | 1~2시간 |
| Day 2 | Phase 1-3, 1-4 | 스케줄러 설계, 원자성 고민 | 2~3시간 |
| Day 2 | Phase 2-1 ~ 2-4 | Failover, 크래시 복구, 중복 진입, Clock Skew | 2~3시간 |
| Day 3 | Phase 2-5, 2-6 | 큐 오버플로우, Graceful Degradation | 1~2시간 |
| Day 3 | Phase 3 | 모니터링, Thundering Herd, Anti-Bot, 부하 테스트 | 2~3시간 |
| Day 3 | Phase 4 | 테스트 전략 정리 (코드 작성 전 읽기만) | 1~2시간 |
| Day 4 | Phase 5 | 비교 분석, Gmarket 아티클 정독 | 1~2시간 |
| Day 4 | 종합 | 내 설계 결정과 근거 문서화 | 2~3시간 |

---

## 검색 키워드 & 참고 자료 모음

| 주제 | 검색 키워드 / 자료 |
|------|-------------------|
| Redis Sorted Set 내부 | `"Redis sorted set skip list implementation"` |
| ZADD 플래그 상세 | Redis 공식 문서: `redis.io/commands/zadd` |
| 가상 대기열 설계 | `"Virtual Waiting Room Architecture"` — System Design Newsletter |
| Gmarket 대기열 | https://dev.gmarket.com/46 |
| Thundering Herd | `"thundering herd problem cache stampede mitigation"` |
| Spring Scheduler 내부 | `"Spring TaskScheduler thread pool single thread"` |
| Redis Lua 스크립팅 | `"Redis Lua scripting atomicity EVALSHA"` |
| SSE in Spring | Baeldung: `"spring server-sent events SseEmitter"` |
| Resilience4j Circuit Breaker | `"resilience4j circuit breaker spring boot"` |
| 부하 테스트 | `"k6 load testing redis queue scenario"` |

---

## 핵심 함정 체크리스트

구현 시작 전 반드시 확인:

- [ ] `ZADD NX` 사용 (NX 없으면 기존 유저 순번 변경됨)
- [ ] 순번 조회(`GET /queue/position`)는 Redis만 조회, DB 절대 안 됨
- [ ] 스케줄러 메서드에서 uncaught exception 방지 (최외곽 try/catch)
- [ ] 토큰 발급 개별 try/catch (한 건 실패가 배치 전체를 죽이면 안 됨)
- [ ] 큐 비어있을 때 ZPOPMIN 호출 결과 처리 (빈 set 반환)
- [ ] 배치 크기, TTL 등은 설정 외부화 (`@Value` 또는 `@ConfigurationProperties`)
- [ ] 주문 완료 후 토큰 삭제 잊지 않기 (TTL로 만료되지만, 명시적 삭제가 올바름)
- [ ] Replica에서 ZRANK 조회 시 복제 지연으로 순번이 1~2 정도 부정확할 수 있음 → 허용 가능한 트레이드오프로 문서화
