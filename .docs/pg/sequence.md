### 결제 요청
```mermaid
sequenceDiagram
    actor 회원
    participant PaymentController
    participant PaymentFacade
    participant PaymentApp
    participant PgClient
    participant PG Simulator
    
    회원 ->> PaymentController: 결제요청
    PaymentController ->> PaymentFacade: 결제처리
    PaymentFacade ->> PaymentApp: 결제생성
    PaymentApp -->> PaymentFacade: 결제 정보
    PaymentFacade ->> PgClient: PG사 결제 연동
    PgClient ->> PG Simulator: 결제 요청
    
    PG Simulator -->> PgClient: 200 OK(연동 키)
    PgClient -->> PaymentFacade: 연동 키
    PaymentFacade ->> PaymentApp: 연동 키 반영
    PaymentApp -->> PaymentFacade: 결제 요청 성공
    PaymentFacade -->> PaymentController: 결제 요청 성공
    PaymentController -->> 회원: 결제 중
```

### 장애 상황 및 대응 전략
#### 1. PG Simulator - 500 응답
- 일시적 오류일 가능성이 있다 판단하고 재시도
- 3회 재시도에도 실패 시 장애로 판단하고 실패 처리
```mermaid
sequenceDiagram
    actor 회원
    participant PaymentController
    participant PaymentFacade
    participant PaymentApp
    participant PgClient
    participant PG Simulator
    
    회원 ->> PaymentController: 결제요청
    PaymentController ->> PaymentFacade: 결제처리
    PaymentFacade ->> PaymentApp: 결제생성
    PaymentApp -->> PaymentFacade: 결제 정보
    PaymentFacade ->> PgClient: PG사 결제 연동
    
    loop 최대 3회
        PgClient ->> PG Simulator: 결제 요청
        PG Simulator -->> PgClient: 500 Internal Server Error
    end
    PgClient -->> PaymentFacade: PgServerException
    PaymentFacade ->> PaymentApp: PENDING -> FAILED 로 상태 변경
    PaymentFacade -->> PaymentController: 결제 실패
    PaymentController -->> 회원: 결제 실패
```
<br/>

#### 2. PG Simulator - 응답 지연
- 응답 지연 시간이 길어질 수록 스레드 풀 고갈 가능성이 올라감
- 이를 방지하기 위해서 API 호출 시 타임아웃 설정
- Connection Timeout: 1초 => 발생 시 3회 재시도
- Read Timeout: 2초 => 이미 결제 진행 중일 수 있으므로 재시도 X
  - callback 또는 복구 프로세스에 의해 PG사와 데이터 동기화
```mermaid
sequenceDiagram
    actor 회원
    participant PaymentController
    participant PaymentFacade
    participant PaymentApp
    participant PgClient
    participant PG Simulator
    
    회원 ->> PaymentController: 결제요청
    PaymentController ->> PaymentFacade: 결제처리
    PaymentFacade ->> PaymentApp: 결제생성
    PaymentApp -->> PaymentFacade: 결제 정보
    PaymentFacade ->> PgClient: PG사 결제 연동
    
    alt Connection Timeout
        loop 최대 3회
            PgClient ->> PG Simulator: 결제 요청
        end
        PgClient -->> PaymentFacade: PgConnectTimeoutException
        PaymentFacade ->> PaymentApp: PENDING -> FAILED 로 상태 변경
        PaymentFacade -->> PaymentController: 결제 실패
        PaymentController -->> 회원: 결제 실패    
    else Read Tiemout
        PgClient ->> PG Simulator: 결제 요청
        PgClient -->> PaymentFacade: PgReadTimeoutException
        PaymentFacade -->> PaymentController: 결제 요청 성공
        PaymentController -->> 회원: 결제 중
    end
```
<br/>

#### 3. PG Simulator - 결제 요청 무응답 or 콜백 미호출
- 결제 요청 후 콜백 예상 시간이 지났으나 PENDING 상태인 결제 요청들
- 특정 주기로 직접 PG사 데이터 확인해서 동기화 진행
```mermaid
sequenceDiagram
    participant PaymentReconciliationScheduler
    participant PaymentReconciliationApp
    participant PaymentRepository
    participant PgClient
    participant PaymentApp
    
    PaymentReconciliationScheduler ->> PaymentReconciliationApp: 복구 프로세스 실행
    PaymentReconciliationApp ->> PaymentRepository: 오래된 Pending Payments 조회
    PaymentRepository -->> PaymentReconciliationApp: Pending Payments

    alt 연동 키 O
        PaymentReconciliationApp ->> PgClient: 결제 트랜잭션 조회(연동키)
        PgClient -->> PaymentReconciliationApp: 결제 트랜잭션
        PaymentReconciliationApp ->> PaymentApp: 결제 상태 반영
        
    else 연동 키 X
        PaymentReconciliationApp ->> PgClient: 결제 트랜잭션 조회(주문ID)
        PgClient -->> PaymentReconciliationApp: 결제 트랜잭션 목록
        
        alt 결제내역 X
            PaymentReconciliationApp ->> PaymentApp: 결제 실패 처리
        else 성공 1건이라도 존재
            PaymentReconciliationApp ->> PaymentApp: 결제 성공 처리
        else 전부 실패
            PaymentReconciliationApp ->> PaymentApp: 결제 실패 처리
        end
    end
```