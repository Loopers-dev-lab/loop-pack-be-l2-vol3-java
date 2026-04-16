# S2 배포·롤백 Runbook

> 2026-04-15 / feat/week10-batch
> 변경 범위: 스키마 + commerce-api (reader JOIN) + commerce-batch (PublishingRankWriter)
> SLA: 배포 창 < 30분, 롤백 창 < 15분

## 사전 체크리스트

- [ ] `docs/operation/s2-migration.sql` DBA 리뷰 완료 서명
- [ ] 스테이징에서 전체 회귀 테스트 PASS (batch + api)
- [ ] `NEW-T6` 부하 테스트 (S2 reader JOIN p95 < 10ms) PASS
- [ ] Publication 테이블 초기 seed 대상 periodKey 리스트 확보
- [ ] 배포 창 고지 (주간 Job 비실행 시간대 — 새벽 2~4시)
- [ ] 롤백 담당자 on-call 확인

## 배포 순서

### Step 1. 스키마 마이그레이션 (무중단)
```bash
mysql -h prod-db -u migrator -p loopers < docs/operation/s2-migration.sql
```
**검증 쿼리**:
```sql
SELECT COUNT(*) AS pub_rows FROM mv_product_rank_publication;
SELECT period_type, COUNT(*) FROM mv_product_rank_publication GROUP BY period_type;
```
기존 주간/월간 periodKey 수와 일치 확인. 0건이면 STOP — seed 실패.

### Step 2. commerce-api 배포
- 구 reader(`WHERE period_key=?` 단독)는 `version` 컬럼 추가에도 호환 — 기존 행(version=1)만 읽어도 무해
- 신 reader는 Publication JOIN 추가, published_version=1 기준으로 기존 데이터 그대로 서빙
- **검증**: `/api/v1/rankings?period=weekly&date=…` 호출 시 응답 이전과 동일

### Step 3. commerce-batch 배포
- 다음 주기 Job이 PublishingRankWriter로 version=2 발행
- **검증**: batch 로그에서 `mvPublishDurationMs` 기록 확인
- 한 번의 주간/월간 Job 성공 후 `mv_product_rank_publication.published_version`이 2로 증가 확인

### Step 4. 모니터링 (배포 후 24시간)
- `/api/v1/rankings` 500/빈응답 비율 < 0.1%
- `mv_product_rank_publication` row count 유지
- `mv_product_rank_weekly` row count: published + orphan. Cleanup Job 1회 후 published만.

## 롤백 순서

### 트리거 조건 (아래 중 하나)
- reader 빈 응답률 > 1%
- API p95 > 50ms (기존 대비 10배 이상)
- batch Job 연속 2회 FAILED
- `mv_product_rank_publication.updated_at` 최근 2h 없음 (publish 중단)

### Step R1. commerce-batch 이전 버전으로 롤백
- Writer가 PublishingRankWriter → AtomicMvRankWriter (구 Writer) 되는 이전 태그로 배포
- **주의**: 구 Writer는 DELETE+INSERT 단일 tx. 현 스키마 PK `(period_key, version, rank_no)` 하에서는 DELETE가 전 version 제거. 즉시 publication 엉클해짐 → **Step R2 스키마 원복과 한 세트로 실행**

### Step R2. 스키마 원복 (선택 — 구 reader가 version 컬럼에 무관하면 생략 가능)
```sql
-- version 컬럼 자체는 구 코드에 무해하나 PK 복원은 필요
ALTER TABLE mv_product_rank_weekly DROP PRIMARY KEY,
    ADD PRIMARY KEY (period_key, rank_no);
ALTER TABLE mv_product_rank_monthly DROP PRIMARY KEY,
    ADD PRIMARY KEY (period_key, rank_no);
-- version이 중복인 행 정리 (구 Writer는 version 모르므로 중복 발생 가능 — 실제로는 PK 재정의 전 버전별 집계 필요)
-- 최악의 경우: 모든 version>1 행 삭제 후 PK 복원
DELETE FROM mv_product_rank_weekly WHERE version > 1;
DELETE FROM mv_product_rank_monthly WHERE version > 1;
```

### Step R3. commerce-api 이전 버전으로 롤백
- 신 reader(JOIN publication) → 구 reader
- Publication 테이블은 보존 — 재도전 시 재사용

### Step R4. 검증
- `/api/v1/rankings` 응답 정상 복원
- 주간 배치 재실행 (`--date=…`) → 구 DELETE+INSERT 패턴으로 정상 갱신

## 파싱 가능한 알람 조건

| 조건 | 심각도 | 대응 |
|---|---|---|
| `publication.updated_at` 최근 2h 없음 | HIGH | batch 로그 확인, 재실행 |
| MV reader empty rate > 1% | HIGH | 롤백 트리거 |
| `mvPublishDurationMs p95 > 1000` | MEDIUM | Cleanup Job 수동 실행, Publication hotspot 확인 |
| Cleanup Job 미실행 24h+ | LOW | orphan version 누적 점검 |

## 배포 담당/책임

- DBA 마이그레이션: {이름}
- commerce-api 배포: {이름}
- commerce-batch 배포: {이름}
- 모니터링 on-call: {이름} — 배포 후 24h
