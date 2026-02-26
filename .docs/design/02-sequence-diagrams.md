# 5. 시스템 시퀀스 다이어그램 (System Sequence Diagrams)

모든 핵심 기능(회원가입, 인증, 조회, 어드민 등록)에 대해 **객체의 역할과 책임(Responsibility)**이 명확히 드러나도록 시퀀스 다이어그램을 작성했습니다.

단순한 `Service` 하나가 모든 일을 다 하는 것이 아니라, **인증 전처리(AuthenticationInterceptor), 인증 서비스(AuthenticationService), 값 객체 검증(Value Object), 암호화(Encoder), 조회(Query)** 등의 책임이 분리된 구조입니다.

| Flow | 핵심 책임 |
|------|-----------|
| User Flow | 회원가입, 헤더 기반 인증, 정보 조회, 비밀번호 변경 |
| Read Flow | 데이터 조회와 DTO 변환 |
| Write Flow (Admin) | 권한 체크와 데이터 무결성(참조 관계) |
| Like Flow | 멱등성 보장과 좋아요 수 동기화 |
| Order Flow | 재고/결제/스냅샷의 트랜잭션 |

---

## 5-1. 회원 기능 (User Flow)

**핵심 책임 객체:**

| 객체 | 책임 |
|------|------|
| `UserController` | HTTP 요청 수신 및 UseCase 위임 |
| `AuthenticationInterceptor` | 인증 필요 API의 헤더 기반 인증 전처리 |
| `AuthenticationService` | 사용자 인증 (비밀번호 매칭) |
| `UserService` | 회원가입, 정보 조회, 비밀번호 변경 서비스 |
| `PasswordEncoder` | 비밀번호 암호화 및 매칭 (SHA-256) |
| `UserRepository` | 중복 ID 체크 및 사용자 영속화 |

### Scenario 1 — 회원가입 (Register)

```mermaid
sequenceDiagram
    autonumber
    actor User as 👤 User
    participant API as 🌐 UserController
    participant Service as 📦 UserService
    participant VO as 🔒 Value Objects
    participant Encoder as 🛡️ PasswordEncoder
    participant DB as 💾 UserRepository

    User->>API: POST /api/v1/users (loginId, password, name, birthday, email)
    API->>Service: register(loginId, name, rawPassword, birthday, email)

    rect rgb(240, 248, 255)
        Note right of Service: [책임 1] 값 객체 검증
        Service->>VO: UserId.of(loginId), UserName.of(name), Birthday.of(birthday), Email.of(email), Password.of(rawPassword, birthday)
        alt 검증 실패 (형식 불일치, 생년월일 포함 등)
            VO-->>Service: throw IllegalArgumentException
            Service-->>API: 예외 전파
            API-->>User: 400 Bad Request
        else 검증 통과
            VO-->>Service: Value Objects
        end
    end

    rect rgb(255, 240, 245)
        Note right of Service: [책임 2] 중복 확인
        Service->>DB: existsById(userId)
        alt ID 중복
            DB-->>Service: true
            Service-->>API: throw IllegalArgumentException("이미 사용중인 ID 입니다.")
            API-->>User: 400 Bad Request
        else ID 사용 가능
            DB-->>Service: false
        end
    end

    rect rgb(255, 250, 205)
        Note right of Service: [책임 3] 암호화
        Service->>Encoder: encrypt(rawPassword)
        Encoder-->>Service: salt:hashedPassword
    end

    rect rgb(240, 255, 240)
        Note right of Service: [책임 4] 도메인 객체 생성 및 저장
        Service->>Service: User.register(userId, userName, encodedPassword, birth, email, wrongPasswordCount, now)
        Service->>DB: save(User)
        DB-->>Service: User
    end

    Service-->>API: void
    API-->>User: 200 OK
```

### Scenario 2 — 내 정보 조회 (Get My Info)

