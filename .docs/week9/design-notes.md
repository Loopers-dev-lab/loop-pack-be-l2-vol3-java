# Week 9 설계 고민 포인트

---

## VIEW 이벤트 토픽 분리 여부

VIEW 이벤트를 기존 `catalog-events` 토픽에 합류시킬지, 별도 `view-events` 토픽으로 분리할지 결정이 필요했다. VIEW 볼륨이 LIKE보다 압도적으로 많기 때문에, 합류 시 파티션 부하 불균형이 발생할 수 있다는 점이 핵심 논점이었다.

**검토한 접근들:**

**접근 A: `catalog-events` 합류**
파티셔닝 키가 productId이므로, 인기 상품의 VIEW가 폭발하면 특정 파티션에 메시지가 집중된다. 구현은 단순하고 별도 컨슈머 관리 비용이 없다.

**접근 B: `view-events` 별도 토픽 분리**
볼륨 격리가 가능하고 VIEW 전용 컨슈머로 독립적으로 스케일링할 수 있다. 단, 토픽·컨슈머 그룹·오프셋·모니터링 항목 등 관리 대상이 늘어난다.

**결론**: 현재 트래픽 규모를 알 수 없는 상황에서 합류 방식으로 단순하게 시작한다. 부하 테스트 후 VIEW 볼륨이 LIKE의 10배 이상 차이나면 토픽 분리를 재검토한다.

---

## ORDER_CREATED payload 확장 시 배포 순서

ORDER_CREATED 메시지에 items 배열을 추가할 때, commerce-api(Producer)와 commerce-streamer(Consumer) 중 어느 쪽을 먼저 배포해야 하는지 결정이 필요했다. 배포 순서가 잘못되면 order_revenue 데이터가 영구적으로 유실될 수 있기 때문이다.

**검토한 접근들:**

**접근 A: Producer(commerce-api) 먼저 배포**
items 포함 메시지가 먼저 발행되는데, 구 Consumer가 이를 소비해 EventHandled에 "처리 완료"로 기록한다. 이후 새 Consumer가 배포되어도 해당 eventId는 이미 처리 완료로 skip → 그 사이 발생한 주문의 order_revenue 영구 누락.

**접근 B: Consumer(commerce-streamer) 먼저 배포**
새 Consumer는 items 파싱 코드를 갖추고 있고, items가 없는 기존 메시지는 `items == null` 체크로 gracefully 통과한다. 이후 Producer 배포 시 items 포함 메시지가 오면 정상 집계.

**결론**: Consumer First 원칙에 따라 commerce-streamer → commerce-api 순서로 배포한다. EventHandled 멱등성 테이블이 있으면 "처리 완료" 기록이 영구적이라 순서 실수의 비용이 크다.

> [글감] Kafka 스키마 확장 시 Consumer First 배포 원칙 — EventHandled 멱등성 테이블이 있을 때 배포 순서가 왜 데이터 유실을 결정하는가

---

## Weight 캐시 TTL

SyncScheduler(5초 주기)가 매 실행마다 weight를 조회하는데, 매번 DB를 치면 불필요한 부하가 생긴다. weight는 비즈니스 정책 수준으로 변경 빈도가 낮아 Redis Cache-Aside 패턴으로 캐싱한다.

**검토한 접근들:**

**짧은 TTL(60초):** 캐시 미스가 자주 발생해 DB 조회 횟수가 많아진다. SyncScheduler 5초 주기 기준으로 1분에 최대 12번 DB hit.

**긴 TTL(300초 이상):** 캐시 히트율이 높아 DB 부하가 적다. 관리자가 DB를 직접 수정해도 최대 TTL만큼 지연 반영된다. 관리자 API가 생기면 변경 시 캐시 evict로 즉시 반영 가능.

**결론**: TTL 300초(5분)로 결정. 관리자 API 없이 DB 직접 수정 시 최대 5분 지연은 랭킹 정확도에 치명적이지 않다. 관리자 weight 변경 API 구현 시 evict 로직 추가 예정.

---

## 랭킹 페이지 상품 정보 캐시 (미결)

랭킹 API 조회 시 Redis ZSET에서 productId 목록을 가져온 뒤 상품명·가격 등 상세 정보를 조합해야 한다. 조회가 빈번할 경우 매번 DB IN 쿼리를 치는 게 부담이 될 수 있다.

**검토한 접근들:**

**접근 A: IN 쿼리로 DB 직접 조회** — 구현 단순, 항상 최신 데이터, 캐시 무효화 불필요. 조회 부하는 상품 수에 비례.

