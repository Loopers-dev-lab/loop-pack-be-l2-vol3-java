# 인덱스를 걸었는데 왜 안 빨라졌을까

**TL;DR**
ERP 대장 관리 화면의 조회가 느렸다. "인덱스를 걸면 빨라지겠지"라고 생각하고 fiscal_year에 인덱스를 걸었다. 안 빨라졌다. 메인 테이블이 아니라 **LEFT JOIN으로 붙는 서브쿼리 4개**가 매번 전체 테이블을 스캔하고 있었기 때문이다. 결국 "어디에 인덱스를 거는가"보다 **"쿼리가 실제로 어떻게 실행되는가"**를 먼저 이해해야 했고, EXPLAIN을 읽는 법을 배운 뒤에야 진짜 병목을 찾을 수 있었다.

---

## 1. 처음 든 생각은 단순했다

사내 ERP 시스템에 **대장 관리** 화면이 있다. 계약 건별 마스터 정보를 조회하는 화면인데, 검색 조건을 입력하고 조회 버튼을 누르면 체감상 3~5초는 걸렸다.

데이터는 약 5만 건. 뭐 엄청 많은 건 아니다.

처음 든 생각은 이거였다.

> "WHERE에 쓰이는 컬럼에 인덱스를 걸면 빨라지겠지."

근데 이 판단이 틀렸다.

<!-- 📸 [YOUR ACTION] 실제 화면 스크린샷 삽입
     - 대장 관리 화면의 검색 조건 영역 캡처 (데이터는 모자이크 처리)
     - "이 화면에서 조회 버튼을 누르면 3~5초 걸렸다"를 시각적으로 보여줌
     - 개인정보/사내 정보가 노출되지 않도록 주의
-->

---

## 2. 내가 뭘 모르는지 점검해봤다

틀렸다는 건 안다. 근데 **왜** 틀렸는지를 설명하지 못하겠다.

일단 내 인식 상태를 점검해봤다.

```
현재 문제: 인덱스를 걸었는데 안 빨라졌다.
내가 한 것: WHERE절의 필수 컬럼(fiscal_year)에 인덱스를 걸었다.
내가 아는 것: "조회 조건에 쓰이는 컬럼에 인덱스를 걸면 빠르다."
내가 모르는 것: 왜 안 빨라졌는지.
```

여기서 멈췄다. "인덱스를 걸면 빠르다"는 건 알고 있었다. 근데 **빨라지지 않는 경우가 있다**는 건 생각해본 적이 없었다. 아는 것만으로는 이 상황을 설명할 수 없었다.

그러면 내가 모르는 건 뭘까?

> "이 쿼리가 실제로 어떤 순서로 실행되는지"를 모른다.

나는 쿼리를 **텍스트**로만 읽고 있었다. WHERE절에 뭐가 있는지, JOIN이 몇 개인지. 하지만 MySQL이 이 쿼리를 **어떤 순서로, 어떤 방식으로 실행하는지**는 한 번도 확인하지 않았다.

---

## 3. 쿼리를 처음부터 다시 읽었다

WHERE절만 볼 게 아니라 쿼리 전체를 읽었다. 그제서야 구조가 보였다.

```
contract_ledger (메인)
  ├─ LEFT JOIN ① : 거래처 건수 (COUNT + GROUP BY 서브쿼리)
  ├─ LEFT JOIN ② : 1순번 거래처 상세 (INNER JOIN 포함)
  ├─ LEFT JOIN ③ : 대표 거래처 상세 (INNER JOIN 포함)
  └─ LEFT JOIN ④ : 하자보증 종료일 집계 (GROUP BY 서브쿼리)
```

4개의 LEFT JOIN. 그 중 2개는 서브쿼리다.

왜 이렇게 복잡할까? 화면 요구사항 때문이다.

- 목록에 **거래처명**이 보여야 한다 → JOIN ②③
- 거래처가 **몇 개인지** 보여야 한다 → JOIN ①
- **하자보증 만료일**이 보여야 한다 → JOIN ④

한 화면에 보여줄 정보가 많으니, 쿼리도 그만큼 복잡해진 것이다. 그리고 이 JOIN들에는 **인덱스가 하나도 없었다.**

WHERE절의 fiscal_year에 인덱스를 걸어서 메인 테이블을 빠르게 찾아도, **JOIN으로 붙는 테이블 4개가 전부 Full Scan이면** 전체 쿼리는 느릴 수밖에 없다.

근데 이건 쿼리를 **텍스트로 읽어서** 알게 된 거지, **실제 실행 계획을 확인**한 건 아니다. "아마 여기가 문제일 거야"라는 건 추측이다. 추측으로 인덱스를 건 게 처음의 실수였으니, 이번에는 추측하지 않기로 했다.

<!-- 📝 [YOUR ACTION] 쿼리 전체를 처음 읽었을 때 느낀 점 1~2문장
     - 예: "JOIN이 4개나 있는 줄 몰랐다. WHERE절만 보고 있었으니까"
     - 예: "200줄이 넘는 쿼리를 처음 펼쳤을 때 솔직히 어디서부터 봐야 할지 감이 안 잡혔다"
-->

---

## 4. EXPLAIN — 추측 대신 실행 계획을 읽었다

### EXPLAIN이 뭔가

`EXPLAIN`은 MySQL에게 "이 쿼리를 어떻게 실행할 건지 알려달라"고 요청하는 명령이다. 쿼리를 실제로 실행하지 않고, **실행 계획**만 보여준다.

