# 인덱스 튜닝 블로그 리서치 — 실무 조회 쿼리 개선기

> 원본: ALLSP_STD > ctrLeg101 (계약대장관리) 메인 조회 쿼리
> 보안을 위해 테이블/컬럼명을 치환하여 블로그에 사용

---

## 1. 치환 매핑표

블로그 공개 시 아래 매핑으로 치환한다. **원본 이름은 절대 블로그에 노출하지 않는다.**

### 테이블 치환

| 원본 테이블 | 치환 테이블 | 설명 |
|------------|-----------|------|
| CTR_CTR_LEDG | `contract_ledger` | 계약대장 마스터 |
| CTR_CTR_CLT | `contract_partner` | 계약 거래처 |
| ACC_CLT | `partner_master` | 거래처 기본정보 |
| CTR_CTR_FLAW | `contract_defect` | 하자관리 |
| CTR_CTR_CHG | `contract_change` | 변경이력 |
| CTR_DFRCMPST | `contract_penalty` | 지연배상금 |
| CTR_CTR_ATAC | `contract_seizure` | 압류현황 |
| CTR_CTR_SUBCNTR | `contract_subcontract` | 하도급 |
| SYS_DEPT / VW_USE_DEPT | `department` | 부서 |

### 주요 컬럼 치환

| 원본 컬럼 | 치환 컬럼 | 설명 |
|----------|----------|------|
| CTR_LEDG_NO | `contract_no` | 계약대장번호 (PK) |
| ACC_YY | `fiscal_year` | 회계연도 |
| CTR_NM | `contract_name` | 계약명 |
| CTR_DAT | `contract_date` | 계약일자 |
| CTR_AMT | `contract_amount` | 계약금액 |
| CTR_KND | `contract_type` | 계약종류 코드 |
| CTR_FOM | `contract_form` | 계약형태 |
| COMPL_DAT | `completion_date` | 준공일자 |
| CLT_CD | `partner_code` | 거래처 코드 |
| CLT_NM | `partner_name` | 거래처명 |
| CLT_SEQ | `partner_seq` | 거래처 순번 |
| REP_CLT_YN | `is_primary` | 대표거래처 여부 |
| BZR_REGNO | `biz_reg_no` | 사업자등록번호 |
| CTR_MNG_DEPT | `manage_dept` | 관리부서 |
| CTR_REQTER_NM | `requester_name` | 요청자명 |
| PCUR_DIV | `procurement_div` | 조달구분 |
| PCUR_NO | `procurement_no` | 조달번호 |
| FLAW_GRNTY_ENDDD | `defect_warranty_end` | 하자보증 종료일 |

---

## 2. 현재 쿼리 구조 분석 (AS-IS)

### 2.1 메인 조회 쿼리 구조도

```
contract_ledger (LEG)  ← 메인 테이블
  ├─ LEFT JOIN ① 거래처 건수 서브쿼리 (COUNT + GROUP BY)
  ├─ LEFT JOIN ② 1순번 거래처 정보 (partner_seq = 1)
  │     └─ INNER JOIN partner_master (거래처 기본정보)
  ├─ LEFT JOIN ③ 대표 거래처 정보 (is_primary = 'Y')
  │     └─ INNER JOIN partner_master (거래처 기본정보)
  └─ LEFT JOIN ④ 하자보증 종료일 집계 (GROUP BY)
```

### 2.2 동적 WHERE 조건 (MyBatis `<if>`)

```sql
WHERE fiscal_year = #{searchFiscalYear}          -- ★ 필수조건 (항상)
  [AND contract_date >= #{searchDateStart}]       -- 선택: 계약일자 범위
  [AND contract_date <= #{searchDateEnd}]
  [AND contract_no = #{searchContractNo}]         -- 선택: 계약번호 정확매칭
  [AND contract_type = #{searchContractType}]     -- 선택: 계약종류
  [AND contract_name LIKE '%keyword%']            -- 선택: 계약명 LIKE
  [AND procurement_no = #{searchProcNo}]          -- 선택: 조달번호
  [AND procurement_div = #{searchProcDiv}]        -- 선택: 조달구분
  [AND requester_name LIKE '%keyword%']           -- 선택: 요청자 LIKE
  [AND manage_dept = #{searchMngDept}]            -- 선택: 관리부서
  [AND 하자보증만료 임박 조건]                       -- 선택: 날짜 계산
  [AND EXISTS (거래처명/사업자번호 검색 서브쿼리)]    -- 선택: 거래처 검색
```

### 2.3 핵심 병목 포인트 식별