```mermaid
sequenceDiagram
    autonumber
    actor User as 👤 User
    participant API as 🌐 UserController
    participant Interceptor as 🔐 AuthenticationInterceptor
    participant AuthService as 🔑 AuthenticationService
    participant Service as 🔍 UserService
    participant DB as 💾 UserRepository

    User->>API: GET /api/v1/users/me (Header: X-Loopers-LoginId, X-Loopers-LoginPw)

    rect rgb(255, 230, 230)
        Note right of Interceptor: [책임 1] Interceptor preHandle — 헤더 기반 인증
        Interceptor->>Interceptor: 헤더에서 loginId, password 추출
        alt 필수 헤더 누락
            Interceptor-->>User: 401 Unauthorized ("필수 헤더가 누락되었습니다")
        end
        Interceptor->>AuthService: authenticate(loginId, rawPassword)
        AuthService->>DB: findById(loginId)
        alt 유저 없음
            DB-->>AuthService: Optional.empty()
            AuthService-->>Interceptor: throw IllegalArgumentException("사용자를 찾을 수 없습니다.")
            Interceptor-->>User: 401 Unauthorized
        else 유저 존재
            DB-->>AuthService: User
        end
        AuthService->>AuthService: passwordEncoder.matches(rawPassword, encodedPassword)
        alt 비밀번호 불일치
            AuthService-->>Interceptor: throw IllegalArgumentException("비밀번호가 일치하지 않습니다.")
            Interceptor-->>User: 401 Unauthorized
        else 비밀번호 일치
            AuthService-->>Interceptor: userId
        end
        Interceptor->>Interceptor: request.setAttribute("authenticatedUserId", userId)
    end

    rect rgb(240, 248, 255)
        Note right of API: [책임 2] 사용자 정보 조회
        API->>API: userId = request.getAttribute("authenticatedUserId")
        API->>Service: getUserInfo(userId)
        Service->>DB: findById(userId)
        DB-->>Service: User
        Note right of Service: 이름 마스킹: "홍길동" → "홍길*"
        Service-->>API: UserInfoResponse(loginId, maskedName, birthday, email)
    end

    API->>API: UserInfoResponse.from(userInfo) — birthday → "yyyyMMdd" 포맷
    API-->>User: 200 OK (JSON)
```

### Scenario 3 — 비밀번호 변경 (Update Password)

```mermaid
sequenceDiagram
    autonumber
    actor User as 👤 User
    participant API as 🌐 UserController
    participant Interceptor as 🔐 AuthenticationInterceptor
    participant AuthService as 🔑 AuthenticationService
    participant Service as 📦 UserService
    participant VO as 🔒 Value Objects
    participant Encoder as 🛡️ PasswordEncoder
    participant DB as 💾 UserRepository

    User->>API: PUT /api/v1/users/password (Header: X-Loopers-LoginId, X-Loopers-LoginPw, Body: currentPassword, newPassword)

    rect rgb(255, 230, 230)
        Note right of Interceptor: [책임 1] Interceptor preHandle — 헤더 기반 인증
        Interceptor->>Interceptor: 헤더에서 loginId, password 추출
        alt 필수 헤더 누락
            Interceptor-->>User: 401 Unauthorized ("필수 헤더가 누락되었습니다")
        end
        Interceptor->>AuthService: authenticate(loginId, rawPassword)
        alt 인증 실패 (유저 없음 또는 비밀번호 불일치)
            AuthService-->>Interceptor: throw IllegalArgumentException
            Interceptor-->>User: 401 Unauthorized
        else 인증 성공
            AuthService-->>Interceptor: userId
        end
        Interceptor->>Interceptor: request.setAttribute("authenticatedUserId", userId)
    end

    API->>API: userId = request.getAttribute("authenticatedUserId")
    API->>Service: updatePassword(userId, currentPassword, newPassword)

    rect rgb(240, 248, 255)
        Note right of Service: [책임 2] 사용자 조회 및 비밀번호 값 객체 검증
        Service->>DB: findById(userId)
        DB-->>Service: User
        Service->>VO: Password.of(currentRawPassword, birthday), Password.of(newRawPassword, birthday)
        alt 비밀번호 형식 오류 또는 생년월일 포함
            VO-->>Service: throw IllegalArgumentException
            Service-->>API: 예외 전파
            API-->>User: 400 Bad Request
        end
    end

    rect rgb(255, 240, 245)
        Note right of Service: [책임 3] 비밀번호 검증
        Service->>Encoder: matches(currentPassword, encodedPassword)
        alt 현재 비밀번호 불일치
            Encoder-->>Service: false
            Service-->>API: throw IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.")
            API-->>User: 400 Bad Request
        end

        Service->>Encoder: matches(newPassword, encodedPassword)
        alt 새 비밀번호가 기존과 동일
            Encoder-->>Service: true
            Service-->>API: throw IllegalArgumentException("현재 비밀번호는 사용할 수 없습니다.")
            API-->>User: 400 Bad Request
        end
    end

    rect rgb(240, 255, 240)
        Note right of Service: [책임 4] 암호화 및 저장
        Service->>Encoder: encrypt(newPassword)
        Encoder-->>Service: salt:hashedPassword
        Service->>Service: user.changePassword(encodedNewPassword)
        Service->>DB: save(updatedUser)
        DB-->>Service: User
    end

    Service-->>API: void
    API-->>User: 200 OK
```

