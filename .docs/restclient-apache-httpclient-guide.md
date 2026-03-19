# HTTP 클라이언트 학습 정리

## 1. 큰 그림: HTTP 클라이언트의 2계층 구조

HTTP 클라이언트는 **"무엇을 보낼지"**와 **"어떻게 보낼지"**가 분리되어 있다.

### 상위 계층 — HTTP Client API (개발자가 직접 사용하는 인터페이스)

URI, 헤더, 바디 조립과 JSON 변환을 담당한다.

| 이름 | 등장 | 방식 | 상태 | 적합한 경우 |
|------|------|------|------|------------|
| RestTemplate | Spring 3 (2009) | 동기 | 유지보수 모드 | 레거시 프로젝트 |
| WebClient | Spring 5 (2017) | 비동기 (Reactor) | 활발 | WebFlux 앱 |
| RestClient | Spring 6.1 (2023) | 동기 | 활발 (최신 권장) | **서블릿 기반 앱** |
| Feign Client | Netflix/Spring Cloud | 선언적 | 활발 | MSA 서비스 간 호출 |

### 하위 계층 — HTTP Engine (실제 네트워크 통신)

TCP 연결, 소켓 관리, 커넥션 풀을 담당한다.

| 이름 | 특징 | 장점 | 단점 |
|------|------|------|------|
| JDK HttpURLConnection | Java 내장 | 추가 의존성 없음 | 커넥션 풀 없음, 매번 TCP 새로 연결 |
| Apache HttpClient 5 | 별도 라이브러리 | 커넥션 풀, 세밀한 타임아웃, **실무 표준** | 설정이 복잡 |
| OkHttp | Square 제작 | API 깔끔, HTTP/2 지원 | 서버 쪽에서는 덜 보편적 |
| Netty | 비동기 전용 | 높은 처리량 | WebClient 전용, 동기 방식에서 사용 안 함 |

### 계층 간 관계

```
상위 계층 (무엇을 보낼지)        하위 계층 (어떻게 보낼지)
┌────────────────┐
│  RestClient    │──────→ Apache HttpClient (우리 선택)
│  RestTemplate  │──────→ JDK HttpURLConnection (기본값)
│  WebClient     │──────→ Netty (기본값)
└────────────────┘
```

상위 계층은 하위 계층 중 하나를 **교체 가능**하다. 예를 들어 RestClient + Apache도 되고, RestClient + OkHttp도 된다.

---

## 2. 각 HTTP Client API 상세

### RestTemplate (Spring 3, 2009~)

```java
RestTemplate restTemplate = new RestTemplate();

PaymentResponse response = restTemplate.postForObject(
        "http://pg-server/payments",
        request,
        PaymentResponse.class  // JSON → 객체 자동 변환
);
```

- 동기 방식: 응답이 올 때까지 스레드가 블로킹됨
- Spring 5부터 **유지보수 모드** (새 기능 추가 없음, 버그 수정만)
- 레거시 코드에서 아직 많이 보이므로 읽을 줄은 알아야 함

### WebClient (Spring 5, 2017~)

```java
WebClient webClient = WebClient.create("http://pg-server");

Mono<PaymentResponse> response = webClient.post()
        .uri("/payments")
        .bodyValue(request)
        .retrieve()
        .bodyToMono(PaymentResponse.class);

response.subscribe(r -> System.out.println(r));  // 비동기 구독
```

- 비동기(논블로킹) 방식: Reactor의 Mono/Flux를 반환
- 적은 스레드로 많은 동시 요청 처리 가능
- 학습 곡선이 높음 (Reactor 이해 필요)
- WebFlux 기반 앱에 적합, **서블릿 기반에서는 과잉**

### RestClient (Spring 6.1, 2023~)

```java
RestClient restClient = RestClient.builder()
        .baseUrl("http://pg-server")
        .requestFactory(apacheFactory)  // 하위 엔진 교체 가능
        .build();

PaymentResponse response = restClient.post()
        .uri("/payments")
        .contentType(MediaType.APPLICATION_JSON)
        .body(request)
        .retrieve()
        .body(PaymentResponse.class);
```

