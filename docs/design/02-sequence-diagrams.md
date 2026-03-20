# 시퀀스 다이어그램

## 주문 요청 (POST /api/v1/orders)

주문 생성은 `쿠폰/포인트/주문 저장`까지만 트랜잭션으로 처리하고, 결제 요청은 트랜잭션 커밋 이후 비동기로 분리한다. 재고 차감은 결제 콜백(또는 상태 수렴)에서 결제 성공이 확인된 뒤 수행한다.

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant OC as OrderController
    participant OU as OrderCreateUseCase
    participant PS as ProductStockApplicationService
    participant CS as CouponApplicationService
    participant PT as PointApplicationService
    participant OS as OrderApplicationService
    participant PE as OrderPaymentEventHandler
    participant PG as PaymentGateway
    participant PC as PaymentCompletionApplicationService
    participant DB as DB

    C->>OC: POST /api/v1/orders (items, couponId?, pointAmount, cardType, cardNo)
    OC->>OU: 주문 생성 유스케이스 실행
    note over OU: @Transactional 시작

    OU->>PS: 상품 존재/삭제/재고 검증
    PS-->>OU: 주문 가능 상품 정보

    alt couponId 있음
        OU->>CS: 쿠폰 검증/사용
        CS->>DB: 소유자/만료/상태 검증 + USED 전이
        DB-->>CS: 성공 또는 실패
    end

    OU->>PT: 포인트 차감
    PT->>DB: 포인트 잔액 차감
    DB-->>PT: 차감 완료

    OU->>OS: 주문 생성(스냅샷 포함)
    OS->>DB: Order + OrderItem 저장
    DB-->>OS: 저장 완료

    alt 중간 실패 발생
        OU-->>OC: 예외 전달
        note over OU: 트랜잭션 롤백
        OC-->>C: 4xx/5xx
    else 성공
        note over OU: 트랜잭션 커밋
        OU-->>OC: 주문 정보 반환
        OC-->>C: 201 Created

        OU--)PE: OrderPaymentRequestEvent (AFTER_COMMIT, @Async)
        PE->>PG: 결제 요청
        PG-->>PE: REQUESTED/SUCCEEDED/FAILED

        PG--)PC: 결제 콜백
        PC->>DB: 결제 상태 수렴 (SUCCEEDED)
        PC--)PS: 결제 성공 시 재고 차감
        PS->>DB: 재고 원자적 차감
    end
```

### 핵심 포인트
- **전체 실패 정책**: 트랜잭션 구간(쿠폰/포인트/주문 저장) 중 하나라도 실패하면 전체 롤백한다.
- **유스케이스 중심**: Controller는 유스케이스를 호출하고, 유스케이스 내부에서 쿠폰/포인트/주문을 오케스트레이션한다.
- **트랜잭션 경계 분리**: 주문 트랜잭션에서는 외부 결제 호출을 수행하지 않고, AFTER_COMMIT 비동기 이벤트로 위임한다.
- **재고 차감 시점**: 결제 성공 상태가 확인된 뒤 콜백/상태수렴 경로에서 차감한다.

### 설계 리스크
- **결제-재고 비동기 간극**: 결제 성공과 재고 차감 사이에 짧은 시간차가 존재하므로, 재시도/수렴 정책이 필요하다.
- **쿠폰 만료 판정**: 도메인 정책(상태/시간)과 저장 정책(ERD) 간 불일치가 있으면 경계 시점 버그가 발생할 수 있다.

---

## 주문 취소 (PATCH /orders/{orderId}/cancel)

주문 취소는 고객/어드민 모두 가능하되 권한이 다르다. 트랜잭션 내에서 쿠폰/포인트/재고를 복구하고 주문을 소프트 삭제한 뒤, 결제 취소는 커밋 이후 비동기로 요청한다.

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant OC as OrderController
    participant OU as OrderCancelUseCase
    participant OS as OrderApplicationService
    participant PS as ProductStockApplicationService
    participant PT as PointApplicationService
    participant CS as CouponApplicationService
    participant PE as OrderPaymentEventHandler
    participant PG as PaymentGateway
    participant DB as DB

    C->>OC: PATCH /orders/{orderId}/cancel
    OC->>OU: 주문 취소 유스케이스 실행
    note over OU: @Transactional 시작

    OU->>OS: 주문 + 주문항목 조회/권한 검증
    OS-->>OU: 주문 정보 반환

    alt 이미 CANCELLED
        OS-->>OU: 409 Conflict
        OU-->>OC: 409 Conflict
        OC-->>C: 409 (이미 취소됨)
    else ORDERED 상태
        alt 주문에 적용된 쿠폰 있음
            OU->>CS: 쿠폰 사용 취소(AVAILABLE 복원)
            CS->>DB: 쿠폰 상태 복원
            DB-->>CS: 복원 완료
        end

        alt 사용 포인트 있음
            OU->>PT: 포인트 복구
            PT->>DB: 포인트 잔액 복구
            DB-->>PT: 복구 완료
        end

        alt 결제 성공으로 재고가 이미 차감됨
            loop 각 OrderItem에 대해
                OU->>PS: 재고 복원 요청
                PS->>DB: 재고 증가
                DB-->>PS: 복원 완료
            end
        end

        OU->>OS: 주문 취소 + 소프트 삭제
        OS->>DB: Order 상태 변경 및 deleted_at 반영
        DB-->>OS: 저장 완료

        note over OU: 트랜잭션 커밋
        OU--)PE: OrderPaymentCancelRequestEvent (AFTER_COMMIT, @Async)
        PE->>PG: 결제 취소 요청
        OU-->>OC: 취소 완료
        OC-->>C: 200 OK
    end
```