**접근 B: Redis에 상품 정보 캐시(`product:{productId}` → JSON)** — DB 부하 감소. 관리자 상품 수정 시 캐시 evict 필요 (AFTER_COMMIT 이벤트 활용). 랭킹 페이지의 약간의 staleness는 PDP에서 정확한 정보를 보여주는 트레이드오프로 허용 가능.

**결론**: 미결. Must-Have에서는 IN 쿼리로 시작하고, 랭킹 API 부하 측정 후 캐시 도입 여부를 재검토한다.

---

## 삭제/비활성화된 상품의 ZSET 잔존 문제

랭킹 ZSET에는 productId를 멤버로 저장하는데, 상품이 삭제되거나 비활성화되어도 ZSET에서 자동으로 제거되지 않는다. ZCARD 기반 totalElements가 부풀려지고, 스케줄러가 dirty 행을 재처리해 삭제 상품을 ZADD로 다시 추가하는 문제가 생긴다.

**검토한 접근들:**

**접근 A: ZREVRANGE gap filling**
페이지 응답에서 null(삭제 상품) 발견 시 추가 ZREVRANGE로 보충하는 방식. 구현 가능하지만 삭제 상품이 연속으로 나타날 경우 Redis 왕복 횟수가 불확정이고 코드 복잡도가 높아진다.

**접근 B: DB 교차 검증으로 totalElements 보정**
ZSET 전체 멤버를 꺼내 DB COUNT로 유효 수를 구하는 방식. Redis 도입 목적(DB 부하 감소)과 역행하고 ZSET 크기에 비례해 IN 절이 무거워진다.

**접근 C: 삭제/비활성화 시 ZREM + dirty=false (채택)**
상품 삭제 이벤트를 `AFTER_COMMIT` ApplicationEvent로 발행해 두 곳을 정리한다. Redis ZSET(`ZREM`)은 즉시 반영, ranking_metrics는 dirty=false로 스케줄러 재ZADD를 방지. 행을 물리 삭제하지 않고 dirty=false만 하는 이유는 이력 보존과 상품 복구 시나리오 대응 때문이다.

**트랜잭션 경계 결정:**
같은 트랜잭션에 Redis 삭제를 넣으면 MySQL-Redis 간 원자성을 보장할 수 없다(Redis가 MySQL 트랜잭션에 참여 불가). 관심사 분리와 이원화 원자성 문제 회피를 위해 이벤트 기반 분리가 맞다.

**결론**: 접근 C. ZSET TTL이 2일이므로 오늘·어제 두 키에 ZREM 적용. 상품 삭제 빈도가 낮아 ZREVRANGE gap filling의 복잡성을 감수할 이유가 없다.

---

## weight 변경 시 기존 ZSET 점수 처리

weight가 바뀌었을 때 이미 ZSET에 들어간 오늘치 점수들을 어떻게 처리할지 결정이 필요했다. 캐시 만료만 기다리면 같은 날 상품별로 weight 기준이 섞이는 문제가 생긴다.

**검토한 접근들:**

**접근 A: Forward-only**
캐시 TTL(300s) 만료 이후 발생하는 dirty 행부터 새 weight 적용. 기존 ZSET 점수는 해당 상품에 새 이벤트가 올 때까지 구 weight 기반 유지. 구현 추가 없음. 단, 오늘 오전/오후 기준이 달라 같은 날 랭킹이 혼재된다.

**접근 B: 전체 재계산 (채택)**
weight 변경 시 오늘치 ranking_metrics 전체를 `dirty=true`로 일괄 마크 → 5초 후 스케줄러가 새 weight로 전부 재ZADD. 오늘 랭킹 전체에 일관된 weight 적용 보장.

```sql
UPDATE ranking_metrics SET dirty = true WHERE metrics_date = today
```

단점은 스케줄러 1회 실행에 평상시 대비 대량의 dirty 행(상품 수 × 활성 시간대)이 몰려 일시적 부하 스파이크 발생 가능. 단, weight 변경은 월 1회 이하의 운영 이벤트이고 스파이크는 일회성이며 랭킹 업데이트 지연만 발생할 뿐 데이터 유실은 없다.

**결론**: 접근 B. 부하 스파이크의 실제 크기(스케줄러 실행 시간, DB SUM 쿼리 시간, 랭킹 API P99 영향)는 부하 테스트 시 weight 변경 시나리오를 별도로 측정해 허용 범위를 확인한다.

---

## VIEW 이벤트 eventId 생성 전략

