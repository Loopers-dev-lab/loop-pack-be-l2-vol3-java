# 03. PG 클라이언트 연동

## 목적

commerce-api에서 pg-simulator로 HTTP 호출을 수행하는 클라이언트를 구현한다.
프로젝트 최초의 외부 시스템 호출이므로, 기반 패턴을 잘 잡는 것이 중요하다.

---

## HTTP 클라이언트 선택지

| 방안 | 특징 | 장점 | 단점 |
|------|------|------|------|
| **RestTemplate** | Spring 기본 제공, 동기 블로킹 | 단순함, 별도 의존성 없음, 학습 비용 낮음 | Deprecated 방향 (유지보수 모드) |
| **RestClient** | Spring 6.1+ 신규, 동기 블로킹 | RestTemplate 대체, 현대적 API | Spring Boot 3.2+ 필요 (충족) |
| **WebClient** | Spring WebFlux, 비동기 논블로킹 | 비동기 지원, 유연함 | webflux 의존성 추가 필요, 학습 비용 |
| **FeignClient** | Spring Cloud, 선언적 HTTP | 인터페이스 기반, 깔끔 | spring-cloud 의존성, 추가 설정 |

**추천: RestClient 또는 RestTemplate**
- 과제 요구사항에 "RestTemplate 혹은 FeignClient"로 명시
- 프로젝트가 Spring Boot 3.4.4이므로 RestClient도 사용 가능
- 비동기 결제이므로 HTTP 호출 자체는 동기로 충분 (PG가 즉시 응답 후 비동기 처리)

---

## PG 클라이언트 설계

### 클래스 구조

```java
// infrastructure/payment/PgClient.java
@Component
public class PgClient {

    private final RestTemplate restTemplate;

    // 결제 요청
    public PgPaymentResponse requestPayment(PgPaymentRequest request) { ... }

    // 결제 상태 확인
    public PgPaymentResponse getPaymentStatus(String transactionKey) { ... }

    // 주문별 결제 조회
    public List<PgPaymentResponse> getPaymentsByOrderId(String orderId) { ... }
}
```

### PG 요청/응답 DTO

```java
// infrastructure/payment/dto/PgPaymentRequest.java
public record PgPaymentRequest(
    String orderId,
    String cardType,
    String cardNo,
    String amount,
    String callbackUrl
) {}

// infrastructure/payment/dto/PgPaymentResponse.java
public record PgPaymentResponse(
    String transactionKey,
    String orderId,
    String status,       // "PENDING", "SUCCESS", "FAILED"
    String failureReason // nullable
) {}
```

---

## 타임아웃 설정

### PG 시스템 지연 특성

| 구간 | 지연 범위 |
|------|-----------|
| 요청 지연 | 100ms ~ 500ms |
| 처리 지연 | 1s ~ 5s (비동기, 콜백으로 수신) |

### 타임아웃 후보값

| 설정 | 값 | 근거 |
|------|-----|------|
| Connection Timeout | 1s | 네트워크 연결 자체는 빠르게 실패해야 함 |
| Read Timeout | 2s ~ 3s | 요청 지연 최대 500ms의 4~6배. 여유 확보 |

```java
// config/PgClientConfig.java
@Configuration
public class PgClientConfig {

    @Bean
    public RestTemplate pgRestTemplate() {
        return new RestTemplateBuilder()
            .setConnectTimeout(Duration.ofSeconds(1))
            .setReadTimeout(Duration.ofSeconds(3))
            .rootUri("http://localhost:8082")
            .build();
    }
}
```

### 타임아웃 후 처리

```
타임아웃 발생 시:
1. PG 요청은 실패로 간주
2. BUT PG 쪽에서는 요청을 받았을 수 있음
3. → 결제 상태 확인 API로 실제 상태를 확인해야 함
4. → PaymentModel은 PENDING 상태로 유지
5. → 콜백 수신 또는 수동 확인으로 최종 상태 결정
```

**이것이 이번 과제의 핵심 시나리오**:
> "PG에 대한 요청이 타임아웃에 의해 실패되더라도 해당 결제건에 대한 정보를 확인하여 정상적으로 시스템에 반영한다"

---

## 콜백 수신 설계

### 콜백 엔드포인트

```http
POST /api/v1/payments/callback
Content-Type: application/json

{
  "transactionKey": "20250816:TR:9577c5",
  "orderId": "1351039135",
  "status": "SUCCESS",
  "failureReason": null
}
```

### 콜백 처리 흐름

```
PG 콜백 수신
  → transactionKey로 PaymentModel 조회
  → 상태 업데이트 (PENDING → SUCCESS/FAILED)
  → (SUCCESS 시) OrderModel 상태 업데이트 (→ CONFIRMED)
  → (FAILED 시) 실패 사유 기록, 주문 상태는 유지 또는 변경
```

### 콜백 미수신 대응

```
시나리오: PG는 결제 성공했지만 콜백이 유실됨
  → PaymentModel은 PENDING 상태로 남아있음
  → 사용자는 결제가 된 건지 안 된 건지 모름

대응 방안:
  A. 수동 상태 확인 API
     GET /api/v1/payments/{paymentId}/verify
     → PG 상태 확인 API 호출 → 결과 반영

  B. 스케줄러 기반 자동 폴링
     → PENDING 상태가 N분 이상 지속된 결제건을 주기적으로 확인
     → @Scheduled(fixedDelay = 60000) // 1분마다

  C. 두 방법 병행 (추천)
```

---

## commerce-api → pg-simulator 호출 매핑

| commerce-api 동작 | PG API | 용도 |
|-------------------|--------|------|
| 결제 요청 | POST /api/v1/payments | 결제 접수 |
| 상태 확인 (복구용) | GET /api/v1/payments/{txKey} | 콜백 미수신 시 확인 |
| 주문별 결제 조회 | GET /api/v1/payments?orderId=X | 주문의 모든 결제 시도 확인 |

---

## callbackUrl 설정

```yaml
# application.yml
pg:
  base-url: http://localhost:8082
  callback-url: http://localhost:8080/api/v1/payments/callback
  timeout:
    connect: 1000  # ms
    read: 3000     # ms
```

```java
@ConfigurationProperties(prefix = "pg")
public record PgProperties(
    String baseUrl,
    String callbackUrl,
    TimeoutProperties timeout
) {
    public record TimeoutProperties(int connect, int read) {}
}
```

---

## 결정 필요 사항

| # | 항목 | 선택지 |
|---|------|--------|
| 1 | HTTP 클라이언트 | RestTemplate vs RestClient vs FeignClient |
| 2 | 콜백 미수신 대응 | 수동 API만 vs 스케줄러 병행 |
| 3 | PG 인증 | X-USER-ID 헤더에 어떤 값을 넣을지 (userId? loginId?) |
| 4 | callbackUrl | 설정 파일 관리 vs 하드코딩 |
