---
name: review-ranking
description: >
  Redis ZSET 기반 랭킹 시스템 리뷰. Kafka Consumer의 ZSET 점수 반영, 가중치 합산, 키 전략, TTL,
  Top-N API, 콜드 스타트 완화를 중심으로 리스크를 드러낸다.
allowed-tools: Read Grep Glob Agent
---

Redis ZSET 기반 랭킹 시스템 코드를 리뷰할 때 이 흐름을 따른다.

## 분석 준비

### Step 1: 설계 문서 확인

코드를 보기 전에 설계 의도와 기결정 사항을 파악한다.

- `.docs/subject/show_me_the_ranking.md` — 학습 주제와 설계 방향
- `.docs/quest/r9quest.md` — 구현 요구사항과 체크리스트
- `.docs/week9/qna.md` — 논의된 설계 결정과 trade-off

이미 검토/결정된 항목은 리뷰에서 제외한다. 결정과 구현이 불일치할 때만 지적한다.

기결정 사항 (qna.md 기반):

- 키: 일별 `ranking:day:yyyyMMdd`, 시간별 `ranking:hour:yyyyMMddHH`
- member: productId, score: 가중치 기반 합산 점수
- TTL: 일별 2일, 시간별 1일
- ZINCRBY로 점수 누적 (ZADD가 아님)
- 주문 score에 log10 적용하여 스케일 완화
- 배치 소비 시 모든 Consumer(Catalog, Order)에서 동일 상품 점수를 배치 단위로 합산하여 Redis 호출 최소화
- UNLIKED 이벤트는 subtractLikeScores로 점수 차감 반영
- 뷰 조합(상품 정보 Aggregation)은 Interfaces 계층(Controller)에서 담당
- ZREVRANK + 1 보정으로 1-based 순위 반환
- 동점 처리: Redis 기본 동작(사전순)에 맡김
- carry-over: 23시 50분 스케줄러, 전날 점수의 10%를 ZUNIONSTORE로 복사
- carry-over 멱등성: 분산 락 또는 Lua 스크립트로 중복 실행 방지
- 랭킹은 근사치로 충분 (at-least-once, 중복 허용)
- 10만 개 상품 ZSET은 약 10MB로 메모리 부담 없음
- ZSET에서 삭제된 상품은 필터링하지 않고 null 정보로 포함 (totalElements 일관성 유지)
- ORDER_CANCELLED 이벤트에는 items(price/quantity) 정보가 없어 점수 역산 불가 — 랭킹 차감하지 않는 것이 현재 구조의 제약
- Redis 장애 시 RDB fallback은 quest Must-Have 범위 밖 — 빈 결과 반환이 현재 구현

### Step 2: 코드 탐색

프로젝트 전체에서 랭킹 관련 코드를 탐색한다.

- Redis 설정 클래스 (RedisConfig, RedisTemplate 등)
- ZSET 연산 사용 코드 (ZINCRBY, ZREVRANGE, ZREVRANK, ZSCORE, ZUNIONSTORE)
- Kafka Consumer (ranking 관련 이벤트 소비 코드)
- 랭킹 점수 계산 로직 (가중치 합산, score 계산)
- 랭킹 조회 API (Controller, Service, Repository)
- 상품 상세 조회 API의 순위 포함 로직
- 콜드 스타트 스케줄러 (carry-over)
- 키 생성/관리 유틸 (날짜 기반 키 계산)

특정 파일만 떼어내어 판단하지 않는다. 이벤트 발행 → Kafka 소비 → ZSET 점수 반영 → API 조회 전체 파이프라인을 기준으로 분석한다.

### Step 3: 해당 여부 필터링

각 점검 항목에 대해 현재 구현에 해당 기능/패턴이 존재하는지 확인한다.

- 코드에 구현되지 않은 기능은 리스크로 지적하지 않는다
- 요구사항 문서에 명시된 범위 밖의 기능 부재는 리스크가 아니다
- 설계 문서에서 구현 예정으로 명시되었으나 코드에 누락된 경우에만 "미구현"으로 지적한다
- Step 1의 기결정 사항에 포함된 항목은 구현과 일치하면 재지적하지 않는다

---

## 1. Kafka Consumer → ZSET 점수 반영