### 핵심 포인트
- **유스케이스 중심**: OrderCancelUseCase가 권한 확인, 쿠폰/포인트/재고 복원, 주문 소프트 삭제를 오케스트레이션한다.
- **트랜잭션 경계**: 주문 취소 트랜잭션에서는 내부 데이터 복구만 처리하고, 외부 결제 취소 호출은 AFTER_COMMIT 비동기로 분리한다.

### 설계 리스크
- **보상 순서 정합성**: 쿠폰/포인트/재고 복구와 결제 취소 비동기 요청 간의 실패 조합을 관찰하고, 보정 정책을 유지해야 한다.
- **삭제된 상품의 재고 복원**: 주문 후 상품이 Soft Delete된 경우에도 재고 복원 여부를 일관되게 유지해야 한다.

---

## 좋아요 등록 (POST /api/v1/products/{productId}/likes)

좋아요 등록은 Like 저장과 Product의 likeCount 업데이트가 일관성 있게 처리되는지 검증한다. 중복 좋아요 방지와 삭제된 상품 차단 로직을 확인한다.

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant LC as LikeController
    participant LF as LikeFacade
    participant PS as ProductService
    participant LS as LikeService

    C->>LC: POST /products/{productId}/likes
    LC->>LF: 좋아요 등록 요청

    LF->>PS: 활성 상품 확인
    PS-->>LF: 상품 정보 반환

    alt 삭제된 상품
        LF-->>LC: 400 Bad Request
        LC-->>C: 400 (삭제된 상품)
    else 활성 상품
        LF->>LS: 좋아요 등록
        LS-->>LF: 등록 결과

        alt 이미 좋아요
            LF-->>LC: 409 Conflict
            LC-->>C: 409 (이미 좋아요)
        else 좋아요 가능
            LF->>PS: 좋아요 수 증가
            PS-->>LF: 증가 완료

            LF-->>LC: 좋아요 등록 완료
            LC-->>C: 201 Created
        end
    end
```

### 핵심 포인트
- **이중 보호**: 애플리케이션 레벨 중복 체크 + DB Unique 제약조건으로 중복 방지.

### 설계 리스크
- **크로스 도메인 원자성**: 좋아요 저장과 좋아요 수 증가가 별도 트랜잭션. 좋아요 수 증가 실패 시 좋아요 삭제 보상 로직 필요.
- **likeCount 경합**: 인기 상품에 좋아요가 몰릴 경우 likeCount UPDATE에서 락 경합 발생 가능. 기본 기능 개발 후 고도화 단계에서 해결.

---

## 브랜드 삭제 (DELETE /api-admin/v1/brands/{brandId})

브랜드 삭제는 소프트 삭제 정책으로 수행되며, 브랜드 삭제 시 해당 브랜드의 상품도 함께 soft-delete 처리되는지 확인한다.

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant BC as BrandAdminController
    participant BS as BrandApplicationService
    

    C->>BC: DELETE /api-admin/v1/brands/{brandId}
    note over BC: LDAP 인증 확인
    BC->>BS: 브랜드 삭제 요청

    BS->>BS: 브랜드 조회
    BS-->>BC: 브랜드 정보 (없으면 404)

    BS->>BS: 브랜드 + 연관 상품 삭제
    BS-->>BC: 브랜드 + 연관 상품 삭제 완료

    BC-->>C: 200 OK
```

### 핵심 포인트
- **삭제 정책**: BrandService가 브랜드 삭제와 연관 상품 soft-delete를 단일 트랜잭션 안에서 수행한다.

### 설계 리스크
- **확인-삭제 갭 제거**: 사전 존재성 검사 분기를 제거하고, 삭제 플로우 내부에서 일괄 soft-delete를 수행해 경쟁 조건을 줄인다.

---

## 상품 목록 조회 (GET /api/v1/products)

동적 필터(브랜드), 정렬(최신순/가격순/좋아요순), 페이지네이션이 결합된 읽기 전용 쿼리다. Facade 없이 처리되는 조회 패턴과, Soft Delete된 상품이 고객/어드민 API에서 다르게 처리되는 로직을 검증한다.

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant PC as ProductController
    participant PS as ProductService

    C->>PC: GET /api/v1/products?brandId=1&sort=price_asc&page=0&size=20
    PC->>PS: 활성 상품 목록 조회 (필터 + 정렬 + 페이지네이션)
    PS-->>PC: 상품 목록 반환
    PC-->>C: 200 OK + 페이지네이션된 상품 목록

    alt 존재하지 않는 brandId 필터
        PC-->>C: 200 OK + 빈 목록
    end
```

### 핵심 포인트
- **Facade 불필요**: 읽기 전용 조회이므로 Controller → Service로 직접 흐른다.
- **동적 쿼리**: brandId는 선택적 필터, sort는 3가지 정렬 기준(latest, price_asc, likes_desc), page/size로 페이지네이션 처리.
- **Soft Delete 분기**: 고객 API는 삭제 상품 제외. 어드민 API(`/api-admin/v1/products`)는 deletedAt 포함하여 전체 노출.

### 설계 리스크
- **정렬 성능**: likes_desc 정렬 시 likeCount 컬럼에 인덱스가 없으면 대량 데이터에서 성능 저하 가능. 인덱스 추가로 해결.

---