VIEW 이벤트는 Outbox 패턴을 사용하지 않기 때문에 LIKE/ORDER와 다른 방식으로 eventId를 생성해야 했다. EventHandled 멱등성 체크는 eventId 기반이라 어떤 방식으로든 고유한 ID가 필요하다.

**Outbox 이벤트 (LIKE, ORDER):**
`outbox_events` 테이블에 INSERT → `OutboxEvent.id = DB AUTO_INCREMENT` → 이 값을 `OutboxMessage.eventId`로 그대로 사용. DB가 단조 증가로 유일성을 보장하므로 충돌 불가능.

**VIEW 이벤트:**
DB에 저장되지 않으므로 DB 기반 ID를 쓸 수 없다 → `UUID.randomUUID().getLeastSignificantBits()`로 ~62비트 랜덤 Long 생성. Kafka at-least-once 재전송 시에는 동일한 `OutboxMessage` 객체가 재발행되어 같은 eventId → EventHandled 중복 방지 정상 작동. 서로 다른 두 VIEW 이벤트가 같은 eventId를 가질 확률은 46억 이벤트당 50% 수준으로 낮고, 충돌 시 두 번째 이벤트가 중복으로 간주되어 누락되지만 weight=0.1이라 허용 가능.

**결론**: 비대칭 설계는 의도적. "DB에 저장된다 = DB가 유일 ID를 보장한다"는 연결 고리에서 VIEW만 UUID를 쓰는 이유가 도출된다.

---

## Carry-Over 범위: 전체 ZSET vs Top N

콜드 스타트 해결을 위해 23:50에 오늘 ZSET 점수의 일부를 내일 키에 미리 기록할 때, 전체 상품을 순회할지 상위 N개만 할지 결정이 필요했다.

**전체 ZSET 순회:**
오늘 한 번이라도 이벤트가 발생한 모든 상품에 Carry-Over 적용. 상품 수가 많아질수록 23:50 스케줄러 실행 비용이 선형 증가하고, 대부분의 서비스가 top N만 표시하므로 101위 이하 상품의 UX 연속성은 사실상 의미가 없다.

**Top 100만 Carry-Over (채택):**
`ZREVRANGE 0 99`로 고정 크기를 가져와 score × 0.1로 내일 키에 ZADD. 복잡도 O(log N + 100)으로 고정되고 랭킹 API가 보여주는 범위(top 100)와 정확히 일치한다.

Carry-Over로 심어둔 score는 오늘 실제 이벤트가 쌓이면서 스케줄러의 ZADD(덮어쓰기)로 자연스럽게 오늘 점수로 대체된다. Carry-Over 값이 실제 활동에 밀려나는 구조라 멱등성 유지.

**Yesterday Fallback 대안 기각:** 오늘 ZSET이 비어있을 때 어제 키로 폴백하면 새 이벤트가 쌓여도 어제 랭킹이 표시되는 혼재가 발생. 오늘 날짜 기준 랭킹을 반환해야 하는 API 계약과 맞지 않음.

**결론**: Top 100 Carry-Over + score × 0.1 (설정값 외부화). Nice-to-Have로 미구현이나 설계 방향 확정.

---

## ZSET 동점(Tie) 처리 (인지된 미결)

log 정규화된 score는 연속 실수값이고, 각 이벤트 유형별 가중치(0.1/0.2/0.7)가 있어 조회수·좋아요·매출이 조금만 달라도 정확히 동점이 될 확률은 매우 낮다. 따라서 현재는 타이브레이커를 구현하지 않고, ZSET 동점 시 Redis 기본 동작(member 사전식 정렬)에 의존한다.

**검토한 접근들:**

**composite score 인코딩:** `score = basePoints + elapsedSec × 1e-9` — 스케줄러 실행 시각을 소수부에 인코딩. member는 productId 그대로 유지해 ZADD 덮어쓰기·ZREVRANK 단순 조회 모두 보장. 타이브레이커 최대값(0.0000864)이 basePoints 차이보다 훨씬 작아 정수부 침범 없음.

**composite member 형식:** `"{tiebreaker}-{productId}"` — 불변값(등록일 등)을 앞에 붙이면 ZADD 덮어쓰기는 가능. 단, ZREVRANK 조회 시 member 전체 문자열이 필요해 Product 테이블 의존이 발생.

**결론**: 미구현. 동점 발생 가능성이 낮아 Must-Have에서는 생략. 실제 동점 문제가 관측되면 composite score 인코딩(timestamp 방식)으로 도입한다.
