# 상품 조회 P95 3초 → 8ms: 인덱스가 해결한 것, 하지 못한 것, 캐시가 대신한 것

---

> **TL;DR**: 1000만 건 규모의 테이블에서 페이지네이션 조회(20건/페이지)가 100 rps 동시 요청 시 100% 실패하던 구조를, 인덱스 + 비정규화 + 멀티 레이어 캐시(L1 Caffeine + L2 Redis)로 P95 8ms / 에러율 0%까지 개선했다. 이 글은 그 과정에서 내린 판단들과, 왜 그렇게 결정했는지에 대한 기록이다.

---

## 문제를 처음 마주했을 때

상품 목록 조회 API에 좋아요 순 정렬을 추가하면서 문제가 시작됐다.

처음에는 단순하게 접근했다. `likes` 테이블에서 `GROUP BY product_id`로 좋아요 수를 세고, Java `Comparator`로 정렬하면 되지 않을까. 페이지네이션을 적용해도, 정렬 기준이 DB 밖(Java)에 있으니 **전체 데이터를 먼저 메모리에 올려야** 했다. 10만 건 정도에서는 2초 걸렸다. 느리긴 했지만 동작은 했다.

그런데 프로덕션 규모를 가정하고 데이터를 1000만 건으로 늘려보니 상황이 달라졌다. 단건 응답이 308초. K6로 100 rps를 걸면 99% 이상의 요청이 타임아웃으로 실패했다. 20건만 보여주면 되는 페이지네이션 요청인데, **매번 1000만 건 전체를 스캔하고 있었다.** 이건 "느린 서비스"가 아니라 **서비스 불능** 상태였다.

원인을 분석해보니 세 가지가 겹쳐 있었다.

1. 전체 상품을 메모리에 올려 정렬하고 있었다 (DB의 인덱스/LIMIT을 활용하지 못하고 Java에서 정렬)
2. 좋아요 수를 매 요청마다 COUNT 집계로 파생시키고 있었다
3. 동일한 쿼리가 반복되는데 캐시가 없었다

하나만 고쳐서는 안 될 것 같았다. 각각의 문제에 대해 어떤 순서로, 어떤 기준으로 접근할지 고민했다.

---

## 판단 1. 좋아요 수를 어디에 둘 것인가

가장 먼저 마주한 건 `likeCount`의 위치 문제였다.

사실 이전에 쓰기 경합을 줄이기 위해 `likeCount` 컬럼을 의도적으로 제거한 적이 있었다. 좋아요가 몰릴 때 같은 row에 대한 UPDATE 경합이 발생하니까, 차라리 `COUNT(*)`로 파생시키는 게 낫다고 판단했었다.

그런데 이번에 읽기 병목을 마주하면서, 같은 구조를 다른 눈으로 보게 됐다.

| 시점 | 우선순위 | 결정 |
|------|---------|------|
| 이전 | 쓰기 경합 해소 > 읽기 성능 | `likeCount` 제거, `COUNT(*)` 파생 |
| 현재 | 읽기 성능 > 쓰기 경합 | `likeCount` 재도입, atomic SQL로 경합 최소화 |

**트레이드오프의 축이 바뀌었다**고 느꼈다. 쓰기 경합은 atomic UPDATE(`SET like_count = like_count + 1`)로 줄일 수 있지만, 1000만 건에서 매번 `COUNT(*) GROUP BY`를 치는 건 구조적으로 한계가 있었다. 운영 환경을 다르게 가정하니, 문제의 무게중심이 달라졌다.

---

## 판단 2. 인덱스를 어떻게 설계할 것인가

비정규화만으로는 부족했다. 1000만 건에서 `ORDER BY like_count DESC`를 하면, 인덱스 없이는 전체 테이블 스캔 + filesort가 발생한다.

처음에는 `like_count`에 단일 인덱스를 걸었다. 그런데 브랜드 필터가 걸리면 인덱스를 타지 못했다. `WHERE brand_id = ? ORDER BY like_count DESC` — 이 조합은 단일 컬럼 인덱스로 커버되지 않는다.

결국 **유스케이스별로 복합 인덱스**를 설계했다.