```sql
EXPLAIN
SELECT ...
FROM contract_ledger LEG
LEFT JOIN (...) ...
WHERE fiscal_year = '2026';
```

이렇게 쿼리 앞에 `EXPLAIN`만 붙이면 된다. 결과는 테이블 형태로 나온다.

### 처음 돌려본 결과

<!-- 📸 [YOUR ACTION] 실제 EXPLAIN 결과 캡처 (Before)
     - 실제 DB에서 인덱스 적용 전 상태로 EXPLAIN을 돌린 스크린샷
     - 테이블명은 치환 후 캡처하거나, 캡처 후 모자이크 처리
     - 아래 예상 결과를 실제 결과로 교체

     실행 방법:
     1. DBeaver 또는 MySQL CLI에서 해당 조회 쿼리 앞에 EXPLAIN 붙이기
     2. 결과에서 id, select_type, table, type, possible_keys, key, rows, extra 확인
     3. 스크린샷 캡처
-->

```
┌──────────────────┬───────┬────────┬───────────────────────┬──────────┐
│      table       │ type  │  rows  │         extra         │   key    │
├──────────────────┼───────┼────────┼───────────────────────┼──────────┤
│ contract_ledger  │ ALL   │ 50,000 │ Using where           │ NULL     │
│ (서브쿼리 ①)     │ ALL   │ 80,000 │ Using temporary       │ NULL     │
│ (서브쿼리 ②)     │ ALL   │ 80,000 │ Using where           │ NULL     │
│ (서브쿼리 ④)     │ ALL   │ 10,000 │ Using temporary       │ NULL     │
└──────────────────┴───────┴────────┴───────────────────────┴──────────┘
```

전부 `type = ALL`. 전부 `key = NULL`.

근데 솔직히 이걸 처음 봤을 때, **뭘 봐야 하는지 몰랐다.** 컬럼이 여러 개 있는데, 어떤 게 중요한 건지 감이 안 잡혔다.

멘토링에서 들은 말이 기준이 됐다.

> "EXPLAIN에서 봐야 할 건 세 가지다. **rows** — 몇 건을 스캔했는지, **type** — 어떻게 접근했는지, **extra** — Using filesort나 Using temporary가 있으면 그게 병목이다."

이 세 가지를 기준으로 다시 읽어봤다.

### EXPLAIN 결과, 이렇게 읽는다

EXPLAIN을 읽을 줄 모르면 결과가 나와도 해석이 안 된다. 나도 그랬다. 각 컬럼이 무엇을 말하는지 정리해본다.

#### type — "이 테이블에 어떻게 접근했는가"

이게 가장 중요하다. 위에서 아래로 갈수록 느리다.

| type | 의미 | 비유 |
|------|------|------|
| **const** | PK 또는 UNIQUE로 정확히 1건 조회 | 주민번호로 본인 찾기 |
| **eq_ref** | JOIN에서 PK/UNIQUE로 1건씩 매칭 | 출석부에서 이름표로 1:1 매칭 |
| **ref** | 인덱스로 여러 건 조회 | 목차에서 "3장" 찾기 → 여러 페이지 |
| **range** | 인덱스 범위 스캔 (BETWEEN, >, <) | 목차에서 "3장~5장" 범위로 찾기 |
| **index** | 인덱스 전체 스캔 (데이터보단 작음) | 목차를 처음부터 끝까지 읽기 |
| **ALL** | 테이블 전체 스캔 ❌ | 책을 1페이지부터 끝까지 넘기기 |

여기서 한 가지 의문이 들 수 있다. **ref와 range가 뭐가 다른 건가?**

```sql
-- ref: Equal(=) 연산. 인덱스에서 정확한 지점을 찾아 여러 건 조회
WHERE fiscal_year = '2026'
→ 인덱스에서 '2026' 위치를 바로 찾음. 거기서 연속된 행들을 읽음.

-- range: 범위 연산. 인덱스에서 시작점~끝점 사이를 스캔
WHERE contract_date >= '2026-01-01' AND contract_date <= '2026-12-31'
→ 인덱스에서 '2026-01-01' 위치를 찾고, '2026-12-31'까지 순차 스캔.
```

ref는 "정확한 지점"을 찾는 거고, range는 "범위를 훑는" 거다. 복합 인덱스에서 Equal 컬럼을 앞에, Range 컬럼을 뒤에 배치해야 하는 이유가 여기에 있다. 뒤에서 다시 다룬다.

**내 쿼리는 전부 ALL이었다.** 4개 테이블 모두 책을 처음부터 끝까지 넘기고 있었다.

#### rows — "몇 건을 읽어야 하는가"

MySQL이 **예상하는 스캔 대상 행 수**다. 핵심은 이것이 **결과 행 수가 아니라는 점**이다.

```
rows = 80,000이면?
→ 결과가 3건이어도, 그 3건을 찾기 위해 8만 건을 훑어본다는 뜻
→ 이게 JOIN마다 발생하면, 50,000 × 80,000 = 40억 번의 비교가 될 수 있다
```

내 쿼리에서 서브쿼리 rows가 80,000이었다. 결과는 기껏 1~3건인데, 그걸 찾으려고 매번 전체를 스캔하고 있었다.

#### key — "실제로 사용한 인덱스"

| 컬럼 | 의미 |
|------|------|
| **possible_keys** | 사용 가능한 인덱스 후보 목록 |
| **key** | 옵티마이저가 실제로 선택한 인덱스 |

`key = NULL`이면 인덱스를 안 쓴 것이다. **내 쿼리는 전부 NULL이었다.**

