# 클래스 다이어그램

## 다이어그램 목적
클래스 다이어그램을 통해 다음을 검증한다:
- 도메인 책임: 각 도메인의 역할이 명확한가
- 의존 방향: 상위 계층이 하위 계층에만 의존하는가
- 응집도: 관련 기능이 적절히 그룹화되어 있는가

---

## 1. 전체 계층 구조 개요

```mermaid
classDiagram
    direction TB

    namespace Interfaces {
        class Controller
        class ApiSpec
        class Dto
        class ArgumentResolver
        class Interceptor
    }

    namespace Application {
        class Facade
        class Info
    }

    namespace Domain {
        class Entity
        class Service
        class Repository
    }

    namespace Infrastructure {
        class RepositoryImpl
        class JpaRepository
    }

    Controller --> Facade : uses
    Controller --> Dto : uses
    Facade --> Service : uses
    Facade --> Info : returns
    Service --> Repository : uses
    Service --> Entity : uses
    RepositoryImpl ..|> Repository : implements
    RepositoryImpl --> JpaRepository : uses
```

**계층별 책임:**
- **Interfaces**: HTTP 요청/응답 처리, DTO 변환, 인증 처리
- **Application**: 유스케이스 조율, 도메인 ↔ 프레젠테이션 변환
- **Domain**: 비즈니스 로직, 엔티티 검증, 도메인 규칙
- **Infrastructure**: 데이터 접근, 외부 시스템 연동

---

## 2. Brand 도메인 클래스

```mermaid
classDiagram
    direction TB

    %% Interfaces Layer
    class BrandV1Controller {
        -BrandFacade brandFacade
        +getBrand(Long brandId) ApiResponse~BrandDto.Response~
    }

    class BrandAdminV1Controller {
        -BrandFacade brandFacade
        +getBrands(Pageable) ApiResponse~Page~
        +getBrand(Long brandId) ApiResponse~BrandDto.Response~
        +createBrand(CreateRequest) ApiResponse~CreateResponse~
        +updateBrand(Long, UpdateRequest) ApiResponse~Response~
        +deleteBrand(Long brandId) ApiResponse~Void~
    }

    class BrandV1Dto {
        <<sealed>>
    }
    class Response {
        <<record>>
        +Long id
        +String name
        +String description
        +String logoUrl
        +from(BrandInfo) Response
    }
    class CreateRequest {
        <<record>>
        +String name
        +String description
        +String logoUrl
    }
    class CreateResponse {
        <<record>>
        +Long brandId
    }
    class UpdateRequest {
        <<record>>
        +String name
        +String description
        +String logoUrl
    }

    %% Application Layer
    class BrandFacade {
        -BrandService brandService
        +getBrand(Long brandId) BrandInfo
        +getBrands(Pageable) Page~BrandInfo~
        +createBrand(String, String, String) BrandInfo
        +updateBrand(Long, String, String, String) BrandInfo
        +deleteBrand(Long brandId) void
    }

    class BrandInfo {
        <<record>>
        +Long id
        +String name
        +String description
        +String logoUrl
        +from(Brand) BrandInfo
    }

    %% Domain Layer
    class Brand {
        -String name
        -String description
        -String logoUrl
        +Brand(String, String, String)
        +update(String, String, String) void
        #guard() void
    }

    class BrandService {
        -BrandRepository brandRepository
        +getBrand(Long brandId) Brand
        +getBrands(Pageable) Page~Brand~
        +createBrand(String, String, String) Brand
        +updateBrand(Long, String, String, String) Brand
        +deleteBrand(Long brandId) void
    }

    class BrandRepository {
        <<interface>>
        +findById(Long) Optional~Brand~
        +findAll(Pageable) Page~Brand~
        +save(Brand) Brand
        +existsByName(String) boolean
    }

    %% Infrastructure Layer
    class BrandRepositoryImpl {
        -BrandJpaRepository brandJpaRepository
    }

    class BrandJpaRepository {
        <<interface>>
        +findByName(String) Optional~Brand~
        +existsByNameAndDeletedAtIsNull(String) boolean
    }

    %% Relationships
    BrandV1Controller --> BrandFacade
    BrandAdminV1Controller --> BrandFacade
    BrandFacade --> BrandService
    BrandFacade --> BrandInfo
    BrandService --> BrandRepository
    BrandService --> Brand
    BrandRepositoryImpl ..|> BrandRepository
    BrandRepositoryImpl --> BrandJpaRepository
    Brand --|> BaseEntity

    BrandV1Dto ..> Response
    BrandV1Dto ..> CreateRequest
    BrandV1Dto ..> CreateResponse
    BrandV1Dto ..> UpdateRequest
```

---

## 3. Product 도메인 클래스