```
idx_product_like_count        (like_count DESC, id DESC)           → 전체 + 좋아요순
idx_product_brand_like_count  (brand_id, like_count DESC, id DESC) → 브랜드 필터 + 좋아요순
idx_product_brand_price       (brand_id, price ASC, id ASC)        → 브랜드 필터 + 가격순
idx_likes_product_id          (product_id)                         → 좋아요 카운트 커버링
```

EXPLAIN으로 전후를 비교해보니 차이가 명확했다.

**AS-IS (인덱스 없음)**:
```
type: ALL | rows: 9,955,217 | Extra: Using filesort
```

**TO-BE (복합 인덱스 적용)**:
```
type: range | rows: 20 | Extra: Using index condition
```

스캔 행이 9,955,217 → 20으로 줄었다. 인덱스가 이미 정렬되어 있으므로 `LIMIT`만큼만 읽고 멈춘다.

---

## 판단 3. 인덱스만으로 충분한가

여기서 한 가지 착각할 뻔했다. EXPLAIN 결과가 극적으로 좋아지니까, "인덱스면 충분하지 않나?"라는 생각이 들었다. 이제 DB가 인덱스를 타서 20건만 빠르게 읽으니까 괜찮을 거라고.

그래서 **인덱스만 적용하고 캐시를 뺀 상태**로 100 rps 부하 테스트를 돌려봤다. 결과는 예상 밖이었다.

| 시나리오 | P95 | Error Rate | 처리량 |
|---------|-----|-----------|--------|
| 인덱스 없음 | 3.01s | 100% | 51 rps |
| **인덱스+비정규화, 캐시 없음** | **3.02s** | **99.65%** | **35 rps** |

인덱스를 걸었는데 오히려 처리량이 떨어졌다. 왜?

Grafana의 HikariCP 패널에서 답을 찾았다. **커넥션 40개가 전부 점유**되어 있었다. 인덱스가 단건 쿼리를 빠르게 하는 건 맞지만, 100 rps로 동시에 밀려오는 요청이 각각 DB 커넥션을 잡으면, 커넥션 풀이 포화되면서 뒤따르는 요청들이 대기 큐에 빠진다. 한 건의 쿼리가 1ms여도, **커넥션을 기다리는 시간이 3초**가 된다.

이 시점에서 깨달은 것: 캐시의 본질적 가치는 "빠른 응답"이 아니라 **"DB에 안 가게 하는 것"** 이다.

---

## 판단 4. 캐시 전략을 어떻게 설계할 것인가

캐시를 적용하기로 했다. 그런데 결정할 게 많았다.

### TTL은 어떻게?

- 상품 상세: TTL 10분. 상품 정보는 자주 바뀌지 않고, 변경 시 명시적으로 evict한다.
- 상품 목록: TTL 5분. 목록은 새 상품 등록, 좋아요 변동 등으로 상대적으로 자주 바뀐다.

처음에는 둘 다 10분으로 뒀는데, 목록 캐시가 너무 오래 유지되면 "방금 좋아요 눌렀는데 순위가 안 바뀌어요" 같은 불만이 생길 것 같았다. 결국 목록의 TTL을 짧게 조정했다.

### 무효화 전략은?

상품 상세는 단건이니까 `evict(productId)`로 충분하다. 문제는 목록이었다. 브랜드, 정렬, 페이지 조합으로 캐시 키가 무수히 많다.

처음에는 패턴 매칭 삭제(`SCAN`)를 고려했다. 하지만 키가 수천 개일 때 O(N) 순회는 Redis에 부담이 된다. 결국 **버전 기반 무효화**를 선택했다.

```
캐시 키: product:list:v{version}:brand:3:sort:likeCount:page:0:size:20
무효화: INCR product:list:version → 기존 키는 자연스럽게 miss
```

O(1)이고, 기존 키는 TTL이 만료되면 알아서 정리된다. 다만 무효화 시 모든 목록 캐시가 한꺼번에 miss되는 thundering herd 가능성은 있다. 현재 규모에서는 DB가 충분히 감당할 수 있다고 판단했지만, 트래픽이 10배로 늘면 재고해야 할 지점이다.

### Redis 장애 시에는?

try-catch로 감싸서 DB 직접 조회로 폴백한다. 캐시는 **최적화 계층이지 필수 의존이 아니다**. 이 원칙은 처음부터 정해두고 싶었다.

---

## 판단 5. 왜 Redis만으로 부족하다고 생각했는가

