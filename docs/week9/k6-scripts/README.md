# R9 랭킹 k6 부하 테스트 스크립트

`docs/week9/k6-scenarios.md` 의 시나리오별 실행 스크립트.

## 디렉토리 구조

```
k6-scripts/
├── README.md              ← 이 파일
├── common/
│   ├── config.js          ← BASE_URL, thresholds, 기본값
│   ├── auth.js            ← X-Loopers-LoginId 헤더 helper
│   └── seed.js            ← 날짜/상품 ID 랜덤 helper
└── scenarios/
    ├── read-top-n.js        (A-1) 랭킹 Top-N 읽기 부하
    ├── product-detail.js    (A-2) 상품 상세 + dailyRank 오버헤드
    ├── write-pipeline.js    (A-3) 쓰기 파이프라인 스루풋 ⭐
    ├── hot-product.js       (A-4) Hot Product 경합
    ├── pagination-depth.js  (B-1) 페이지네이션 깊이
    └── mixed-workload.js    (B-4) 읽기+쓰기 혼합
```

## 사전 준비

```bash
# 인프라 기동
docker-compose -f ./docker/infra-compose.yml up -d

# 애플리케이션 기동 (별도 터미널)
./gradlew :apps:commerce-api:bootRun
./gradlew :apps:commerce-streamer:bootRun

# 테스트 데이터 seed — 상품 1000개 + 테스트 회원 1명 (아래 방식 중 택1)
# 1) HTTP 로 회원가입 + 어드민 API 로 상품 등록
# 2) data.sql 활용
# 3) seed SQL 스크립트 직접 실행
```

## 실행

```bash
# 기본
k6 run scenarios/read-top-n.js

# 환경변수 오버라이드
BASE_URL=http://localhost:8080 \
MAX_PRODUCT_ID=1000 \
TEST_LOGIN_ID=loadtest \
k6 run scenarios/write-pipeline.js

# JSON 요약 리포트
k6 run --summary-export=results/a1-$(date +%Y%m%d-%H%M).json \
       scenarios/read-top-n.js
```

## 환경 변수

| 변수 | 기본값 | 설명 |
|---|---|---|
| `BASE_URL` | `http://localhost:8080` | commerce-api 주소 |
| `MAX_PRODUCT_ID` | `1000` | 랜덤 선택할 상품 ID 최대값 |
| `TEST_LOGIN_ID` | `loadtest` | 좋아요/주문 등 인증 필요 요청의 로그인 ID |
| `TEST_DATE` | 오늘 (KST) | 랭킹 조회용 yyyyMMdd |
| `HOT_PRODUCT_ID` | `1` | A-4 에서 트래픽을 집중시킬 상품 ID |
| `HOT_RATIO` | `0.7` | A-4 에서 hot 상품 트래픽 비율 |

## 외부 지표 동시 관찰 (A-3, A-4 필수)

```bash
# Kafka consumer lag
watch -n 1 'docker exec kafka kafka-consumer-groups.sh \
    --bootstrap-server localhost:9092 --describe --group metrics-group'

# Redis OPS
watch -n 1 'docker exec redis-master redis-cli INFO stats | grep ops_per_sec'

# MySQL 상태
docker exec docker-mysql-1 mysqladmin -uroot -papplication extended-status -i 2 \
    | grep -E "Com_insert|Com_update|Threads_running|Innodb_row_lock_waits"
```

## 결과 저장 권장 경로

```
docs/week9/k6-results/
└── 2026-04-09/
    ├── a1-read-top-n.json
    ├── a3-write-pipeline.json
    └── notes.md
```
