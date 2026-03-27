# 01. PG Simulator 셋업

## 현재 상태

- Java additionals 레포(`loopback-be-l2-java-additionals`)에는 **초기 커밋만 존재** (pg-simulator 미포함)
- Kotlin additionals 레포에 PR#1로 pg-simulator 구조가 공개됨
- pg-simulator는 **별도 SpringBoot 앱** (port 8082)으로 동작

## pg-simulator 확보 방법 (선택지)

### 방법 A: Kotlin 버전 직접 사용 (추천)

```bash
# Kotlin additionals 레포를 별도 디렉토리에 클론
git clone https://github.com/Loopers-dev-lab/loopback-be-l2-kotlin-additionals.git
cd loopback-be-l2-kotlin-additionals
git checkout origin/pg-simulator  # 또는 PR 브랜치

# pg-simulator만 실행
./gradlew :apps:pg-simulator:bootRun
```

- **장점**: 즉시 사용 가능, 별도 포팅 작업 불필요
- **단점**: Kotlin 프로젝트를 별도로 관리해야 함

### 방법 B: Java로 포팅하여 현재 프로젝트에 추가

```
apps/
├── commerce-api/        # 기존
├── commerce-batch/      # 기존
├── commerce-streamer/   # 기존
└── pg-simulator/        # 새로 추가
```

settings.gradle.kts에 `":apps:pg-simulator"` 추가 필요.

- **장점**: 하나의 프로젝트에서 관리, Java로 통일
- **단점**: 포팅 작업 필요 (약 10개 클래스)

### 방법 C: Java additionals 레포에 pg-simulator가 올라올 때까지 대기

- **장점**: 공식 제공물 사용
- **단점**: 일정 불확실

## pg-simulator 아키텍처 (Kotlin PR 기반 분석)

### 포트 설정

| 앱 | 서버 포트 | Actuator 포트 |
|----|-----------|---------------|
| commerce-api | 8080 | 8081 |
| pg-simulator | 8082 | 8083 |

### API 엔드포인트

```http
## 결제 요청 (비동기)
POST /api/v1/payments
Headers: X-USER-ID
Body: { orderId, cardType, cardNo, amount, callbackUrl }
Response: { transactionKey, status: "PENDING" }

## 결제 정보 확인
GET /api/v1/payments/{transactionKey}
Headers: X-USER-ID
Response: { transactionKey, orderId, status, ... }

## 주문에 엮인 결제 정보 조회
GET /api/v1/payments?orderId={orderId}
Headers: X-USER-ID
Response: [ { transactionKey, status, ... } ]
```

### 내부 처리 흐름

```
1. POST /api/v1/payments 수신
   → Payment 엔티티 생성 (status: PENDING)
   → PaymentCreated 이벤트 발행
   → 즉시 응답 반환 (transactionKey)

2. @Async 비동기 처리 (1s ~ 5s 지연)
   → handle(transactionKey) 호출
   → 확률 기반 결과 결정:
      - 70%: approve() → status: SUCCESS
      - 20%: limitExceeded() → status: FAILED
      - 10%: invalidCard() → status: FAILED
   → PaymentHandled 이벤트 발행

3. 콜백 전송
   → callbackUrl로 POST 요청
   → 결제 결과(TransactionInfo) 전달
```

### 요청 실패 시뮬레이션

- 요청 자체의 성공률: 60% (40%는 요청 단계에서 실패)
- 요청 지연: 100ms ~ 500ms
- 이 부분은 코드에서 어떻게 구현되었는지 추가 확인 필요

### 핵심 도메인 모델

```
Payment (Entity)
├── transactionKey (PK, "20250816:TR:9577c5" 형식)
├── userId
├── orderId
├── cardType (SAMSUNG, KB, HYUNDAI)
├── cardNo
├── amount
├── callbackUrl
└── status (PENDING → SUCCESS | FAILED)
```

### 의존 모듈

```kotlin
implementation(project(":modules:jpa"))      // JPA + QueryDSL
implementation(project(":modules:redis"))     // Redis (필요시)
implementation(project(":supports:jackson"))  // JSON
implementation(project(":supports:logging"))  // 로깅
implementation(project(":supports:monitoring")) // 메트릭
```

## 결정 필요 사항

| 항목 | 질문 |
|------|------|
| **확보 방법** | A/B/C 중 어떤 방법으로 pg-simulator를 확보할 것인가? |
| **DB 분리** | pg-simulator와 commerce-api가 같은 MySQL을 쓸 것인가, 별도 DB를 쓸 것인가? |
| **동시 실행** | 두 앱을 동시에 실행하는 방법 (터미널 2개 vs docker-compose 추가) |