```mermaid
classDiagram
    direction TB

    %% Interfaces Layer
    class ProductV1Controller {
        -ProductFacade productFacade
        +getProducts(String sort, Long brandId, Pageable) ApiResponse~Page~
        +getProduct(Long productId) ApiResponse~DetailResponse~
    }

    class ProductAdminV1Controller {
        -ProductFacade productFacade
        +getProducts(Pageable, Long brandId) ApiResponse~Page~
        +getProduct(Long productId) ApiResponse~AdminDetailResponse~
        +createProduct(CreateRequest) ApiResponse~CreateResponse~
        +updateProduct(Long, UpdateRequest) ApiResponse~Response~
        +deleteProduct(Long productId) ApiResponse~Void~
    }

    %% Application Layer
    class ProductFacade {
        -ProductService productService
        -ProductLikeService productLikeService
        -BrandService brandService
        +getProducts(String, Long, Pageable) Page~ProductInfo~
        +getProduct(Long productId) ProductInfo
        +createProduct(Long, String, String, Long, Integer, String) ProductInfo
        +updateProduct(Long, String, String, Long, Integer, String) ProductInfo
        +deleteProduct(Long productId) void
    }

    class ProductInfo {
        <<record>>
        +Long id
        +Long brandId
        +String brandName
        +String name
        +String description
        +Long price
        +Integer stock
        +String imageUrl
        +Long likeCount
        +from(Product, Long) ProductInfo
    }

    %% Domain Layer
    class Product {
        -Long brandId
        -String name
        -String description
        -Long price
        -Integer stock
        -String imageUrl
        +Product(Long, String, String, Long, Integer, String)
        +update(String, String, Long, Integer, String) void
        +decreaseStock(int quantity) void
        +increaseStock(int quantity) void
        #guard() void
    }

    class ProductService {
        -ProductRepository productRepository
        -BrandRepository brandRepository
        +getProduct(Long productId) Product
        +getProductForOrder(Long productId) Product
        +getProducts(String, Long, Pageable) Page~Product~
        +getProductsByBrandId(Long brandId) List~Product~
        +createProduct(...) Product
        +updateProduct(...) Product
        +deleteProduct(Long productId) void
        +decreaseStock(Long productId, int quantity) void
    }

    class ProductRepository {
        <<interface>>
        +findById(Long) Optional~Product~
        +findByIdWithLock(Long) Optional~Product~
        +findAll(String, Long, Pageable) Page~Product~
        +findAllByBrandId(Long) List~Product~
        +findAllOrderByLikeCountDesc(Long, Pageable) Page~Object[]~
        +save(Product) Product
    }

    %% Infrastructure Layer
    class ProductRepositoryImpl {
        -ProductJpaRepository productJpaRepository
        -JPAQueryFactory queryFactory
    }

    class ProductJpaRepository {
        <<interface>>
    }

    %% Relationships
    ProductV1Controller --> ProductFacade
    ProductAdminV1Controller --> ProductFacade
    ProductFacade --> ProductService
    ProductFacade --> ProductInfo
    ProductService --> ProductRepository
    ProductService --> Product
    ProductRepositoryImpl ..|> ProductRepository
    ProductRepositoryImpl --> ProductJpaRepository
    Product --|> BaseEntity
```

---

## 4. ProductLike 도메인 클래스

```mermaid
classDiagram
    direction TB

    %% Interfaces Layer
    class ProductLikeV1Controller {
        -ProductLikeFacade productLikeFacade
        +like(AuthenticatedUser, Long productId) ApiResponse~Void~
        +unlike(AuthenticatedUser, Long productId) ApiResponse~Void~
        +getMyLikes(AuthenticatedUser, Long userId) ApiResponse~List~
    }

    %% Application Layer
    class ProductLikeFacade {
        -ProductLikeService productLikeService
        -ProductService productService
        -UserService userService
        +like(Long userId, Long productId) void
        +unlike(Long userId, Long productId) void
        +getMyLikes(Long userId) List~ProductLikeInfo~
    }

    class ProductLikeInfo {
        <<record>>
        +Long productId
        +String productName
        +Long price
        +String imageUrl
        +ZonedDateTime likedAt
    }

    %% Domain Layer
    class ProductLike {
        -Long userId
        -Long productId
        +ProductLike(Long userId, Long productId)
        +getUserId() Long
        +getProductId() Long
    }

    class ProductLikeService {
        -ProductLikeRepository productLikeRepository
        +like(Long userId, Long productId) ProductLike
        +unlike(Long userId, Long productId) void
        +existsByUserIdAndProductId(Long, Long) boolean
        +countByProductId(Long productId) Long
        +getLikeCounts(List~Long~ productIds) Map~Long_Long~
        +getByUserId(Long userId) List~ProductLike~
        +deleteAllByProductId(Long productId) void
    }

    class ProductLikeRepository {
        <<interface>>
        +findByUserIdAndProductId(Long, Long) Optional~ProductLike~
        +existsByUserIdAndProductId(Long, Long) boolean
        +countByProductId(Long) Long
        +countByProductIdIn(List~Long~) List~Object[]~
        +findAllByUserId(Long) List~ProductLike~
        +deleteByUserIdAndProductId(Long, Long) void
        +deleteAllByProductId(Long) void
        +save(ProductLike) ProductLike
    }

    %% Infrastructure Layer
    class ProductLikeRepositoryImpl {
        -ProductLikeJpaRepository productLikeJpaRepository
    }

    class ProductLikeJpaRepository {
        <<interface>>
    }

    %% Relationships
    ProductLikeV1Controller --> ProductLikeFacade
    ProductLikeFacade --> ProductLikeService
    ProductLikeFacade --> ProductLikeInfo
    ProductLikeService --> ProductLikeRepository
    ProductLikeService --> ProductLike
    ProductLikeRepositoryImpl ..|> ProductLikeRepository
    ProductLikeRepositoryImpl --> ProductLikeJpaRepository
    ProductLike --|> BaseEntity
```