가끔 possible_keys에는 있는데 key가 NULL인 경우가 있다. 이건 옵티마이저가 **"인덱스 안 쓰는 게 더 빠르다"**고 판단한 것이다. 테이블이 작거나, 인덱스의 선택도(selectivity)가 낮을 때 발생한다.

#### extra — "추가로 벌어지는 일"

여기가 **병목의 증거**가 나오는 곳이다.

| extra | 의미 | 위험도 |
|-------|------|--------|
| **Using where** | WHERE 조건으로 필터링 중 | 보통 (정상 동작) |
| **Using index** | 커버링 인덱스 사용 ✅ | 좋음 (디스크 I/O 없음) |
| **Using temporary** | 임시 테이블 생성 ❌ | 나쁨 |
| **Using filesort** | 별도 정렬 수행 ❌ | 나쁨 |
| **Using index condition** | ICP(Index Condition Pushdown) | 보통 |

**Using temporary**는 MySQL이 GROUP BY나 DISTINCT를 처리하기 위해 **임시 테이블을 메모리에 만드는 것**이다. 데이터가 크면 디스크에 쓰기도 한다.

**Using filesort**는 ORDER BY를 인덱스로 처리하지 못해서 **별도의 정렬 작업**을 수행하는 것이다.

> Using temporary + Using filesort가 동시에 나오면 최악이다. 임시 테이블을 만들고, 그 안에서 다시 정렬까지 하는 것이니까.

### 그래서 병목은 어디였나

다시 내 EXPLAIN 결과를 봤다.

```
서브쿼리 ① : type=ALL, rows=80,000, extra=Using temporary
서브쿼리 ④ : type=ALL, rows=10,000, extra=Using temporary
```

**Using temporary가 2개.** 서브쿼리가 GROUP BY를 처리하기 위해 매번 임시 테이블을 만들고 있었다.

메인 테이블에 인덱스를 걸어서 500건으로 줄여봤자, **서브쿼리가 매번 8만 건을 전부 훑으면서 임시 테이블을 만들고 있으면** 전체 쿼리는 느릴 수밖에 없다.

처음에 나는 "WHERE절이 느린 거다"라고 생각했다. EXPLAIN을 보니 **병목은 WHERE절이 아니라 JOIN이었다.**

추측과 실제가 달랐다. EXPLAIN을 안 돌렸으면 계속 WHERE절만 잡고 있었을 것이다.

<!-- 📝 [YOUR ACTION] EXPLAIN을 처음 돌렸을 때의 반응 1~2문장
     - "ALL이 4줄 연속으로 나왔을 때 느낌"
     - "Using temporary의 의미를 알고 나서 든 생각"
     - 예: "솔직히 type=ALL이 뭔지도 몰라서 검색부터 했다"
-->

---

## 5. 틀린 판단 2: "인덱스를 많이 걸면 위험하다"고 아꼈다

병목을 찾고 나서도 바로 인덱스를 추가하지 못했다. 이런 생각이 있었기 때문이다.

> "인덱스를 많이 걸면 INSERT/UPDATE가 느려진다고 했는데, 4개나 추가해도 괜찮을까?"

이것도 틀린 판단이었다. 정확히 말하면, **맞는 말이지만 이 상황에는 적용되지 않는 말**이었다.

### 왜 이 상황에는 적용되지 않는가

인덱스를 추가하면 쓰기가 느려지는 건 사실이다. INSERT나 UPDATE가 발생할 때마다 해당 인덱스도 함께 갱신해야 하니까. 근데 이 원칙이 적용되려면 **전제 조건**이 있다.

```
"인덱스가 많으면 쓰기가 느려진다"가 문제가 되려면:
→ 쓰기가 빈번해야 한다.

이 테이블의 실상:
→ 등록은 월 수십 건, 수정은 거의 없음
→ 조회는 하루 수십~수백 번
```

읽기와 쓰기의 비율이 **100:1 이상**이다. 이런 테이블에서 인덱스 4개를 아끼는 건, 멘토링에서 들은 말을 빌리면:

> "인덱스 개수 자체는 무의미하다. 조회에 쓰이는 컬럼은 무조건 인덱스를 걸어라. 쓰기 부담 정리는 이후 최적화 단계다."

**"인덱스가 많으면 위험하다"는 일반론이 이 테이블에 적용되지 않는 이유를 한 줄로 정리하면:** 이 테이블은 읽기가 압도적이다. 읽기 편향 테이블에서 인덱스를 아끼는 건 잘못된 절약이다.

<!-- 📝 [YOUR ACTION] 실제 읽기/쓰기 비율 구체화
     - "등록은 월 수십 건, 조회는 하루 수백 번" 수준으로
     - 정확한 수치가 아니어도 됨. 대략적 비율만 있으면 판단 근거가 됨
-->

---

## 6. 인덱스를 어디에, 왜 이 순서로 걸었는가

### 질문: 동적 WHERE에 "하나의 완벽한 인덱스"가 가능한가?

이 쿼리의 WHERE절은 MyBatis `<if>` 태그로 **10개 이상의 동적 조건**이 있다. 사용자가 어떤 조건을 입력하느냐에 따라 실제 실행되는 쿼리가 달라진다.

"모든 조합을 커버하는 인덱스"는 불가능하다. 그래서 **실제 사용 빈도**를 기준으로 잡았다.