- 동기 방식 + 현대적 플루언트(체이닝) API
- RestTemplate의 기능 + WebClient의 깔끔한 API를 합침
- **Spring Boot 3.2+ 기준 동기 HTTP 클라이언트의 공식 권장**

### Feign Client (Netflix → Spring Cloud OpenFeign)

```java
@FeignClient(name = "pg-client", url = "http://pg-server")
public interface PgClient {

    @PostMapping("/payments")
    PgPaymentResponse requestPayment(@RequestBody PgPaymentRequest request);

    @GetMapping("/payments/{transactionId}")
    PgPaymentStatusResponse getPaymentStatus(@PathVariable String transactionId);
}
// 구현체를 안 만들어도 됨! Spring이 프록시로 자동 생성
```

- **선언적** 방식: 인터페이스 + 어노테이션만으로 HTTP 호출 정의
- 마이크로서비스 간 통신에 최적화
- Spring Cloud 의존성 필요, 세밀한 제어가 어려움
- 외부 API 1~2곳 연동에는 과잉

---

## 3. 커넥션 풀이 중요한 이유

### 커넥션 풀 없이 (JDK HttpURLConnection)

```
요청 1: TCP 연결(~30ms) → 요청 → 응답 → TCP 종료
요청 2: TCP 연결(~30ms) → 요청 → 응답 → TCP 종료
요청 3: TCP 연결(~30ms) → 요청 → 응답 → TCP 종료
```

매 요청마다 TCP 3-way handshake가 발생한다 (수십ms 낭비).

### 커넥션 풀 사용 (Apache HttpClient)

```
[풀에 연결 준비됨]
요청 1: 풀에서 꺼냄 → 요청 → 응답 → 풀에 반납
요청 2: 풀에서 꺼냄 → 요청 → 응답 → 풀에 반납  ← TCP 연결 재사용!
요청 3: 풀에서 꺼냄 → 요청 → 응답 → 풀에 반납
```

TCP 연결을 재사용하므로 핸드셰이크 비용이 0에 가깝다.

---

## 4. Apache HttpClient의 3가지 타임아웃

```
[1] connectTimeout (TCP 연결 타임아웃)
    우리 서버 ──TCP 연결 시도──→ PG 서버
    "3초 안에 TCP 연결이 안 되면 포기"
    실패 시: ConnectException

[2] socketTimeout (응답 대기 타임아웃)
    TCP 연결 완료, 요청 전송 완료 후
    PG 서버가 응답을 보내기를 기다리는 시간
    "3초 안에 응답 첫 바이트가 안 오면 포기"
    실패 시: SocketTimeoutException

[3] connectionRequestTimeout (풀 대기 타임아웃)
    커넥션 풀에서 빈 연결을 기다리는 시간
    풀의 연결이 전부 사용 중일 때 발생
    "3초 안에 빈 커넥션이 안 생기면 포기"
    실패 시: ConnectionRequestTimeoutException
```

- [1], [2]는 **PG 서버와의 통신 문제**
- [3]은 **우리 서버 내부의 자원 문제** (풀 고갈)

---

## 5. 프로젝트 적용 코드 분석

### 조립 순서

```
ConnectionConfig (타임아웃 설정)
    ↓
PoolingHttpClientConnectionManager (커넥션 풀 + 타임아웃)
    ↓
RequestConfig (풀 대기 타임아웃)
    ↓
CloseableHttpClient (Apache HttpClient 완성)
    ↓
HttpComponentsClientHttpRequestFactory (Spring 어댑터)
    ↓
RestClient (개발자가 사용하는 최종 API)
```

### 생성자 코드 (Apache HttpClient 설정)