---

## 5. Order 도메인 클래스

```mermaid
classDiagram
    direction TB

    %% Interfaces Layer
    class OrderV1Controller {
        -OrderFacade orderFacade
        +createOrder(AuthenticatedUser, CreateRequest) ApiResponse~CreateResponse~
        +getOrders(AuthenticatedUser, LocalDate, LocalDate, Pageable) ApiResponse~Page~
        +getOrder(AuthenticatedUser, Long orderId) ApiResponse~DetailResponse~
    }

    class OrderV1Dto {
        <<sealed>>
    }

    class CreateRequest {
        <<record>>
        +List~OrderItemRequest~ items
    }

    class OrderItemRequest {
        <<record>>
        +Long productId
        +Integer quantity
    }

    class CreateResponse {
        <<record>>
        +Long orderId
        +Long totalPrice
    }

    class ListResponse {
        <<record>>
        +Long orderId
        +Long totalPrice
        +String status
        +ZonedDateTime createdAt
    }

    class DetailResponse {
        <<record>>
        +Long orderId
        +Long totalPrice
        +String status
        +List~OrderItemResponse~ items
        +ZonedDateTime createdAt
    }

    class OrderItemResponse {
        <<record>>
        +Long productId
        +String productName
        +Integer quantity
        +Long price
    }

    %% Application Layer
    class OrderFacade {
        -OrderService orderService
        -ProductService productService
        -UserService userService
        +createOrder(Long userId, List~OrderItemRequest~) OrderInfo
        +getOrders(Long userId, LocalDate, LocalDate, Pageable) Page~OrderInfo~
        +getOrder(Long userId, Long orderId) OrderInfo
    }

    class OrderInfo {
        <<record>>
        +Long id
        +Long userId
        +Long totalPrice
        +OrderStatus status
        +List~OrderItemInfo~ items
        +ZonedDateTime createdAt
        +from(Order) OrderInfo
    }

    class OrderItemInfo {
        <<record>>
        +Long productId
        +String productName
        +Integer quantity
        +Long price
        +from(OrderItem) OrderItemInfo
    }

    %% Domain Layer
    class Order {
        -Long userId
        -Long totalPrice
        -OrderStatus status
        -List~OrderItem~ orderItems
        +Order(Long userId)
        +addItem(OrderItem item) void
        +calculateTotalPrice() void
        +complete() void
        +cancel() void
    }

    class OrderItem {
        -Order order
        -Long productId
        -String productName
        -Integer quantity
        -Long price
        +OrderItem(Long, String, Integer, Long)
        +setOrder(Order order) void
        +getSubtotal() Long
    }

    class OrderStatus {
        <<enumeration>>
        PENDING
        COMPLETED
        CANCELLED
    }

    class OrderService {
        -OrderRepository orderRepository
        -ProductService productService
        +createOrder(Long userId, List~OrderItemRequest~) Order
        +getOrder(Long orderId) Order
        +getOrders(Long userId, LocalDate, LocalDate, Pageable) Page~Order~
        +validateOrderAccess(Long userId, Order order) void
    }

    class OrderRepository {
        <<interface>>
        +findById(Long) Optional~Order~
        +findByUserId(Long, Pageable) Page~Order~
        +findByUserIdAndCreatedAtBetween(...) Page~Order~
        +save(Order) Order
    }

    %% Infrastructure Layer
    class OrderRepositoryImpl {
        -OrderJpaRepository orderJpaRepository
    }

    class OrderJpaRepository {
        <<interface>>
    }

    %% Relationships
    OrderV1Controller --> OrderFacade
    OrderFacade --> OrderService
    OrderFacade --> OrderInfo
    OrderService --> OrderRepository
    OrderService --> Order
    Order --> OrderItem
    Order --> OrderStatus
    OrderRepositoryImpl ..|> OrderRepository
    OrderRepositoryImpl --> OrderJpaRepository
    Order --|> BaseEntity
    OrderItem --|> BaseEntity

    OrderV1Dto ..> CreateRequest
    OrderV1Dto ..> OrderItemRequest
    OrderV1Dto ..> CreateResponse
    OrderV1Dto ..> ListResponse
    OrderV1Dto ..> DetailResponse
    OrderV1Dto ..> OrderItemResponse
```