```
[패턴 A] ★★★ 가장 빈번: fiscal_year만으로 전체 조회
[패턴 B] ★★  빈번:     fiscal_year + contract_type (Equal)
[패턴 C] ★★  빈번:     fiscal_year + contract_date (Range)
[패턴 D] ★   가끔:     fiscal_year + contract_name (LIKE)
[패턴 E] ★   가끔:     fiscal_year + manage_dept (Equal)
```

<!-- 📝 [YOUR ACTION] 패턴 빈도를 어떻게 판단했는지 1~2문장
     - "실제 사용자 행동을 관찰했다" / "화면 기획서를 봤다" / "담당자에게 물어봤다"
     - 예: "ERP 특성상 회계연도를 먼저 선택하고 전체 목록을 본 뒤 필터링하는 패턴이 대부분이었다"
-->

### 복합 인덱스의 컬럼 순서 — 왜 이 순서여야 하는가

패턴 A, B, C를 하나의 복합 인덱스로 커버하려면:

```sql
CREATE INDEX idx_ledger_main
ON contract_ledger(fiscal_year, contract_type, contract_date);
```

왜 `(fiscal_year, contract_type, contract_date)` 이 순서인가?

**원칙 1: 필수조건을 맨 앞에 (Leftmost Prefix)**

복합 인덱스는 **왼쪽부터 순서대로** 작동한다. 맨 앞 컬럼이 없으면 인덱스 자체를 탈 수 없다.

```
전화번호부가 (성, 이름, 전화번호) 순으로 정렬되어 있다고 하자.

✅ "김" 씨를 찾아주세요         → 바로 찾음
✅ "김" 씨 중 "민수"를 찾아주세요 → 바로 찾음
❌ "민수"를 찾아주세요 (성 모름)  → 처음부터 끝까지 봐야 함
```

`fiscal_year`는 **모든 조회에 항상 포함**되는 필수조건이다. 그러니 맨 앞에 둔다.

**원칙 2: Equal 조건을 Range 조건보다 앞에**

복합 인덱스 `(A, B, C)`에서 B가 Range 연산이면, **C는 인덱스를 탈 수 없다.**

왜 그런가? 인덱스는 정렬된 순서로 저장된다. B에서 범위로 흩어지면, 그 안에서 C의 순서가 보장되지 않기 때문이다.

```
인덱스: (fiscal_year, contract_type, contract_date)

✅ fiscal_year = '2026' AND contract_type = 'A' AND contract_date >= '2026-01-01'
   → fiscal_year(Equal) → contract_type(Equal) → contract_date(Range)
   → 3개 컬럼 모두 인덱스 활용

만약 순서가 (fiscal_year, contract_date, contract_type)이었다면?
❌ fiscal_year = '2026' AND contract_date >= '2026-01-01' AND contract_type = 'A'
   → fiscal_year(Equal) → contract_date(Range) → contract_type(Equal)
   → contract_date에서 범위로 흩어지므로, contract_type은 인덱스 미활용
```

그래서 Equal인 `contract_type`을 두 번째에, Range인 `contract_date`를 맨 뒤에 배치했다.

**그런데 한 가지 의문.**

> "인덱스 1에 manage_dept를 추가하면 안 되나? (fiscal_year, contract_type, contract_date, manage_dept)로?"

할 수 있다. 하지만 contract_date가 Range 연산이면 **그 뒤의 manage_dept는 인덱스를 타지 않는다.** Range 이후의 컬럼은 인덱스가 중단되기 때문이다. 그래서 별도 인덱스를 만들었다.

```sql
CREATE INDEX idx_ledger_dept
ON contract_ledger(fiscal_year, manage_dept);
```

<!-- 📸 [YOUR ACTION] idx_ledger_main 인덱스만 건 후 EXPLAIN
     - type이 ALL → ref로 바뀌었는지
     - rows가 얼마나 줄었는지
     - "메인 테이블은 개선됐지만, 서브쿼리는 아직 ALL이다"를 보여줄 수 있으면 좋음
-->

### 진짜 효과가 컸던 건 — JOIN 인덱스

메인 테이블 인덱스보다 **체감 효과가 훨씬 컸던 건** 서브쿼리 쪽이었다.

```sql
CREATE INDEX idx_partner_lookup
ON contract_partner(contract_no, partner_seq);
```

LEFT JOIN ①②③이 전부 `contract_no`로 조인한다. **이 인덱스 하나로 3개의 JOIN이 개선됐다.**

특히 JOIN ①의 서브쿼리를 자세히 보자:

```sql
SELECT COUNT(partner_seq), contract_no
FROM contract_partner
GROUP BY contract_no
```

이 서브쿼리가 `(contract_no, partner_seq)` 인덱스를 타면 **커버링 인덱스**가 된다.

커버링 인덱스란, **쿼리가 필요한 모든 컬럼이 인덱스에 포함되어 있는 상태**를 말한다. 이 경우 MySQL은 디스크의 실제 데이터 페이지를 읽을 필요 없이, **인덱스 페이지만 읽어서 결과를 반환**할 수 있다.

```
왜 커버링 인덱스가 되는가?

SELECT에 사용된 컬럼: contract_no, partner_seq
인덱스에 포함된 컬럼: contract_no, partner_seq

→ SELECT가 필요로 하는 모든 컬럼이 인덱스에 포함 ✅
→ 디스크 I/O 없이 인덱스 페이지만 읽음
→ EXPLAIN extra에 "Using index"로 확인 가능
```

커버링 인덱스가 되면 **Using temporary가 사라진다.** GROUP BY를 처리하기 위해 임시 테이블을 만들 필요가 없기 때문이다. 인덱스 자체가 `contract_no` 순으로 정렬되어 있으니, GROUP BY도 인덱스 순서를 그대로 따라가면 된다.