- `ZINCRBY`를 사용하는지 (`ZADD`는 덮어쓰기)
- 이벤트 타입별 가중치 적용
  - view, like, order 각각 다른 가중치가 적용되는지
  - 가중치가 설정으로 관리되는지 (하드코딩이 아닌)
  - 가중치 합이 1인지 런타임에서 검증하는지
- 주문 score에 log 또는 정규화가 적용되는지
- 일별 키와 시간별 키 모두에 점수를 반영하는지
- 키 생성 시 소비 시점 vs 이벤트 발생 시점
  - 시간별 키에서 경계 오차의 비중이 커질 수 있음을 인지하고 있는지

---

## 2. ZSET 키 설계 및 TTL

- 키 포맷 일관성: 일별 `ranking:day:yyyyMMdd`, 시간별 `ranking:hour:yyyyMMddHH`
- 키 생성 로직이 중앙화되어 있는지 (여러 곳에서 문자열 직접 조합 금지)
- TTL: 일별 약 2일, 시간별 약 1일
- 키 생성 시점에 TTL이 설정되는지
- member에 productId(String)를 사용하는지

---

## 3. 랭킹 조회 API

- Top-N 페이징: `ZREVRANGE` + WITHSCORES로 score 내림차순 조회
- 페이징 인덱스 계산이 일관적인지
- 상품 정보 Aggregation이 Interfaces 계층(Controller)에서 이루어지는지
- 상품 조회 시 N+1 문제가 없는지 (IN 절 등)
- 개별 상품 순위: `ZREVRANK` + 1 보정, 랭킹에 없으면 null
- date 파라미터 기본값 오늘, 이전 날짜 조회 가능, 없는 키는 빈 결과

---

## 4. 콜드 스타트 완화 (Score Carry-Over)

- 스케줄러 실행 시점: 23시 50분 (자정이 아닌)
- ZUNIONSTORE로 전날 점수의 일부(WEIGHTS)를 복사하는지
- 멱등성/중복 실행 방지
  - 분산 락 사용 시: 락 획득/해제가 안전한지, 불필요한 방어 코드(hasKey 등)가 혼합되지 않는지
  - Lua 스크립트 사용 시: EXISTS + ZUNIONSTORE가 원자적으로 실행되는지
  - 두 방식을 혼합하지 않는지 (한 쪽의 장점을 살리지 못하는 구조)

---

## 5. 가중치 관리

- 저장 방식: 설정 파일(application.yml) 등 외부화
- view, like, order의 가중치 비율이 서비스 의도에 부합하는지

---

## 6. 아키텍처 계층 준수

CLAUDE.md 규칙에 따라 확인한다.

- Domain: Repository 인터페이스가 domain 패키지, Redis 의존성 미노출
- Infrastructure: Redis Repository 구현체, ZSET 연산 캡슐화
- Application: 조율자 역할만
- Interfaces: 뷰 조합(상품 정보 Aggregation)은 Controller에서, Response DTO `from()` 팩토리, Request에 Spring Validation

---

## 7. 이벤트 파이프라인 연결

- collector가 product_metrics upsert(기존)와 ZSET 반영(신규)을 모두 수행하는지
- at-least-once 방식이고 중복 반영을 허용하는지
- ZINCRBY 실패 시 전체 이벤트 처리를 중단시키지 않는지
- collector와 commerce-api가 동일한 키 포맷을 공유하는지

---

## 톤 & 스타일

- 코드 레벨 수정안을 직접 제시하지 않는다
- 설계를 비판하지 말고 리스크를 드러내는 톤을 유지한다
- 구현보다 파이프라인 전체의 신뢰성, 점수 정합성, 사용자 경험을 중심으로 분석한다
- 설정값에 대해서는 "왜 이 값을 선택했는가?"를 질문하는 방식으로 접근한다
- 랭킹은 근사치(approximate) 시스템이며, 은행 잔고 수준의 정합성은 불필요하다는 전제로 리뷰한다
- 설계 문서에서 이미 논의/결정된 trade-off는 존중한다
- "구현하지 않은 것"과 "구현해야 하는데 빠진 것"을 구분한다. 요구사항 문서에 없는 기능의 부재는 리스크가 아니다
- Redis 가정:
  - 싱글 스레드 — 단일 명령은 원자적, 복합 연산은 비원자적
  - ZINCRBY는 원자적이며 별도 동시��� 제어 불필요
  - TTL 만료는 정확한 시점을 보장하지 않음 (lazy + active expiration)
  - ZSET 동점은 member 사전순 정렬