---

## 5-2. 브랜드 및 상품 조회 (Public Read Flow)

**핵심 책임 객체:**

| 객체 | 책임 |
|------|------|
| `QueryHandler` | 복잡한 검색/필터링 쿼리 처리 (QueryDSL 등) |
| `DtoMapper` | 엔티티 → API 응답 객체 변환 (민감 정보 제외, 포맷팅) |

```mermaid
sequenceDiagram
    autonumber
    actor User as 👤 User
    participant API as 🌐 ProductController
    participant Service as 🛍️ ProductService
    participant Query as 🔍 QueryHandler
    participant Mapper as 🎨 DtoMapper
    participant DB as 💾 Repository

    Note over User, API: 인증 불필요 (Public API)

    User->>API: GET /api/v1/products?brandId=1&sort=latest&page=0
    API->>Service: getProductList(filterCondition)

    rect rgb(240, 248, 255)
        Note right of Service: [책임 1] 데이터 조회
        Service->>Query: search(brandId, sort, page)
        Query->>DB: Dynamic Select Query
        DB-->>Query: List<ProductEntity>
        Query-->>Service: List<ProductEntity>
    end

    rect rgb(255, 240, 245)
        Note right of Service: [책임 2] 응답 데이터 가공
        Service->>Mapper: toSummaryDtoList(entities)
        Note right of Mapper: 품절 여부 계산, 이미지 URL 매핑
        Mapper-->>Service: List<ProductSummaryDto>
    end

    Service-->>API: PageResponse<ProductSummaryDto>
    API-->>User: 200 OK (JSON)
```

---

## 5-3. 브랜드 및 상품 등록 (Admin Write Flow)

**핵심 책임 객체:**

| 객체 | 책임 |
|------|------|
| `AdminGuard` | 관리자 권한 및 헤더 검증 (AOP/Interceptor) |
| `ImageUploader` | 이미지 파일 외부 저장소 업로드 (S3 등) |
| `CatalogService` | 브랜드 유효성 검증 및 상품 등록 오케스트레이션 |

```mermaid
sequenceDiagram
    autonumber
    actor Admin as 👨‍💼 Admin
    participant API as 🌐 AdminController
    participant Guard as 👮 AdminGuard
    participant Service as 📦 CatalogService
    participant Uploader as ☁️ ImageUploader
    participant DB as 💾 Repository

    Note over Admin, API: Header: X-Loopers-Ldap

    Admin->>API: POST /api-admin/v1/products (Info, Images, BrandId)

    rect rgb(255, 230, 230)
        Note right of API: [책임 1] 관리자 권한 검증
        API->>Guard: checkAdminHeader(request)
        alt 권한 없음
            Guard-->>API: throw UnauthorizedException
            API-->>Admin: 403 Forbidden
        else 권한 확인됨
            Guard-->>API: AdminInfo
        end
    end

    API->>Service: registerProduct(dto, files)

    rect rgb(240, 248, 255)
        Note right of Service: [책임 2] 비즈니스 유효성 검사
        Service->>DB: existsBrand(brandId)
        alt 브랜드 없음
            DB-->>Service: false
            Service-->>API: throw InvalidBrandException
            API-->>Admin: 400 Bad Request
        else 브랜드 존재
            DB-->>Service: true
        end
    end

    rect rgb(255, 250, 205)
        Note right of Service: [책임 3] 리소스(이미지) 처리
        Service->>Uploader: uploadImages(files)
        Uploader-->>Service: List<ImageUrl>
    end

    rect rgb(240, 255, 240)
        Note right of Service: [책임 4] 데이터 영속화
        Service->>DB: save(ProductEntity + ImageEntities)
        DB-->>Service: Product ID
    end

    Service-->>API: ProductResponse
    API-->>Admin: 201 Created
```