Redis 캐시만 적용한 상태에서 P95가 10ms, 에러율 0%까지 떨어졌다. 충분히 만족할 만한 수치다.

그런데 한 가지 마음에 걸렸다. 모든 캐시 조회가 Redis 네트워크 왕복을 거치고 있었다. Docker 환경에서 Redis가 localhost라 1ms 미만이지만, 실 운영에서 Redis가 별도 서버에 있으면 왕복 1~3ms가 추가된다. 수천 RPS에서 그 차이가 Tomcat 스레드 점유 시간으로 누적되면?

인기 상품 상위 0.5%만 JVM 로컬 캐시(Caffeine)에 올리면 네트워크 비용 자체를 없앨 수 있다. 메모리는 ~1.5MB. 무시 가능한 비용이다.

```
GET:   L1(Caffeine) hit → 반환 (μs)
       L1 miss → L2(Redis) hit → L1 backfill → 반환 (ms)
       양쪽 miss → DB 조회 → L2 저장 → L1 저장
```

**벤치마크 결과 (동일 조건: 100 rps, 1분, 1000만 건)**:

| 시나리오 | P50 | P95 | Error Rate | 처리량 |
|---------|-----|-----|-----------|--------|
| L2 Redis Only | 6.47ms | 10.19ms | 0% | 100 rps |
| **L1+L2 Multi-Layer** | **4.76ms** | **8.04ms** | **0%** | **100 rps** |

수치 차이는 2ms다. 하지만 이건 Redis가 localhost인 Docker 환경의 결과다. 실 운영에서는 이 차이가 더 벌어질 거라고 예상한다.

---

## 판단 6. 캐시 구현체를 인터페이스로 분리한 이유

멀티 레이어 캐시를 만들면서 구조적인 문제를 발견했다.

기존 `ProductCacheService`는 application 레이어의 concrete class인데, `RedisTemplate`을 직접 의존하고 있었다. Repository는 DIP를 잘 지키고 있었는데, 캐시만 예외였다.

```
// Repository — DIP 준수
ProductFacade → ProductRepository (domain interface) ← ProductRepositoryImpl (infrastructure)

// 캐시 — DIP 위반
ProductFacade → ProductCacheService (concrete, RedisTemplate 직접 의존)
```

처음에는 "캐시니까 그냥 이대로 써도 되지 않을까" 싶었다. 그런데 테스트를 작성하면서 문제를 체감했다. Fake 객체를 만들려면 `extends ProductCacheService`에서 `super(null, null, null)`을 호출해야 했다. 생성자 파라미터가 바뀔 때마다 모든 Fake가 깨진다.

L1, L2, MultiLayer 세 개의 구현체가 필요한 시점에서, 인터페이스 분리는 선택이 아니라 필수였다.

```
ProductCachePort (application, interface)
  ├── CaffeineProductCacheAdapter (infrastructure, L1)
  ├── RedisProductCacheAdapter (infrastructure, L2)
  └── MultiLayerProductCacheAdapter (infrastructure, @Primary, L1+L2)
```

호출부(`ProductFacade`, `LikeController`)는 타입과 변수명만 교체하면 됐다. 메서드 시그니처가 동일하니까.

---

## 검증 — 어떻게 측정했는가

"좋아졌다"를 체감하려면 수치가 필요했다. 그리고 **각 계층이 얼마나 기여하는지** 분리해서 보고 싶었다.

### 환경 구성

- **MySQL** (Docker): 상품 1000만 건, 브랜드 500개, 회원 5000명, 좋아요 95만 건
- **Redis** (Docker): Master-Replica 구성
- **K6**: 100 rps, 1분, constant-arrival-rate. 페이지 0~4 × 정렬 3종 = 15개 조합을 랜덤 요청 (각 요청당 20건 페이지네이션)
- **Prometheus + Grafana**: P95, RPS, Error Rate, HikariCP, JVM Heap 모니터링

### 비교군 설계

각 최적화 계층의 기여분을 분리하기 위해 A/B 비교 엔드포인트를 추가했다.

| 엔드포인트 | 인덱스 | 비정규화 | 캐시 | 증명하는 것 |
|-----------|--------|---------|------|-----------|
| `/products/no-optimization` | X | X | X | 기준선 — 왜 최적화가 필요한가 |
| `/products/no-cache` | O | O | X | 인덱스만으로 충분한가 |
| `/products` (L2) | O | O | L2 | 캐시 하나로 얼마나 달라지는가 |
| `/products` (L1+L2) | O | O | L1+L2 | 로컬 캐시가 추가로 줄여주는 것 |