하자관리 테이블도 같은 논리로:

```sql
CREATE INDEX idx_defect_lookup
ON contract_defect(contract_no, defect_warranty_end);
```

<!-- 📸 [YOUR ACTION] 4개 인덱스 모두 적용한 후 EXPLAIN 결과 캡처
     - 핵심 확인 포인트:
       1. contract_partner의 type이 ALL → ref로 바뀌었는가?
       2. extra가 Using temporary → Using index로 바뀌었는가?
       3. rows가 80,000 → 몇으로 줄었는가?
     - 이 스크린샷이 블로그의 **클라이맥스**
-->

---

## 7. 인덱스로 해결할 수 없는 것들

여기까지 오면 "인덱스를 잘 걸면 다 빨라진다"고 생각할 수 있다. 근데 **그렇지 않은 경우**가 있다. 이걸 모르면 인덱스가 안 먹히는 조건에서 헛삽질을 하게 된다.

### LIKE '%keyword%' — 왜 인덱스를 못 타는가

```sql
WHERE contract_name LIKE '%공사%'
```

이건 인덱스를 **절대** 타지 않는다.

왜?

인덱스는 정렬된 순서로 데이터를 저장한다. `LIKE '공사%'`는 "공사"로 시작하는 지점을 바로 찾을 수 있다. B-Tree 인덱스에서 "공사"라는 시작점이 명확하니까.

하지만 `LIKE '%공사%'`는 **시작점이 없다.** "공사"가 어디에 있을지 알 수 없으니 처음부터 끝까지 다 봐야 한다. 전화번호부에서 "이름에 '민'이 들어간 사람"을 찾으려면 1페이지부터 끝까지 넘겨야 하는 것과 같다.

**그래서 어떻게 했는가?**

포기했다. 정확히 말하면, LIKE 자체는 그대로 두고 **다른 인덱스가 rows를 충분히 줄여주는 구조**로 만들었다.

```
Before: 50,000건에 LIKE Full Scan → 느림
After:  500건 (fiscal_year 인덱스로 축소) + LIKE 필터 → 무시할 수준
```

5만 건에서 LIKE를 거는 것과 500건에서 LIKE를 거는 건 차원이 다르다. **인덱스로 해결할 수 없는 것을 인정하고, 다른 인덱스가 먼저 rows를 줄여주는 구조를 만드는 것**이 현실적인 대응이었다.

> "모든 걸 인덱스로 해결하려 하면 안 된다"는 건, 인덱스 설계에서 가장 중요한 인식 중 하나인 것 같다.

<!-- 📸 [YOUR ACTION] LIKE 포함 시 EXPLAIN 비교 (선택)
     - fiscal_year + contract_name LIKE '%keyword%' 조합으로 EXPLAIN
     - 인덱스 적용 전: type=ALL, rows=50,000
     - 인덱스 적용 후: type=ref, rows=500 + Using where
-->

### DATE 함수 — 컬럼에 함수를 씌우면 인덱스가 죽는다

```sql
-- AS-IS ❌
WHERE CURDATE() >= DATE_SUB(DATE_ADD(completion_date, INTERVAL 1 YEAR), INTERVAL 15 DAY)
```

`completion_date`에 인덱스가 있어도, **컬럼에 함수를 씌우면 인덱스를 탈 수 없다.**

왜? DB 입장에서 생각해보자. 인덱스에는 `completion_date` 원본 값이 정렬되어 있다. 근데 `DATE_ADD(completion_date, ...)`의 결과가 뭔지는 **모든 행에 대해 함수를 실행해봐야** 안다. 인덱스의 정렬 순서와 함수 적용 후의 순서가 같다는 보장이 없으니, 인덱스를 쓸 수 없는 것이다.

해결은 간단하다. **함수를 컬럼이 아닌 상수 쪽으로 옮긴다.**

```sql
-- TO-BE ✅
WHERE completion_date <= DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 1 YEAR), INTERVAL 15 DAY)
```

같은 논리인데 `completion_date`에 직접 비교하도록 변환했다. 이제 인덱스를 탈 수 있다. `CURDATE()`와 `DATE_ADD`는 **상수로 한 번만 계산**되기 때문이다.

> WHERE절에서 컬럼은 "벌거벗은 상태"여야 한다. 함수, 연산, 형변환을 씌우는 순간 인덱스는 무력화된다.

---

## 8. 적용 결과

### EXPLAIN 비교

<!-- 📸 [YOUR ACTION] 최종 Before/After EXPLAIN 캡처
     - Before: 인덱스 전혀 없는 상태
     - After: 4개 인덱스 모두 적용한 상태
     - 동일한 쿼리, 동일한 검색 조건으로 비교
-->

```
■ Before
┌──────────────────┬───────┬────────┬───────────────────────┬──────────┐
│      table       │ type  │  rows  │         extra         │   key    │
├──────────────────┼───────┼────────┼───────────────────────┼──────────┤
│ contract_ledger  │ ALL   │ 50,000 │ Using where           │ NULL     │
│ contract_partner │ ALL   │ 80,000 │ Using temporary       │ NULL     │
│ contract_defect  │ ALL   │ 10,000 │ Using temporary       │ NULL     │
└──────────────────┴───────┴────────┴───────────────────────┴──────────┘

■ After
┌──────────────────┬───────┬───────┬───────────────────────┬────────────────────┐
│      table       │ type  │ rows  │         extra         │        key         │
├──────────────────┼───────┼───────┼───────────────────────┼────────────────────┤
│ contract_ledger  │ ref   │   500 │ Using where           │ idx_ledger_main    │
│ contract_partner │ ref   │     3 │ Using index           │ idx_partner_lookup │
│ contract_defect  │ ref   │     2 │ Using index           │ idx_defect_lookup  │
└──────────────────┴───────┴───────┴───────────────────────┴────────────────────┘
```