---

## 5-4. 좋아요 기능 (Like Flow)

**핵심 책임 객체:**

| 객체 | 책임 |
|------|------|
| `LikeController` | HTTP 요청 수신 및 UseCase 위임 |
| `AuthenticationInterceptor` | 인증 필요 API의 헤더 기반 인증 전처리 |
| `AuthenticationService` | 사용자 인증 (비밀번호 매칭) |
| `LikeService` | 좋아요 등록/취소 오케스트레이션 (멱등성 보장) |
| `ProductRepository` | 상품 존재 여부 확인 |
| `LikeRepository` | 좋아요 데이터 영속화 및 중복 확인 |

### Scenario 1 — 좋아요 등록 (Add Like)

```mermaid
sequenceDiagram
    autonumber
    actor User as 👤 User
    participant API as 🌐 LikeController
    participant Interceptor as 🔐 AuthenticationInterceptor
    participant AuthService as 🔑 AuthenticationService
    participant Service as ❤️ LikeService
    participant ProductDB as 💾 ProductRepository
    participant LikeDB as 💾 LikeRepository

    User->>API: POST /api/v1/products/{productId}/likes (Header: X-Loopers-LoginId, X-Loopers-LoginPw)

    rect rgb(255, 230, 230)
        Note right of Interceptor: [책임 1] Interceptor preHandle — 헤더 기반 인증
        Interceptor->>Interceptor: 헤더에서 loginId, password 추출
        alt 필수 헤더 누락
            Interceptor-->>User: 401 Unauthorized
        end
        Interceptor->>AuthService: authenticate(loginId, rawPassword)
        alt 인증 실패
            AuthService-->>Interceptor: throw IllegalArgumentException
            Interceptor-->>User: 401 Unauthorized
        else 인증 성공
            AuthService-->>Interceptor: userId
        end
        Interceptor->>Interceptor: request.setAttribute("authenticatedUserId", userId)
    end

    API->>API: userId = request.getAttribute("authenticatedUserId")
    API->>Service: addLike(userId, productId)

    rect rgb(240, 248, 255)
        Note right of Service: [책임 2] 상품 존재 확인
        Service->>ProductDB: findById(productId)
        alt 상품 없음
            ProductDB-->>Service: Optional.empty()
            Service-->>API: throw IllegalArgumentException("상품을 찾을 수 없습니다.")
            API-->>User: 404 Not Found
        else 상품 존재
            ProductDB-->>Service: Product
        end
    end

    rect rgb(255, 240, 245)
        Note right of Service: [책임 3] 멱등성 보장 (중복 확인)
        Service->>LikeDB: existsByUserIdAndProductId(userId, productId)
        alt 이미 좋아요 누름
            LikeDB-->>Service: true
            Service-->>API: 정상 응답 (멱등성 — 에러 아님)
            API-->>User: 200 OK
        else 좋아요 없음
            LikeDB-->>Service: false
        end
    end

    rect rgb(240, 255, 240)
        Note right of Service: [책임 4] 좋아요 저장
        Service->>Service: Like.create(userId, productId)
        Service->>LikeDB: save(Like)
        LikeDB-->>Service: Like
    end

    Service-->>API: void
    API-->>User: 200 OK
```

### Scenario 2 — 좋아요 취소 (Cancel Like)

