# 시퀀스 다이어그램

## 주문 요청 (POST /api/v1/orders)

주문 생성은 이 시스템에서 가장 복잡한 로직이다. 재고 차감, 쿠폰 사용, 주문 생성이 하나의 유스케이스 트랜잭션에서 원자적으로 처리되고, 실패 시 전체 롤백되는지 검증한다.

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant OC as OrderController
    participant OU as OrderCreateUseCase
    participant PS as ProductStockApplicationService
    participant CS as CouponApplicationService
    participant OS as OrderApplicationService
    participant DB as DB

    C->>OC: POST /api/v1/orders (items, couponId?)
    OC->>OU: 주문 생성 유스케이스 실행
    note over OU: @Transactional 시작

    OU->>PS: 재고 예약/차감(락 기반)
    PS->>DB: 상품 행 잠금 + 재고 검증/차감
    DB-->>PS: 예약 완료

    alt couponId 있음
        OU->>CS: 쿠폰 검증/사용
        CS->>DB: 소유자/만료/상태 검증 + USED 전이
        DB-->>CS: 성공 또는 실패
    end

    OU->>OS: 주문 생성(스냅샷 포함)
    OS->>DB: Order + OrderItem + CouponSnapshot 저장
    DB-->>OS: 저장 완료

    alt 중간 실패 발생
        OU-->>OC: 예외 전달
        note over OU: 트랜잭션 롤백
        OC-->>C: 4xx/5xx
    else 성공
        note over OU: 트랜잭션 커밋
        OU-->>OC: 주문 정보 반환
        OC-->>C: 201 Created
    end
```

### 핵심 포인트
- **전체 실패 정책**: 여러 상품 중 하나라도 문제가 있으면 전체 주문이 실패한다 (부분 성공 없음).
- **유스케이스 중심**: Controller는 유스케이스를 호출하고, 유스케이스 내부에서 재고/쿠폰/주문 흐름을 오케스트레이션한다.
- **트랜잭션 경계**: 주문 유스케이스(`@Transactional`)에서 재고 차감 + 쿠폰 사용 + 주문 저장을 원자적으로 처리한다.

### 설계 리스크
- **락 경합**: 동시 주문이 몰리면 상품/쿠폰 락 대기가 길어질 수 있다. 락 순서 고정과 짧은 트랜잭션 유지가 필요.
- **쿠폰 만료 판정**: 도메인 정책(상태/시간)과 저장 정책(ERD) 간 불일치가 있으면 경계 시점 버그가 발생할 수 있다.

---

## 주문 취소 (PATCH /orders/{orderId}/cancel)

주문 취소는 고객/어드민 모두 가능하되 권한이 다르다. 상태 변경과 재고 복원이 처리되는지, 이미 취소된 주문에 대한 처리를 검증한다.

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant OC as OrderController
    participant OU as OrderCancelUseCase
    participant OS as OrderService
    participant PS as ProductService
    participant CS as CouponApplicationService
    participant DB as DB

    C->>OC: PATCH /orders/{orderId}/cancel
    OC->>OU: 주문 취소 유스케이스 실행
    note over OU: @Transactional 시작

    OU->>OS: 주문 + 주문항목 조회
    OS-->>OU: 주문 정보 반환

    note over OU: 권한 확인 (고객: 본인만, 어드민: 모두)

    OU->>OS: 주문 취소 처리

    alt 이미 CANCELLED
        OS-->>OU: 409 Conflict
        OU-->>OC: 409 Conflict
        OC-->>C: 409 (이미 취소됨)
    else ORDERED 상태
        OS-->>OU: 취소 완료

        loop 각 OrderItem에 대해
            OU->>PS: 재고 복원 요청
            PS->>DB: 재고 증가
            DB-->>PS: 복원 완료
        end

        alt 주문에 적용된 쿠폰 있음
            OU->>CS: 쿠폰 사용 취소(AVAILABLE 복원)
            CS->>DB: 쿠폰 상태 복원
            DB-->>CS: 복원 완료
        end

        note over OU: 트랜잭션 커밋
        OU-->>OC: 취소 완료
        OC-->>C: 200 OK
    end
```

### 핵심 포인트
- **유스케이스 중심**: OrderCancelUseCase가 권한 확인, 주문 취소, 재고 복원, 쿠폰 복원을 오케스트레이션한다.
- **트랜잭션 경계**: 주문 취소 유스케이스(`@Transactional`)에서 취소/복원 동작을 원자적으로 처리한다.

### 설계 리스크
- **삭제된 상품의 재고 복원**: 주문 후 상품이 Soft Delete된 경우, 취소 시 재고를 복원해야 하는지 정책 결정 필요. 현재는 복원하는 것으로 가정.
- **쿠폰 복원 정책**: 주문 취소 시 쿠폰 재사용 허용 여부(AVAILABLE 복원) 정책을 명확히 합의해야 한다.

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