| 지표 | Before | After | 변화 |
|------|--------|-------|------|
| 메인 테이블 type | ALL | ref | Full Scan → Index Scan |
| 메인 테이블 rows | 50,000 | 500 | **100배 감소** |
| 서브쿼리 type | ALL | ref | Full Scan → Index Lookup |
| 서브쿼리 rows | 80,000 | 3 | **26,000배 감소** |
| extra | Using temporary | Using index | 임시 테이블 → 커버링 인덱스 |

숫자만 봐도 차이가 크지만, 핵심은 **Using temporary → Using index**다. 서브쿼리가 매번 8만 건을 GROUP BY하면서 임시 테이블을 만들던 게, 인덱스만으로 3건을 찾는 것으로 바뀌었다. 임시 테이블 생성이라는 **무거운 연산 자체가 사라진 것**이다.

### 응답시간

<!-- ⏱️ [YOUR ACTION] 실제 응답시간 측정
     측정 방법 (택 1):
     a. DBeaver에서 동일 쿼리 실행 → 하단 실행시간 확인
     b. MySQL에서 SET profiling = 1; → 쿼리 실행 → SHOW PROFILES;
     c. 브라우저 DevTools > Network 탭에서 API 응답시간 확인

     측정 절차:
     1. 인덱스 제거 (Before): DROP INDEX idx_ledger_main ON contract_ledger;
     2. 동일 조건으로 3회 실행 → 평균 기록
     3. 인덱스 재생성 (After): CREATE INDEX ...;
     4. 동일 조건으로 3회 실행 → 평균 기록
-->

```
TODO: 실제 측정값으로 교체

Before: 약 ?초
After:  약 ?초
개선율: ?%
```

<!-- 📝 [YOUR ACTION] 체감 차이 1~2문장
     - "3초가 0.2초가 됐을 때, rows 차이가 체감으로 이렇게 크구나 싶었다" 같은 느낌
-->

---

## 9. 돌아보면

### 내가 빠졌던 함정

이번 과정에서 내가 빠졌던 함정은 세 가지다.

**함정 1: WHERE절만 보고 인덱스를 설계했다.**

쿼리 튜닝이라고 하면 반사적으로 WHERE절을 본다. 틀린 건 아니다. 하지만 이 쿼리의 병목은 WHERE가 아니라 **FROM절의 서브쿼리 JOIN**이었다.

"어디에 인덱스를 거는가"를 묻기 전에, **"이 쿼리가 실제로 어떻게 실행되는가"**를 먼저 물어야 했다.

**함정 2: "인덱스가 많으면 위험하다"는 일반론에 매몰됐다.**

맞는 말이다. 하지만 **이 테이블에는 해당되지 않는 말**이었다. 일반론을 적용하려면 전제 조건(쓰기가 빈번한가?)을 먼저 확인해야 했다.

인덱스 개수가 아니라, **이 테이블의 읽기/쓰기 비율**이 판단 기준이다.

**함정 3: EXPLAIN을 안 돌리고 추측했다.**

"여기가 느릴 것 같다"로 시작하면 틀린다. EXPLAIN을 돌리기 전의 내 추측과 실제 결과는 달랐다. 메인 테이블이 아니라 서브쿼리가 범인이었고, Using temporary라는 경고를 눈으로 확인하고 나서야 확신이 생겼다.

추측하지 마라. EXPLAIN을 돌려라. **데이터가 답이다.**

<!-- 📝 [YOUR ACTION] 세 가지 중 가장 뼈아팠던 함정 1~2문장
     - "이 중에서 가장 시간을 낭비한 건 어떤 것이었는지"
-->

### 이 경험에서 발견한 기준

```
1. 쿼리를 전부 읽어라
   — WHERE절만이 아니라 FROM, JOIN, 서브쿼리까지.

2. EXPLAIN을 돌려라
   — 추측이 아니라 rows, type, extra로 판단하라.

3. 쿼리 패턴을 분류하라
   — 동적 WHERE의 실제 사용 빈도가 인덱스 우선순위다.

4. Range는 맨 뒤에
   — 복합 인덱스에서 범위 연산 이후 컬럼은 인덱스를 못 탄다.

5. 포기할 건 포기하라
   — LIKE '%keyword%', DATE 함수는 인덱스로 해결 불가.
     다른 인덱스가 rows를 줄여주는 구조로 대응하라.
```

처음에는 "인덱스를 걸면 빨라진다"고 단순하게 생각했다. 틀렸다.

> "어떤 인덱스를 거는가"보다 **"이 쿼리가 어떻게 실행되는가"**를 먼저 이해하는 게 시작이다.

---

## 10. 결국 비정규화를 했다

인덱스로 서브쿼리의 병목을 제거했다. Using temporary가 사라졌고, rows도 극적으로 줄었다. 하지만 쿼리를 다시 보니 **근본적인 질문**이 남았다.

> "인덱스를 아무리 잘 걸어도, JOIN 4개를 매번 타는 구조 자체가 문제 아닌가?"

### 인덱스만으로는 부족했던 이유

