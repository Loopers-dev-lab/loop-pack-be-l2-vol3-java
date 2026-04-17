PG 성공 후 DB 저장 실패를 복구하는 법 — Local WAL


> **TL;DR**: PG 결제 성공 응답을 DB에 저장하기 전에 로컬 파일에 먼저 기록한다. DB가 죽어도 PG 응답은 보존되고, 복구 스케줄러가 DB에 재반영한다.

---

## 가장 위험한 빈틈

다섯 개의 빈틈 중 이 빈틈이 가장 위험하다.

```
[PG 호출] → PG: "결제 성공, transactionKey=TX-abc123"
[DB 저장] → DB 장애 → Payment 상태 업데이트 실패
```

고객의 돈은 빠져나갔는데 우리 DB에는 그 기록이 없다. 다른 빈틈은 "결제가 안 된" 상태인데, 이 빈틈은 "결제가 됐는데 모르는" 상태다.

배치 복구가 이걸 잡을 수 있을까? 배치는 Payment 레코드를 기준으로 PG에 조회한다. TX-1에서 Payment(REQUESTED)는 저장되어 있으니 배치가 찾아낼 수는 있다. 하지만 배치 주기는 1분이다. 그 사이에 PG 응답 상세 정보(transactionKey, pgProvider 등)가 메모리에만 있다가 사라진다.

---

## DB가 WAL을 쓰는 이유

PostgreSQL은 데이터 파일을 수정하기 전에 WAL(Write-Ahead Log)에 먼저 기록한다. 서버가 크래시해도 WAL에서 복구할 수 있다. MySQL의 Redo Log도 같은 원리다.

핵심은 간단하다. **비싼 연산의 결과를 값싼 저장소에 먼저 보존하는 것.**

결제 시스템에서 "비싼 연산"은 PG 결제 요청이다. 한 번 실행되면 고객의 돈이 빠져나간다. 되돌리려면 환불 프로세스를 밟아야 한다. 이 결과를 DB에 저장하기 전에 로컬 파일에 먼저 기록한다.

---

## 구현

```java
// PG 응답 수신 직후
walWriter.write(orderId, transactionKey, pgStatus);   // 1. 로컬 파일 기록

paymentRepository.updateStatus(paymentId, PAID);       // 2. DB 저장 시도
walWriter.delete(walFile);                             // 3. 성공 시 WAL 삭제
```

DB 저장이 실패하면 2번에서 예외가 터지고, 3번은 실행되지 않는다. WAL 파일이 남는다.

```java
public void write(Long orderId, String transactionKey, String pgStatus) {
    Map<String, Object> walEntry = Map.of(
        "orderId", orderId,
        "transactionKey", transactionKey,
        "pgStatus", pgStatus,
        "timestamp", System.currentTimeMillis()
    );
    String content = objectMapper.writeValueAsString(walEntry);
    Path walFile = walDirectory.resolve(
        "wal-" + orderId + "-" + transactionKey + ".json");
    Files.writeString(walFile, content,
        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
}
```

파일 하나에 JSON 하나. `wal-10042-TX-abc123.json` 같은 이름으로 저장된다.

---

## 복구

`WalRecoveryScheduler`가 주기적으로 WAL 디렉토리를 스캔한다.

```
./wal/payments/
  wal-10042-TX-abc123.json    ← DB 저장 실패한 건
  wal-10043-TX-def456.json    ← DB 저장 실패한 건
```

```
[WAL Recovery — 주기적]
1. WAL 디렉토리에서 .json 파일 목록 조회
2. 각 파일에 대해:
   a. JSON 파싱 → orderId, transactionKey, pgStatus 추출
   b. DB에 Payment 상태 업데이트 시도
      → 성공 → WAL 파일 삭제
      → 실패 → 다음 주기에 재시도
```

DB가 살아나는 순간 WAL에 남아있던 건이 전부 반영된다.

---

## WAL 자체가 실패하면

로컬 디스크에 쓰는 것도 실패할 수 있다. 디스크 가득 참, 파일 시스템 장애 같은 경우다.

```java
public void write(Long orderId, String transactionKey, String pgStatus) {
    try {
        // ... 파일 쓰기
    } catch (IOException e) {
        log.error("WAL 기록 실패: orderId={}, error={}", orderId, e.getMessage());
        // 예외를 던지지 않는다 — WAL 실패가 결제 흐름을 막으면 안 된다
    }
}
```

WAL 기록 실패 시 예외를 던지지 않는다. WAL은 안전장치이지 주 경로가 아니다. WAL이 실패해도 DB 저장은 시도한다. DB 저장도 실패하면? 그때는 배치 복구(1분)가 Payment(REQUESTED) 레코드를 기준으로 PG에 조회해서 잡아낸다.

```
WAL 성공 + DB 성공 → 정상 (WAL 삭제)
WAL 성공 + DB 실패 → WAL Recovery가 복구
WAL 실패 + DB 성공 → 정상 (WAL 필요 없음)
WAL 실패 + DB 실패 → 배치 복구(1분)가 Payment 기준으로 PG 조회
```

WAL은 가장 빠른 복구 경로이지, 유일한 복구 경로가 아니다.

---

## 저장소 선택

현재 구현은 로컬 파일이다.

| 선택지 | 장점 | 단점 |
|--------|------|------|
| 로컬 파일 | DB와 독립적, 단순 | 서버 디스크 장애 시 유실, 다중 서버 불가 |
| Redis | 서버 간 공유 가능 | Redis 장애 시 유실 |
| Kafka | 내구성 높음 | 인프라 추가 필요 |

단일 서버 환경이라 로컬 파일을 선택했다. 다중 서버 환경이면 Redis나 Kafka로 바꿔야 한다. 핵심은 "DB와 독립적인 저장소에 PG 응답을 먼저 기록하는 것"이고, 저장소가 무엇인지는 부차적이다.

---

## 돌아보며

WAL의 코드량은 50줄 정도다. JSON 파일을 쓰고, 읽고, 지우는 것이 전부다. 하지만 이 50줄이 "PG 성공 + DB 실패"라는 가장 위험한 빈틈을 메운다.

TX 분리가 만든 빈틈은 결국 "두 시스템 사이에 원자성이 없다"는 문제다. 원자성을 돌려놓을 수는 없으니, 한쪽의 결과를 다른 곳에 먼저 기록해서 유실을 막는다. 데이터베이스가 수십 년 전에 해결한 문제를 애플리케이션 레벨에서 다시 풀고 있는 셈이다.