```java
public PgClientImpl(@Value("${pg.base-url}") String pgBaseUrl) {
    // [Step 1] 커넥션 풀 매니저 생성
    PoolingHttpClientConnectionManager connectionManager
            = new PoolingHttpClientConnectionManager();
    connectionManager.setMaxTotal(20);            // 전체 최대 커넥션 20개
    connectionManager.setDefaultMaxPerRoute(20);   // PG 1곳이므로 route당 = 전체
    connectionManager.setDefaultConnectionConfig(
            ConnectionConfig.custom()
                    .setConnectTimeout(Timeout.ofSeconds(3))   // TCP 연결 3초
                    .setSocketTimeout(Timeout.ofSeconds(3))    // 응답 대기 3초
                    .build()
    );

    // [Step 2] 요청 레벨 설정
    RequestConfig requestConfig = RequestConfig.custom()
            .setConnectionRequestTimeout(Timeout.ofSeconds(3)) // 풀 대기 3초
            .build();

    // [Step 3] Apache HttpClient 조립
    CloseableHttpClient httpClient = HttpClients.custom()
            .setConnectionManager(connectionManager)
            .setDefaultRequestConfig(requestConfig)
            .build();

    // [Step 4] Spring 어댑터로 감싸기
    HttpComponentsClientHttpRequestFactory factory
            = new HttpComponentsClientHttpRequestFactory(httpClient);

    // [Step 5] RestClient 생성 (상위 계층 + 하위 계층 연결)
    this.restClient = RestClient.builder()
            .baseUrl(pgBaseUrl)
            .requestFactory(factory)
            .build();
}
```

### 실제 HTTP 호출 코드 (RestClient 사용)

```java
return restClient.post()                              // HTTP POST
        .uri("/api/v1/payments")                      // URL
        .contentType(MediaType.APPLICATION_JSON)       // Content-Type 헤더
        .body(request)                                 // 바디 (Jackson이 JSON 변환)
        .retrieve()                                    // 요청 실행!
        .body(PgPaymentResponse.class);                // 응답 JSON → 객체 변환
```

### 실행 시 내부 동작

```
[RestClient] request → JSON 직렬화 (Jackson)
    ↓
[HttpComponentsClientHttpRequestFactory] Spring → Apache 형식 변환
    ↓
[Apache HttpClient] 커넥션 풀에서 연결 꺼냄 → HTTP 전송 → 응답 수신 → 풀에 반납
    ↓
[RestClient] 응답 JSON → PgPaymentResponse 역직렬화 (Jackson)
    ↓
return PgPaymentResponse 객체
```

---

## 6. Resilience4j 어노테이션 동작 원리

### AOP 프록시 방식

```java
@Retry(name = "pgPaymentRequest")
@CircuitBreaker(name = "pgPaymentRequest", fallbackMethod = "requestPaymentFallback")
public PgPaymentResponse requestPayment(PgPaymentRequest request) { ... }
```

`@Retry`, `@CircuitBreaker`는 Spring AOP 프록시로 메서드 실행을 감싼다.

```
외부에서 pgClient.requestPayment() 호출
    ↓
[AOP 프록시가 가로챔]
    ↓
[CircuitBreaker] 서킷이 OPEN인가?
    → OPEN: 메서드 실행 안 함, 바로 fallback
    → CLOSED: 다음 단계로
    ↓
[Retry] 메서드 실행 시도
    → 성공: 결과 반환
    → ConnectException: 500ms 후 재시도 (최대 2회)
    → 재시도도 실패: CircuitBreaker에 실패 기록
    ↓
[fallback 실행]
    → PgPaymentResponse(null, false, "PG 연동 실패") 반환
```

### 주의사항

`spring-boot-starter-aop` 의존성이 반드시 필요하다. 없으면 어노테이션이 무시된다.
같은 클래스 내부에서 호출하면 프록시를 거치지 않아서 Resilience4j가 동작하지 않는다 (self-invocation 문제).

---

## 7. 학습 우선순위

1. **RestClient + Apache HttpClient** — 현재 프로젝트에서 사용 중, 가장 먼저
2. **RestTemplate** — 레거시 코드를 읽을 일이 많으므로 API 차이를 이해
3. **Feign Client** — MSA 프로젝트에 참여하게 되면
4. **WebClient** — WebFlux/리액티브가 필요한 프로젝트에 참여하게 되면