인덱스가 JOIN의 속도를 빠르게 만들어준 건 맞다. 하지만 **JOIN 자체가 없으면 더 빠르다.** 당연한 말인데, 이걸 실감한 건 인덱스를 걸고 나서였다.

LEFT JOIN ②③이 하는 일을 다시 보자:

```
LEFT JOIN ② : 1순번 거래처의 거래처명 → 화면에 "거래처명" 표시
LEFT JOIN ③ : 대표 거래처의 거래처명 → 화면에 "대표거래처명" 표시
```

이 JOIN들은 **contract_partner → partner_master**를 타고 가서 거래처명을 가져온다. 매 조회마다. 5만 건 각각에 대해.

"이걸 메인 테이블에 넣어두면 JOIN을 안 해도 되지 않나?"

처음엔 이 생각을 보류했다. 비정규화는 **정규화의 원칙을 깨는 것**이니까. 거래처명이 바뀌면 동기화해야 하고, 데이터 정합성 관리 포인트가 늘어난다. "지금 안 아프면 안 바꾼다"는 판단이었다.

근데 돌아보니, **지금 아프다.** 인덱스를 걸어도 JOIN 2개는 여전히 실행되고 있었다. 그리고 이 테이블의 거래처 정보는 **한번 등록되면 거의 변경되지 않는다.** 변경 빈도가 극히 낮은 데이터를 매번 JOIN으로 가져오는 건, 비용 대비 이득이 맞지 않았다.

### 비정규화 판단 기준

비정규화를 결정하기 전에 세 가지를 확인했다.

```
1. 이 데이터가 얼마나 자주 변경되는가?
   → 거래처명 변경: 연 수건 이하. 거의 없다.

2. 변경 시 동기화를 놓치면 어떤 일이 벌어지는가?
   → 목록에 옛 거래처명이 표시됨. 치명적이진 않다.
   → 상세 화면에서는 원본 테이블을 조회하므로 정합성 확인 가능.

3. JOIN을 제거했을 때 얼마나 빨라지는가?
   → LEFT JOIN 2개 + INNER JOIN 2개 제거 → 쿼리 구조 자체가 단순해짐.
```

세 가지 모두 비정규화에 유리했다. **변경은 드물고, 변경 시 리스크는 낮고, 제거 효과는 크다.** 보류할 이유가 없었다.

### 마이그레이션

비정규화를 "하겠다"와 "했다"는 다르다. 운영 중인 테이블의 구조를 바꾸는 건 신중해야 한다.

**Step 1. 컬럼 추가**

```sql
ALTER TABLE contract_ledger
  ADD COLUMN partner_name VARCHAR(100) COMMENT '거래처명 (비정규화)',
  ADD COLUMN partner_count INT DEFAULT 0 COMMENT '거래처 수 (비정규화)';
```

기존 데이터에 영향을 주지 않도록 **컬럼 추가만** 먼저 수행했다. 이 시점에서 새 컬럼은 전부 NULL이다. 기존 쿼리는 이 컬럼을 참조하지 않으므로 서비스에 영향 없다.

**Step 2. 기존 데이터 마이그레이션**

```sql
-- 거래처명: 1순번 거래처의 이름을 메인 테이블로 복사
UPDATE contract_ledger LEG
INNER JOIN contract_partner CLT
  ON LEG.contract_no = CLT.contract_no
  AND CLT.partner_seq = 1
INNER JOIN partner_master MST
  ON CLT.partner_code = MST.partner_code
SET LEG.partner_name = MST.partner_name
WHERE LEG.partner_name IS NULL;

-- 거래처 수: GROUP BY로 집계한 값을 메인 테이블로 복사
UPDATE contract_ledger LEG
INNER JOIN (
  SELECT contract_no, COUNT(*) AS cnt
  FROM contract_partner
  GROUP BY contract_no
) CNT ON LEG.contract_no = CNT.contract_no
SET LEG.partner_count = CNT.cnt
WHERE LEG.partner_count = 0;
```

5만 건에 대해 UPDATE를 실행했다. 이 작업은 **한 번만 수행**되는 것이므로 소요 시간은 문제가 되지 않는다.

<!-- ⏱️ [YOUR ACTION] 마이그레이션 UPDATE 소요시간
     - 실제로 돌렸을 때 몇 초 걸렸는지 (예: "5만 건 UPDATE에 약 3초")
     - 마이그레이션 전후로 데이터 정합성 확인한 방법 (예: "COUNT 비교", "샘플 10건 검증")
-->

**Step 3. 쿼리 수정**

마이그레이션이 완료된 후, 조회 쿼리에서 JOIN ①②③을 제거하고 메인 테이블의 컬럼으로 대체했다.

```sql
-- AS-IS: JOIN 4개
SELECT LEG.*,
       CLT_CNT.partner_count,
       MST1.partner_name AS partner_name_1,
       MST2.partner_name AS partner_name_rep,
       ...
FROM contract_ledger LEG
LEFT JOIN (SELECT contract_no, COUNT(*) ... GROUP BY contract_no) CLT_CNT ...
LEFT JOIN contract_partner CLT1 ... INNER JOIN partner_master MST1 ...
LEFT JOIN contract_partner CLT2 ... INNER JOIN partner_master MST2 ...
LEFT JOIN contract_defect DEF ...
WHERE ...

-- TO-BE: JOIN 1개 (하자관리만 남음)
SELECT LEG.*,
       LEG.partner_count,
       LEG.partner_name,
       ...
FROM contract_ledger LEG
LEFT JOIN contract_defect DEF ...
WHERE ...
```