---

## 6. 인증 관련 클래스

```mermaid
classDiagram
    direction TB

    %% 일반 사용자 인증 (기존)
    class AuthenticatedUser {
        <<record>>
        +String loginId
        +String password
    }

    class AuthenticatedUserArgumentResolver {
        -UserService userService
        +supportsParameter(MethodParameter) boolean
        +resolveArgument(...) Object
    }

    %% 어드민 인증 (신규)
    class AdminUser {
        <<record>>
        +String ldapId
    }

    class AdminAuthInterceptor {
        -String ADMIN_LDAP_HEADER
        -String ADMIN_LDAP_VALUE
        +preHandle(HttpServletRequest, HttpServletResponse, Object) boolean
    }

    class AdminUserArgumentResolver {
        +supportsParameter(MethodParameter) boolean
        +resolveArgument(...) Object
    }

    %% WebMvcConfig
    class WebMvcConfig {
        -AuthenticatedUserArgumentResolver authResolver
        -AdminUserArgumentResolver adminResolver
        -AdminAuthInterceptor adminInterceptor
        +addArgumentResolvers(List) void
        +addInterceptors(InterceptorRegistry) void
    }

    %% Interfaces
    class HandlerMethodArgumentResolver {
        <<interface>>
    }

    class HandlerInterceptor {
        <<interface>>
    }

    %% Relationships
    WebMvcConfig --> AuthenticatedUserArgumentResolver
    WebMvcConfig --> AdminUserArgumentResolver
    WebMvcConfig --> AdminAuthInterceptor
    AuthenticatedUserArgumentResolver ..|> HandlerMethodArgumentResolver
    AdminUserArgumentResolver ..|> HandlerMethodArgumentResolver
    AdminAuthInterceptor ..|> HandlerInterceptor
```

**핵심 포인트:**
- **AdminAuthInterceptor**: `/api-admin/**` 경로에 대해 헤더 검증 (1차 방어선)
- **AdminUserArgumentResolver**: 컨트롤러에 AdminUser 객체 주입

---

## 7. 공통 클래스

```mermaid
classDiagram
    direction TB

    class BaseEntity {
        <<abstract>>
        #Long id
        #ZonedDateTime createdAt
        #ZonedDateTime updatedAt
        #ZonedDateTime deletedAt
        #guard() void
        +delete() void
        +restore() void
        +isDeleted() boolean
    }

    class ApiResponse~T~ {
        <<record>>
        +Metadata meta
        +T data
        +success() ApiResponse~Object~
        +success(T data) ApiResponse~T~
        +fail(String, String) ApiResponse~Object~
    }

    class Metadata {
        <<record>>
        +Result result
        +String errorCode
        +String message
    }

    class Result {
        <<enumeration>>
        SUCCESS
        FAIL
    }

    class CoreException {
        -ErrorType errorType
        -String customMessage
        +CoreException(ErrorType)
        +CoreException(ErrorType, String)
        +getErrorType() ErrorType
    }

    class ErrorType {
        <<enumeration>>
        INTERNAL_ERROR
        BAD_REQUEST
        NOT_FOUND
        CONFLICT
        UNAUTHORIZED
        USER_NOT_FOUND
        PASSWORD_MISMATCH
        BRAND_NOT_FOUND
        BRAND_ALREADY_EXISTS
        BRAND_DELETED
        PRODUCT_NOT_FOUND
        PRODUCT_DELETED
        INSUFFICIENT_STOCK
        ORDER_NOT_FOUND
        ORDER_ACCESS_DENIED
        ADMIN_UNAUTHORIZED
        BRAND_CHANGE_NOT_ALLOWED
        -HttpStatus status
        -String code
        -String message
    }

    ApiResponse --> Metadata
    Metadata --> Result
    CoreException --> ErrorType
```

**핵심 포인트:**
- **BaseEntity**: 모든 엔티티의 공통 필드 (id, timestamps, soft delete)
- **ApiResponse**: 통일된 API 응답 형식
- **ErrorType**: 도메인별 에러 코드 정의