### Grafana에서 읽은 것

![](blob:https://velog.io/aed83a2a-5306-4f43-a971-1f887d09dc39)
*P95 Response Time + P50/RPS*

![](https://velog.velcdn.com/images/sukhee/post/97a9acb2-04c2-4f57-8aac-de800887b6ab/image.png)
*P50 + RPS + Error Rate + HikariCP*

![](https://velog.velcdn.com/images/sukhee/post/2630fb37-9da0-4bc2-a871-815c6f9181e9/image.png)
*Error Rate + HikariCP + JVM Heap + Total Requests*

숫자 테이블보다 Grafana가 더 직관적으로 보여주는 것들이 있었다.

**HikariCP 패널이 진짜 병목을 드러냈다.** 비캐시 구간에서 40개(Max Pool) 전부 점유, 캐시 구간에서 1~2개. 느린 쿼리 하나가 문제가 아니라, 느린 쿼리가 커넥션을 물고 놓지 않으면 뒤따르는 모든 요청이 대기에 빠진다. 이걸 보고 나서 "캐시는 속도 최적화"라는 생각이 바뀌었다. **캐시는 가용성 확보**다.

**RPS 패널에서 서비스 용량의 차이가 보였다.** 비캐시는 목표 100 rps에 실제 35~51 rps만 처리하고 나머지는 유실됐다. 캐시를 적용하니 100 rps를 안정적으로 소화했다. 같은 하드웨어에서 캐시 유무가 처리 가능 트래픽을 2~3배 갈랐다.

**L2 → L1+L2 차이는 환경의 한계를 알고 읽어야 한다.** 10ms → 8ms, 2ms 차이. Redis가 localhost여서 네트워크 latency가 거의 0인 Docker 환경이기 때문이다. 실 운영에서 Redis가 별도 서버에 있으면 이 차이는 더 벌어질 것이다.

---

## 시행착오

검증 과정이 순탄하지는 않았다.

**Docker `/tmp` 디스크 포화.** No Optimization 테스트를 먼저 돌리면, 1000만 건 전체 풀스캔 + filesort가 MySQL 임시 파일을 대량 생성해서 Docker VM 디스크를 채웠다. 이후 돌리는 캐시 테스트도 캐시 미스 시 DB 쿼리가 `No space left on device`로 실패하며 연쇄적으로 무너졌다. MySQL `sort_buffer_size`를 8MB로 올리고, 테스트 간 MySQL 컨테이너를 재시작해서 해결했다.

**앱 재시작 시 1000만 건 데이터 유실.** local 프로필의 `ddl-auto: create` 때문에, 앱을 재시작하면 테이블이 재생성됐다. Stored procedure로 30분 걸려 시딩한 데이터가 순식간에 날아가는 경험을 했다... `--spring.jpa.hibernate.ddl-auto=none`을 JVM 인자로 전달해서 해결했는데, 한 번 당하기 전에는 떠올리기 어려운 종류의 실수였다.

---

## 돌아보며

이번 작업에서 가장 크게 배운 건, **같은 구조도 문제의 맥락이 바뀌면 다시 판단해야 한다**는 점이다. `likeCount` 비정규화가 대표적이다. 쓰기 경합 관점에서는 제거하는 게 맞았지만, 읽기 병목 관점에서는 다시 도입하는 게 맞았다. "이전에 결정한 거니까"라고 고집하지 않고, 현재의 문제에 맞게 재판단하는 게 중요했다.

그리고 인덱스만 믿고 캐시를 빼봤을 때 오히려 더 느려진 경험이 인상적이었다. EXPLAIN의 rows가 20이어도, 100 rps에서 커넥션 풀이 포화되면 의미가 없다. **단건 성능과 동시성 하의 성능은 완전히 다른 문제**라는 걸 체감했다.

아직 아쉬운 부분도 있다. 다중 서버 환경에서 L1 캐시의 일관성 문제는 짧은 TTL로 회피하고 있을 뿐, 근본적으로 해결하지는 않았다. 트래픽이 더 커지면 Redis Pub/Sub 기반의 L1 무효화를 추가해야 할 것 같다. 그건 다음 과제로 남겨둔다.