| # | 병목 후보 | 이유 |
|---|----------|------|
| ① | **4개의 LEFT JOIN** (그 중 2개가 서브쿼리) | 서브쿼리가 전체 테이블을 GROUP BY하므로, contract_partner 데이터가 많을수록 비용 증가 |
| ② | **LIKE '%keyword%'** (양쪽 와일드카드) | 인덱스 사용 불가. Full Table Scan 유발 |
| ③ | **EXISTS 서브쿼리 (거래처 검색)** | 외부 쿼리 행마다 서브쿼리 실행. contract_partner 테이블 반복 스캔 |
| ④ | **DATE 함수 사용 (하자보증만료 조건)** | `DATE_SUB(DATE_ADD(...))` → 인덱스 미작동 |
| ⑤ | **필수조건이 fiscal_year 단 1개** | 파티셔닝이나 인덱스 없으면 해당 연도 전체 스캔 |

---

## 3. 인덱스 설계 전략 (TO-BE)

### 3.1 쿼리 패턴별 인덱스 후보

현재 쿼리의 WHERE 조건 조합을 빈도순으로 정리하면:

```
[패턴 A] 가장 빈번: fiscal_year만으로 조회 (전체 목록)
[패턴 B] 빈번:     fiscal_year + contract_type
[패턴 C] 빈번:     fiscal_year + contract_date 범위
[패턴 D] 가끔:     fiscal_year + contract_name LIKE
[패턴 E] 가끔:     fiscal_year + manage_dept
[패턴 F] 드묾:     fiscal_year + 거래처 EXISTS
```

### 3.2 인덱스 설계안

```sql
-- ■ 인덱스 1: 메인 테이블 기본 조회 (패턴 A, B, C 커버)
CREATE INDEX idx_ledger_year_type_date
ON contract_ledger(fiscal_year, contract_type, contract_date);

-- ■ 인덱스 2: 관리부서 필터 (패턴 E)
CREATE INDEX idx_ledger_year_dept
ON contract_ledger(fiscal_year, manage_dept);

-- ■ 인덱스 3: 거래처 서브쿼리 성능 개선
CREATE INDEX idx_partner_contract_no
ON contract_partner(contract_no, partner_seq);
-- → LEFT JOIN ①②③ 모두 contract_no로 조인하므로 필수

-- ■ 인덱스 4: 하자관리 GROUP BY 최적화
CREATE INDEX idx_defect_contract_no
ON contract_defect(contract_no, defect_warranty_end);
-- → LEFT JOIN ④ GROUP BY 최적화
```

### 3.3 LIKE 검색 한계와 대안

```
문제: contract_name LIKE '%keyword%' → 인덱스 불가

대안 1: 앞쪽 와일드카드 제거 (LIKE 'keyword%') → 비즈니스 요건상 어려움
대안 2: Full-Text Index 적용 → MySQL 5.7+ 지원, 한글 형태소 분석 한계
대안 3: 별도 검색 인덱스 테이블 (역인덱스) → 오버엔지니어링 가능성
대안 4: 현실적 선택 — LIKE는 그대로 두고, 다른 조건으로 rows를 먼저 줄인 후 LIKE 필터

→ 블로그 포인트: "인덱스로 해결할 수 없는 것"을 인정하는 것도 설계의 일부
```

### 3.4 DATE 함수 문제 해결

```sql
-- AS-IS (인덱스 미작동)
WHERE CURDATE() >= DATE_SUB(DATE_ADD(completion_date, INTERVAL 1 YEAR), INTERVAL 15 DAY)

-- TO-BE (인덱스 작동 가능)
WHERE completion_date >= DATE_SUB(DATE_SUB(CURDATE(), INTERVAL 1 YEAR), INTERVAL -15 DAY)
-- → completion_date 컬럼에 직접 비교하도록 변환
-- → completion_date에 인덱스가 있으면 Range Scan 가능
```

---

## 4. EXPLAIN 분석 시나리오 (블로그용)

### 4.1 Before: 인덱스 없는 상태

```
예상 EXPLAIN 결과:
┌─────────┬────────┬──────┬───────────────┬──────────┐
│  table  │  type  │ rows │     extra     │   key    │
├─────────┼────────┼──────┼───────────────┼──────────┤
│ LEG     │ ALL    │ 50K  │ Using where   │ NULL     │ ← Full Table Scan
│ CCC     │ ALL    │ 80K  │ Using temp    │ NULL     │ ← 서브쿼리 전체스캔
│ CLT_INF │ ALL    │ 80K  │ Using where   │ NULL     │ ← 거래처 전체스캔
│ CTF     │ ALL    │ 10K  │ Using temp    │ NULL     │ ← 하자 전체스캔
└─────────┴────────┴──────┴───────────────┴──────────┘
```

### 4.2 After: 인덱스 적용 후

```
예상 EXPLAIN 결과:
┌─────────┬────────┬──────┬───────────────┬──────────────────────────┐
│  table  │  type  │ rows │     extra     │           key            │
├─────────┼────────┼──────┼───────────────┼──────────────────────────┤
│ LEG     │ ref    │ 500  │ Using where   │ idx_ledger_year_type_date│ ← Index Scan
│ CCC     │ ref    │   3  │ Using index   │ idx_partner_contract_no  │ ← Covering Index
│ CLT_INF │ ref    │   1  │               │ idx_partner_contract_no  │
│ CTF     │ ref    │   2  │ Using index   │ idx_defect_contract_no   │ ← Covering Index
└─────────┴────────┴──────┴───────────────┴──────────────────────────┘
```

