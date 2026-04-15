# Round 9 랭킹 — Risk Discovery (시나리오 문서 기반)

> **범위 (1문장)**: `commerce-api`의 일간 랭킹 **읽기 경로**(`RankingQueryService`, `GET /api/v1/rankings`, `POST .../rankings/snapshots`, 상세 `rankingRank`)와 시나리오 문서 [09-ranking-user-scenarios.md](./09-ranking-user-scenarios.md) §1–2에 대응하는 실패·경계를 식별한다.

---

## 1. 1차 질문 (범위 내)

1. **경계값**: `ZCARD`와 Hydration 후 실제 행 수가 다를 때, 페이징·`totalElements` 계약은 무엇을 보장하는가?
2. **실패·복구**: Redis `count`는 예외를 삼키지만 `ZREVRANGE`는 그대로인가? 부분 장애 시 사용자 응답은?
3. **시간**: 동일 사용자가 목록 API와 상세 API를 연달아 호출할 때 순위 필드가 달라도 오류로 처리하지 않는가?
4. **데이터 무결성**: ZSET member가 비숫자이거나, 상품·브랜드가 DB에 없을 때 행을 생략하는가, 예외인가?
5. **관측성**: Redis 장애로 인한 빈 랭킹과 “진짜 빈 ZSET”을 API 응답만으로 구분할 수 있는가?
6. **운영**: 재고 0 상품이 랭킹에 남는 것이 의도인가, 정책 공백인가?

## 2. 꼬리질문 (답이 코드/문서에서 확인됨)

1. `findReverseRangeWithScores`가 `RedisConnectionFailureException`을 던지면 — **현재는 상위로 전파**된다(`RankingQueryService`에 try 없음).
2. 상세 캐시 히트 시 — **`rankingRank`는 매 요청 `RankingQueryService` 조회**로 덧붙인다(`ProductFacade`).
3. 동점 시 순서 — **Redis member 문자열 규칙**을 따르며 숫자 ID의 사전순 ≠ 숫자 대소일 수 있다.

---

## 3. 리스크 레지스터

| ID | Risk | Trigger | Impact | Mitigation (test / code idea) |
|----|------|---------|--------|-------------------------------|
| R1 | Redis `count` 실패 시 빈 랭킹으로 위장 | 연결 끊김, `RedisSystemException` | I3: 인기 없음과 장애 구분 불가 | 단위: degraded 응답 고정; 운영: 메트릭/알람 |
| R2 | Redis `ZREVRANGE`만 실패 | count 성공 후 구간 조회 예외 | 500 등 비정상 응답 | 단위: 전파 여부 명세; 코드: 동일 try로 감싸거나 계약 문서화 |
| R3 | ZSET·DB 불일치로 행 수 < 페이지 크기 | orphan member, 삭제 상품, 브랜드 누락 | I2: 희소 페이지 | 단위·E2E: `totalElements`는 ZCARD 유지 |
| R4 | 비숫자 member | 잘못된 쓰기·수동 Redis 조작 | I2: 행 생략 | 단위: 스킵 동작 |
| R5 | 상세 `rankingRank` null의 원인 중복 | ZSET 미등록 vs Redis 순위 조회 실패 | I4: 클라이언트 해석 모호 | 계약: `meta` 확장 또는 로그 상관관계 |
| R6 | 품절 상품 노출 | 재고 필터 없음 | I5: 전환·정책 불일치 | E2E: 재고 0 노출 기록; 정책 도입 시 필터 테스트 |
| R7 | 오프셋+실시간 갱신 | 재요청 사이 ZSET 변경 | I4: 중복·누락 | 문서·E2E: 라이브 키 연속 페이지; **스냅샷 API**로 복제 ZSET 읽기 시 완화([09-ranking-user-scenarios.md](./09-ranking-user-scenarios.md) H6·H7, E-OFFSET-SHIFT) |
| R8 | 스냅샷 TTL·메모리 | 스냅샷 다건 생성 | Redis 키 수·메모리 증가 | TTL(`snapshot-ttl-seconds`)·클라이언트 재사용 가이드; 운영: `ranking:snap:*` 모니터링 |

---

## 4. 테스트 케이스 매핑

| TC ID | From risk | Level | Given | When | Then |
|-------|-----------|-------|-------|------|------|
| TC-R1-1 | R1 | unit | mock `count` → `RedisConnectionFailureException` | `loadPage` | `totalElements==0`, 빈 rows |
| TC-R1-2 | R1 | unit | mock `findOneBasedReverseRank` → `RedisSystemException` | `findOneBasedDailyRank` | `OptionalLong.empty()` |
| TC-R2-1 | R2 | unit | `count` OK, `findReverseRangeWithScores` 예외 | `loadPage` | 예외 전파(현 구현 명세) |
| TC-R3-1 | R3 | unit | ZSET 2건, DB 1건만 | `loadPage` | `totalElements==2`, rows 1건, rank=슬라이스 인덱스 |
| TC-R3-2 | R3 | e2e | Redis에 존재하지 않는 ID + 실제 상품 | `GET /rankings` | `totalElements`는 2, content 1 |
| TC-R4-1 | R4 | unit | member `"bad"` + 유효 ID | `loadPage` | 비숫자 슬롯 스킵 |
| TC-R3-3 | R3 | unit | 브랜드 맵에 없음 | `loadPage` | 행 생략 |
| TC-R6-1 | R6 | e2e | 재고 0 상품만 ZSET에 | `GET /rankings` | 200, 품절 상품 포함 |
| TC-R5-1 | R5 | unit | 캐시 히트 + mock 순위 | `getProductDetail` | `rankingRank`가 mock 값으로 채워짐 |

---

## 5. 리팩토링 힌트 (승인 후)

- `loadPage`의 Redis 읽기(`count`·`findReverseRangeWithScores`)를 **동일 degraded 정책**으로 묶을지, 아니면 **일부만 실패 시 5xx**를 계약으로 고정할지 결정.
- 장애와 빈 랭킹 구분: 응답 `meta` 플래그 또는 별도 헤더는 **API 계약 변경**이므로 클라이언트와 합의 후.

---

## 6. 시나리오 문서 교차 참조

| 시나리오 ID | 리스크 ID |
|---------------|-----------|
| E-REDIS-LIST | R1 |
| E-REDIS-RANK | R1, R5 |
| E-ZSET-ORPHAN, E-TOTAL-MISMATCH | R3 |
| E-PARSE | R4 |
| E-HYDR (브랜드) | R3 |
| E-STOCK-NOFILTER | R6 |
| E-OFFSET-SHIFT | R7 |
| H6·H7 (스냅샷) | R7(완화), R8 |
| E-REDIS-SNAP, E-SNAP-MISS, E-VAL-SNAP-ID, E-SNAP-CREATE | R1, R8 |
| (미문서화) ZREVRANGE 예외 | R2 |