```mermaid
sequenceDiagram
    autonumber
    actor User as 👤 User
    participant API as 🌐 LikeController
    participant Interceptor as 🔐 AuthenticationInterceptor
    participant AuthService as 🔑 AuthenticationService
    participant Service as ❤️ LikeService
    participant LikeDB as 💾 LikeRepository

    User->>API: DELETE /api/v1/products/{productId}/likes (Header: X-Loopers-LoginId, X-Loopers-LoginPw)

    rect rgb(255, 230, 230)
        Note right of Interceptor: [책임 1] Interceptor preHandle — 헤더 기반 인증
        Interceptor->>Interceptor: 헤더에서 loginId, password 추출
        alt 필수 헤더 누락
            Interceptor-->>User: 401 Unauthorized
        end
        Interceptor->>AuthService: authenticate(loginId, rawPassword)
        alt 인증 실패
            AuthService-->>Interceptor: throw IllegalArgumentException
            Interceptor-->>User: 401 Unauthorized
        else 인증 성공
            AuthService-->>Interceptor: userId
        end
        Interceptor->>Interceptor: request.setAttribute("authenticatedUserId", userId)
    end

    API->>API: userId = request.getAttribute("authenticatedUserId")
    API->>Service: cancelLike(userId, productId)

    rect rgb(240, 248, 255)
        Note right of Service: [책임 2] 좋아요 존재 확인
        Service->>LikeDB: findByUserIdAndProductId(userId, productId)
        alt 좋아요 없음
            LikeDB-->>Service: Optional.empty()
            Service-->>API: 정상 응답 (멱등성 — 에러 아님)
            API-->>User: 200 OK
        else 좋아요 존재
            LikeDB-->>Service: Like
        end
    end

    rect rgb(255, 240, 245)
        Note right of Service: [책임 3] 좋아요 삭제
        Service->>LikeDB: delete(Like)
    end

    Service-->>API: void
    API-->>User: 200 OK
```

---

## 5-5. 주문 기능 (Order Flow)

**핵심 책임 객체:**

| 객체 | 책임 |
|------|------|
| `OrderController` | HTTP 요청 수신 및 UseCase 위임 |
| `AuthenticationInterceptor` | 인증 필요 API의 헤더 기반 인증 전처리 |
| `AuthenticationService` | 사용자 인증 (비밀번호 매칭) |
| `OrderCreateService` | 주문 생성 오케스트레이션 (재고 확인, 스냅샷) |
| `OrderCancelService` | 주문 취소 처리 (상태 검증, 재고 복원) |
| `ProductRepository` | 재고 확인 및 차감 |
| `OrderRepository` | 주문 데이터 영속화 |

### Scenario 1 — 주문 생성 (Create Order)

```mermaid
sequenceDiagram
    autonumber
    actor User as 👤 User
    participant API as 🌐 OrderController
    participant Interceptor as 🔐 AuthenticationInterceptor
    participant AuthService as 🔑 AuthenticationService
    participant Service as 🛒 OrderCreateService
    participant ProductDB as 💾 ProductRepository
    participant OrderDB as 💾 OrderRepository

    User->>API: POST /api/v1/orders (Header: X-Loopers-LoginId, X-Loopers-LoginPw, Body: items, deliveryInfo, paymentMethod)

    rect rgb(255, 230, 230)
        Note right of Interceptor: [책임 1] Interceptor preHandle — 헤더 기반 인증
        Interceptor->>Interceptor: 헤더에서 loginId, password 추출
        alt 필수 헤더 누락
            Interceptor-->>User: 401 Unauthorized
        end
        Interceptor->>AuthService: authenticate(loginId, rawPassword)
        alt 인증 실패
            AuthService-->>Interceptor: throw IllegalArgumentException
            Interceptor-->>User: 401 Unauthorized
        else 인증 성공
            AuthService-->>Interceptor: userId
        end
        Interceptor->>Interceptor: request.setAttribute("authenticatedUserId", userId)
    end

    API->>API: userId = request.getAttribute("authenticatedUserId")
    API->>Service: createOrder(userId, orderRequest)

    rect rgb(240, 248, 255)
        Note right of Service: [책임 2] 재고 확인 및 차감
        loop 각 주문 항목
            Service->>ProductDB: findByIdForUpdate(productId)
            Note right of ProductDB: SELECT ... FOR UPDATE (동시성 제어)
            alt 상품 없음
                ProductDB-->>Service: Optional.empty()
                Service-->>API: throw IllegalArgumentException("상품을 찾을 수 없습니다.")
                API-->>User: 404 Not Found
            else 상품 존재
                ProductDB-->>Service: Product
            end
            Service->>Service: product.decreaseStock(quantity)
            alt 재고 부족
                Service-->>API: throw IllegalStateException("재고가 부족합니다.")
                API-->>User: 409 Conflict
            end
            Service->>ProductDB: save(product)
        end
    end

    rect rgb(255, 240, 245)
        Note right of Service: [책임 3] 결제 금액 검증
        Service->>Service: calculateTotalAmount(items)
        Service->>Service: verifyPaymentAmount(calculated, requested)
        alt 금액 불일치
            Service-->>API: throw IllegalArgumentException("결제 금액이 일치하지 않습니다.")
            API-->>User: 400 Bad Request
        end
    end

    rect rgb(240, 255, 240)
        Note right of Service: [책임 4] 주문 생성 및 스냅샷 저장
        Service->>Service: Order.create(userId, items, totalAmount, deliveryInfo)
        Service->>Service: OrderSnapshot.capture(order, products) — 주문 시점 상품 정보 보존
        Service->>OrderDB: save(Order + OrderItems + OrderSnapshot)
        OrderDB-->>Service: Order
    end

    Service-->>API: OrderResponse
    API-->>User: 200 OK (JSON)
```