LEFT JOIN 3개가 사라졌다. **SELECT에서 직접 메인 테이블의 컬럼을 읽으면 되니까 JOIN이 필요 없다.**

**Step 4. 동기화 처리**

비정규화의 대가는 **동기화**다. 거래처 정보가 변경되면 메인 테이블도 함께 갱신해야 한다.

```sql
-- 거래처 등록/수정/삭제 시 트리거 또는 서비스 로직에서 실행
UPDATE contract_ledger
SET partner_name = #{newPartnerName},
    partner_count = (
      SELECT COUNT(*) FROM contract_partner
      WHERE contract_no = #{contractNo}
    )
WHERE contract_no = #{contractNo};
```

거래처 변경이 연 수건 이하이므로, 이 동기화 비용은 **매 조회마다 JOIN을 타는 비용**에 비하면 무시할 수 있다.

<!-- 📝 [YOUR ACTION] 동기화를 어떻게 구현했는지 1~2문장
     - 트리거로 했는지, 서비스 로직에서 했는지
     - 예: "거래처 등록/수정 서비스에서 UPDATE를 같이 실행하도록 했다"
-->

### 비정규화 후 EXPLAIN

<!-- 📸 [YOUR ACTION] 비정규화 후 EXPLAIN 캡처
     - JOIN이 줄어든 상태에서 EXPLAIN을 돌린 스크린샷
     - 핵심: 테이블 접근 자체가 줄었는지 (행 수가 줄어든 게 아니라, 행 자체가 없어진 것)
-->

```
■ After Index (§8)
┌──────────────────┬───────┬───────┬───────────────────────┬────────────────────┐
│      table       │ type  │ rows  │         extra         │        key         │
├──────────────────┼───────┼───────┼───────────────────────┼────────────────────┤
│ contract_ledger  │ ref   │   500 │ Using where           │ idx_ledger_main    │
│ contract_partner │ ref   │     3 │ Using index           │ idx_partner_lookup │
│ contract_defect  │ ref   │     2 │ Using index           │ idx_defect_lookup  │
└──────────────────┴───────┴───────┴───────────────────────┴────────────────────┘

■ After Denormalization (§10)
┌──────────────────┬───────┬───────┬───────────────────────┬────────────────────┐
│      table       │ type  │ rows  │         extra         │        key         │
├──────────────────┼───────┼───────┼───────────────────────┼────────────────────┤
│ contract_ledger  │ ref   │   500 │ Using where           │ idx_ledger_main    │
│ contract_defect  │ ref   │     2 │ Using index           │ idx_defect_lookup  │
└──────────────────┴───────┴───────┴───────────────────────┴────────────────────┘
```

contract_partner 행이 **통째로 사라졌다.** 인덱스가 "빠르게 찾는 것"이라면, 비정규화는 **"찾을 필요 자체를 없앤 것"**이다.

인덱스 최적화에서 rows가 80,000 → 3으로 줄었을 때도 대단하다고 느꼈는데, **테이블 접근 자체가 사라지는 건 차원이 다른 개선**이었다.

### 정리: 인덱스 vs 비정규화

```
인덱스:   "이 테이블에서 빠르게 찾아라" → rows를 줄인다
비정규화: "이 테이블을 아예 보지 마라"  → JOIN을 없앤다
```

둘 다 읽기 성능을 개선하지만, 비용 구조가 다르다.

| | 인덱스 | 비정규화 |
|---|---|---|
| **개선 방식** | 탐색 경로 최적화 | JOIN 제거 |
| **쓰기 비용** | INSERT/UPDATE 시 인덱스 갱신 | 원본 변경 시 동기화 필요 |
| **적용 조건** | 항상 가능 | 변경이 드문 데이터에 유리 |
| **되돌리기** | DROP INDEX | 컬럼 제거 + 쿼리 원복 (더 번거로움) |

인덱스는 "안전한 개선"이고, 비정규화는 **"트레이드오프를 감수한 개선"**이다. 그래서 인덱스를 먼저 시도하고, 그것만으로 부족할 때 비정규화를 검토하는 순서가 맞았다.

---

## 11. 아직 남은 것들

**5만 건을 한 번에 가져오는 것 자체가 문제일 수 있다.** 현재 이 화면은 페이지네이션 없이 전체 결과를 한 번에 로드한다. 커서 기반 페이징을 도입하면 인덱스 효율이 더 좋아진다. 하지만 ERP 특성상 사용자가 "전체 목록을 한눈에 보고 싶다"는 요구가 강하기 때문에, 이건 기술적 판단만으로 결정할 수 없는 영역이다.

동시성 제어를 다뤘던 이전 글에서 "아프기 시작하면 바꾸는 거다"라고 정리했었는데, 인덱스도 비정규화도 마찬가지였다. **전환 시점은 트래픽 숫자가 아니라, 운영에서 관측되는 실패 모드가 기준이다.** 슬로우 쿼리 로그가 찍히기 시작하면, 그때 다음 단계를 밟으면 된다.

<!-- 📝 [YOUR ACTION] 다음에 시도할 것 1~2문장
     - 페이지네이션, 캐시 적용 등 어떤 걸 다음으로 해보고 싶은지
     - 이전 글의 "PG 결제 트랜잭션 분리는 아직 안 함"처럼 의도적 보류를 명시
-->

---

> 이 글에서 사용된 테이블명, 컬럼명, 데이터는 보안을 위해 모두 치환되었습니다.
