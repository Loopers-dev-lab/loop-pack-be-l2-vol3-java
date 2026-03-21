# Phase 1: Timeout — 장애 대응의 출발점

## 학습 목표
- Connection Timeout과 Read Timeout의 차이를 이해한다
- 타임아웃 부재 시 스레드 고갈이 전체 시스템 마비로 이어지는 과정을 이해한다
- 적정 타임아웃 값을 정하는 판단 기준을 세운다

## 타임아웃 설정 근거

### PG 시뮬레이터 응답 시간 측정 (50회)

| 구분 | 응답 시간 | 발생 원인 |
|------|----------|----------|
| 빠른 그룹 | 100~500ms | 시뮬레이터 인위적 지연 후 응답 (정상 or 40% 실패) |
| 느린 그룹 | 3.0~3.5초 | DB 커넥션 타임아웃 (HikariCP default 3초) |

### Connection Timeout: 1000ms (1초)
- TCP 3-way handshake 완료까지의 대기 시간
- PG 서버가 아예 죽었거나 네트워크 단절 시에 걸림
- 1초면 충분: 같은 네트워크(localhost) 또는 같은 데이터센터 내에서 TCP 연결은 수~수십 ms

### Read Timeout: 2000ms (2초)
- 연결 수립 후 응답 데이터 수신 대기 시간
- PG 시뮬레이터의 정상 응답: 100~500ms
- p99 기준(500ms) + 여유분(1.5초) = 2초
- **느린 그룹(3초+)은 타임아웃으로 차단** → 이것이 타임아웃의 핵심 역할

### 타임아웃 없으면 벌어지는 일 (계산)
```
조건:
- Tomcat 최대 스레드: 200개 (현재 설정)
- PG 장애 시 응답 지연: 30초 (타임아웃 없으면 기본값)
- 초당 요청: 10건

결과:
- 200개 스레드가 30초 안에 모두 점유 (200 / 10 = 20초)
- PG와 무관한 상품 조회, 주문 조회 등도 전부 응답 불가
- 전체 시스템 마비 → 장애 전파
```

## 측정에서 얻은 관찰

### PG 시뮬레이터 DB DOWN 상태에서의 동작
- PG 시뮬레이터 health: DOWN (DB 연결 실패)
- 그럼에도 요청 자체는 수신됨 (포트 8082 오픈)
- 두 가지 실패 패턴 관찰:
  1. 컨트롤러 지연(100~500ms) 후 즉시 에러 → "현재 서버가 불안정합니다" (40% 실패율 로직)
  2. 컨트롤러 통과 후 DB 접근 시 HikariCP 커넥션 타임아웃(3초) → "일시적인 오류가 발생했습니다"

### 이 관찰이 중요한 이유
- Read Timeout 2초 설정 시, 패턴2(3초+ 지연)가 **우리 측에서 먼저 끊어진다**
- 이것이 타임아웃의 본질: 상대방의 문제(DB 장애)가 우리 시스템으로 전파되지 않도록 차단
- 우리 스레드는 최대 2초만 점유되고 해방 → 다른 요청 처리 가능

## (v2 보강) 소켓 타임아웃 vs 호출 타임아웃

HTTP 클라이언트마다 "타임아웃"의 의미가 다르다:

| 라이브러리 | 타임아웃 기준 | 특징 |
|---|---|---|
| Apache HttpClient | **패킷 단위 소켓 타임아웃** | 각 TCP 패킷 수신 간 대기 시간. 데이터가 조금씩이라도 오면 리셋됨 |
| OkHttp | **전체 요청 호출 타임아웃** | 요청 시작~응답 완료까지의 총 시간 |
| Spring RestTemplate | **호출 타임아웃** (내부 JDK HttpURLConnection 기반) | connectTimeout/readTimeout 모두 호출 단위 |

현재 구현은 RestTemplate의 호출 타임아웃 기반이므로, "2초 안에 응답이 오지 않으면 실패"가 명확하다.

## (v2 보강) 타임아웃이 너무 짧을 때의 위험

타임아웃은 너무 길어도, **너무 짧아도** 위험하다:

```
위험 시나리오: Read Timeout 500ms (너무 짧게 설정)

1. Client → PG: 결제 요청
2. PG: 450ms에 결제 처리 완료 (돈 빠짐)
3. PG → Client: 응답 전송 시작 (500ms 초과)
4. Client: SocketTimeoutException → "결제 실패"로 판단
5. 결과: PG에서는 결제 성공, 우리 시스템에서는 실패
   → 고객: 돈은 빠졌는데 주문은 안 됨
```

이것이 현재 Read Timeout을 p99(500ms) + **충분한 여유분(1.5초) = 2초**로 잡은 이유다.
그래도 이 시나리오가 완전히 방지되지는 않으므로, **sync API를 통한 상태 대조**가 반드시 필요하다.

## (v2 보강) HTTP 커넥션 풀 최적화

RestTemplate은 기본적으로 요청마다 새 TCP 연결을 생성한다. 부하가 높아지면:
- TCP handshake 오버헤드 반복
- 운영체제 포트 고갈 가능 (TIME_WAIT)

커넥션 풀 설정으로 연결을 재사용한다:

```yaml
pg:
  pool:
    max-connections: 20        # 전체 최대 연결 수 (PG 서버 수용 한계 고려)
    max-per-route: 20          # 단일 호스트(PG) 최대 연결
    connection-request-timeout: 1000  # 풀에서 커넥션 얻는 대기 시간
    keep-alive: 30s            # 유휴 연결 유지 시간
```

- **max-connections**: PG 서버가 수용 가능한 동시 연결 수를 초과하지 않도록 설정
- **connection-request-timeout**: 풀이 꽉 차면 빠르게 실패 (스레드 대기 방지)
- **keep-alive**: PG 서버의 keep-alive 정책에 맞춰 설정 (끊어진 커넥션 사용 방지)

## 구현 내용

### RestTemplate 타임아웃 설정
```java
RestTemplate pgRestTemplate = builder
    .connectTimeout(Duration.ofMillis(1000))  // Connection Timeout
    .readTimeout(Duration.ofMillis(2000))      // Read Timeout
    .build();
```

### 예외 매핑
- `ResourceAccessException` → `PaymentGatewayException` (타임아웃)
  - 내부적으로 `SocketTimeoutException` (Read Timeout) 또는 `ConnectTimeoutException`
- `RestClientException` → `PaymentGatewayException` (HTTP 에러)
  - 4xx, 5xx 응답

### 설정 외부화 (application.yml)
```yaml
pg:
  base-url: http://localhost:8082
  connect-timeout: 1000
  read-timeout: 2000
```
- 환경별 조정 가능 (dev/qa/prd 프로파일)

## ArchTest 위반 해결 기록

### 문제
PgPaymentGateway를 Config에서 직접 생성하면 Config → Infrastructure 의존이 발생.
ArchTest 규칙 `whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()`에 위반.

### 해결
- PgPaymentGateway를 `@Component`로 선언하고 Spring이 자동 등록
- PG 설정값(baseUrl, callbackUrl)은 `@Value`로 직접 주입
- Config에서는 타임아웃이 설정된 RestTemplate Bean만 등록
- 결과: Config → Domain(허용), Infrastructure → Domain(허용), 레이어 규칙 준수

### 교훈
- DomainServiceConfig가 통과하는 이유: Config → Domain 방향이고, Domain은 Config에서의 접근을 허용
- Infrastructure는 어디서도 직접 접근 불가 → Spring의 DI로만 연결