### Scenario 2 — 주문 취소 (Cancel Order)

```mermaid
sequenceDiagram
    autonumber
    actor User as 👤 User
    participant API as 🌐 OrderController
    participant Interceptor as 🔐 AuthenticationInterceptor
    participant AuthService as 🔑 AuthenticationService
    participant Service as 🛒 OrderCancelService
    participant OrderDB as 💾 OrderRepository
    participant ProductDB as 💾 ProductRepository

    User->>API: POST /api/v1/orders/{orderId}/cancel (Header: X-Loopers-LoginId, X-Loopers-LoginPw)

    rect rgb(255, 230, 230)
        Note right of Interceptor: [책임 1] Interceptor preHandle — 헤더 기반 인증
        Interceptor->>Interceptor: 헤더에서 loginId, password 추출
        alt 필수 헤더 누락
            Interceptor-->>User: 401 Unauthorized
        end
        Interceptor->>AuthService: authenticate(loginId, rawPassword)
        alt 인증 실패
            AuthService-->>Interceptor: throw IllegalArgumentException
            Interceptor-->>User: 401 Unauthorized
        else 인증 성공
            AuthService-->>Interceptor: userId
        end
        Interceptor->>Interceptor: request.setAttribute("authenticatedUserId", userId)
    end

    API->>API: userId = request.getAttribute("authenticatedUserId")
    API->>Service: cancelOrder(userId, orderId)

    rect rgb(240, 248, 255)
        Note right of Service: [책임 2] 주문 조회 및 권한/상태 확인
        Service->>OrderDB: findById(orderId)
        alt 주문 없음
            OrderDB-->>Service: Optional.empty()
            Service-->>API: throw IllegalArgumentException("주문을 찾을 수 없습니다.")
            API-->>User: 404 Not Found
        else 주문 존재
            OrderDB-->>Service: Order
        end
        Service->>Service: order.validateOwner(userId) — 본인 주문 확인
        alt 본인 주문 아님
            Service-->>API: throw IllegalArgumentException("본인의 주문만 취소할 수 있습니다.")
            API-->>User: 400 Bad Request
        end
        Service->>Service: order.isCancellable() — 상태 확인 (결제완료/상품준비중)
        alt 취소 불가 상태 (배송중/배송완료)
            Service-->>API: throw IllegalStateException("배송중/배송완료 상태에서는 취소할 수 없습니다.")
            API-->>User: 409 Conflict
        end
    end

    rect rgb(255, 240, 245)
        Note right of Service: [책임 3] 재고 복원
        loop 각 주문 항목
            Service->>ProductDB: findById(productId)
            ProductDB-->>Service: Product
            Service->>Service: product.increaseStock(quantity)
            Service->>ProductDB: save(product)
        end
    end

    rect rgb(240, 255, 240)
        Note right of Service: [책임 4] 주문 상태 변경
        Service->>Service: order.cancel() — 상태를 '취소'로 변경
        Service->>OrderDB: save(order)
        OrderDB-->>Service: Order
    end

    Service-->>API: void
    API-->>User: 200 OK
```