---

## 5. 블로그 글 구성안

### 제목 후보

1. **"계약 5만 건을 조회하는데 8초 — 인덱스 하나로 0.3초가 된 이야기"**
2. **"EXPLAIN 하나면 충분하다 — 실무 조회 쿼리 튜닝 일지"**
3. **"인덱스를 걸기 전에 쿼리를 먼저 읽어라"**

### 목차 구성

```
1. 들어가며: 왜 이 쿼리를 튜닝해야 했는가
   - 상황 설명: N개의 JOIN + 동적 WHERE 조건을 가진 대장 조회 화면
   - 체감 문제: 목록 조회 시 수 초 이상 대기

2. 쿼리 구조 파악: 무엇이 느린 건지 모르면 고칠 수 없다
   - 쿼리 구조도 (JOIN 관계 시각화)
   - 동적 WHERE 조건 패턴 정리
   - "어디가 문제인지 짐작이 가지 않았다"

3. EXPLAIN으로 병목 찾기
   - EXPLAIN 결과 캡처 (Before)
   - type=ALL, rows, extra 해석
   - "범인은 서브쿼리 LEFT JOIN이었다"

4. 인덱스 설계: 어떤 기준으로 무엇을 걸었는가
   - 쿼리 패턴 빈도 분석 → 인덱스 우선순위
   - 복합 인덱스 컬럼 순서 결정 과정
   - 레인지 연산(날짜 범위)은 맨 뒤에 배치한 이유
   - 커버링 인덱스 기회 발굴

5. 인덱스로 해결할 수 없는 것들
   - LIKE '%keyword%'는 왜 인덱스를 못 타는가
   - DATE 함수를 컬럼에 걸면 인덱스가 죽는 이유
   - "모든 것을 인덱스로 해결하려 하지 마라"

6. 적용 결과: EXPLAIN 비교 (Before vs After)
   - rows 감소율
   - type 변화 (ALL → ref)
   - extra에서 Using filesort / Using temporary 제거 여부
   - 실제 응답시간 비교

7. 회고: 인덱스 설계에서 배운 것
   - 멘토링에서 배운 원칙과 실제 적용의 gap
   - "인덱스 개수가 아니라 쿼리 패턴이 기준이다"
   - 트레이드오프: 인덱스 추가 → 쓰기 부담 증가
```

---

## 6. 블로그에 넣을 핵심 비교 자료 (TODO)

실제 EXPLAIN을 돌려서 아래 수치를 채워야 한다:

| 항목 | Before | After | 개선율 |
|------|--------|-------|--------|
| rows (메인 테이블) | ? | ? | ? |
| type (메인 테이블) | ALL? | ref? | - |
| extra 경고 | Using filesort? | 제거? | - |
| 실제 응답시간 | ?초 | ?초 | ?% |
| 서브쿼리 스캔 rows | ? | ? | ? |

---

## 7. 멘토링 연결 포인트

블로그에 자연스럽게 녹일 수 있는 멘토링 인사이트:

| 멘토링 내용 | 블로그 활용 |
|------------|-----------|
| "실제 SELECT 쿼리 패턴에 맞춰 설계하라" | 동적 WHERE 조건 패턴 분석 과정에 인용 |
| "레인지 연산 이후 컬럼은 인덱스 미작동" | 복합 인덱스 순서 결정 근거로 활용 |
| "DB 함수 사용 시 인덱스 미작동" | DATE 함수 문제 해결 섹션에 연결 |
| "인덱스 개수는 무의미, 조회에 쓰이면 걸어라" | 인덱스 4개를 추가한 판단 근거 |
| "EXPLAIN extra의 Using filesort = 병목 신호" | Before EXPLAIN 분석 시 강조 |
| "커버링 인덱스로 디스크 I/O 제거" | 서브쿼리 JOIN 최적화 설명 |

---

## 8. 보안 체크리스트

- [ ] 원본 테이블명(CTR_CTR_LEDG 등) 노출 안 됨
- [ ] 원본 컬럼명(CTR_LEDG_NO 등) 노출 안 됨
- [ ] 프로젝트명(ALLSP_STD, ALLSP_ANSAN) 노출 안 됨
- [ ] 화면 ID(ctrLeg101) 노출 안 됨
- [ ] 회사명, 고객사명 노출 안 됨
- [ ] 실제 데이터 값 노출 안 됨
- [ ] 비즈니스 로직(채번 규칙 등) 상세 노출 안 됨
- [ ] 스크린샷에 실제 화면/데이터 없음
