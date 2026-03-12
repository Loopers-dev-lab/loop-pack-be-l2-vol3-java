# 아키텍처 레이어 분리 리팩토링 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Domain/Application/Presentation 레이어의 책임을 올바르게 분리한다.

**Architecture:**
- **Domain**: Entity, VO, Repository 인터페이스만 존재. 비즈니스 의사결정이 필요한 경우에만 Domain Service 허용.
- **Application**: Service(CRUD+트랜잭션 조율) + Facade(다중 도메인 조합). Application DTO(Info/Result record)를 통해 Presentation에 데이터 전달.
- **Presentation(interfaces)**: Controller는 Application 레이어만 의존. Dto는 Application DTO로부터 변환. Domain 모델 직접 참조 금지.

**Tech Stack:** Java 21, Spring Boot 3.4.4, JPA, JUnit 5

**판별 기준 (블로그 참고):**
- 비즈니스 의사결정을 내리는 코드 → Domain (Entity/VO/Domain Service)
- 의사결정을 조율하고 외부 세계와 상호작용하는 코드 → Application Service
- 여러 도메인 서비스를 조합하는 코드 → Application Facade

---

## 현재 문제 요약

### 1. Service가 domain에 있지만 전부 Application Service
모든 Service가 Repository 의존 + 트랜잭션 관리 = 유스케이스 조율 역할.
비즈니스 의사결정은 Entity/VO가 이미 담당하고 있음.

### 2. Controller가 domain Service를 직접 호출 (레이어 위반)
| Controller | domain Service 직접 호출 |
|---|---|
| `BrandV1Controller` | `BrandService` |
| `BrandAdminV1Controller` | `BrandService` |
| `ProductAdminV1Controller` | `ProductService` |
| `OrderV1Controller` | `OrderService`, `MemberAuthService` |
| `OrderAdminV1Controller` | `OrderService` |

### 3. Presentation Dto가 Domain Model 직접 참조
| Dto | domain 모델 직접 참조 |
|---|---|
| `BrandV1Dto.BrandResponse` | `BrandModel` |
| `BrandAdminV1Dto.BrandResponse` | `BrandModel` |
| `OrderV1Dto.OrderResponse` | `OrderModel`, `OrderItemModel` |
| `OrderV1Dto.OrderSummaryResponse` | `OrderModel` |
| `OrderAdminV1Dto.*` | `OrderModel`, `OrderItemModel` |

---

## 리팩토링 후 목표 구조

```
domain/
├── brand/
│   ├── BrandModel.java, BrandName.java, BrandRepository.java
├── example/
│   ├── ExampleModel.java, ExampleRepository.java
├── like/
│   ├── LikeModel.java, LikeRepository.java
├── member/
│   ├── MemberModel.java, LoginId.java, Email.java, MemberName.java, Password.java
│   └── MemberRepository.java
├── order/
│   ├── OrderModel.java, OrderItemModel.java, OrderStatus.java
│   └── OrderRepository.java, OrderItemRepository.java
├── product/
│   ├── ProductModel.java, Money.java, ProductSortType.java, ProductRepository.java
└── stock/
    ├── StockModel.java, StockStatus.java, StockRepository.java

application/
├── brand/
│   ├── BrandService.java (moved from domain)
│   ├── BrandFacade.java (cross-domain: brand + product 삭제)
│   └── BrandInfo.java
├── example/
│   ├── ExampleService.java (moved from domain, ExampleFacade 합침)
│   └── ExampleInfo.java
├── like/
│   ├── LikeService.java (moved from domain)
│   ├── LikeFacade.java (cross-domain: like + product)
│   └── LikeWithProduct.java
├── member/
│   ├── MemberSignupService.java (moved from domain)
│   ├── MemberAuthService.java (moved from domain)
│   ├── MemberPasswordService.java (moved from domain)
│   ├── MemberFacade.java
│   └── MemberInfo.java
├── order/
│   ├── OrderService.java (moved from domain)
│   ├── OrderFacade.java (cross-domain: order + product + stock)
│   ├── OrderItemCommand.java, OrderResult.java
│   └── OrderInfo.java (NEW)
├── product/
│   ├── ProductService.java (moved from domain)
│   ├── ProductFacade.java (cross-domain: product + brand + stock)
│   └── ProductDetail.java
└── stock/
    └── StockService.java (moved from domain)

interfaces/api/  (변경사항: Controller → application만 의존, Dto → application DTO만 참조)
```

---

## Task 1: Example 도메인 (패턴 확립)

가장 단순한 도메인으로 리팩토링 패턴을 확립한다.
ExampleFacade는 pass-through이므로 ExampleService에 합친다.

**Files:**
- Move: `domain/example/ExampleService.java` → `application/example/ExampleService.java`
- Delete: `application/example/ExampleFacade.java` (ExampleService에 합침)
- Modify: `interfaces/api/example/ExampleV1Controller.java`
- Move test: `domain/example/ExampleServiceIntegrationTest.java` → `application/example/ExampleServiceIntegrationTest.java`

**Step 1: ExampleService를 application으로 이동 + ExampleFacade 합침**

`application/example/ExampleService.java`:
```java
package com.loopers.application.example;

import com.loopers.domain.example.ExampleModel;
import com.loopers.domain.example.ExampleRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class ExampleService {

    private final ExampleRepository exampleRepository;

    @Transactional(readOnly = true)
    public ExampleInfo getExample(Long id) {
        ExampleModel example = exampleRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 예시를 찾을 수 없습니다."));
        return ExampleInfo.from(example);
    }
}
```

**Step 2: ExampleV1Controller 수정 (Facade → Service)**

```java
package com.loopers.interfaces.api.example;

import com.loopers.application.example.ExampleInfo;
import com.loopers.application.example.ExampleService;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/examples")
public class ExampleV1Controller implements ExampleV1ApiSpec {

    private final ExampleService exampleService;

    @GetMapping("/{exampleId}")
    @Override
    public ApiResponse<ExampleV1Dto.ExampleResponse> getExample(
        @PathVariable(value = "exampleId") Long exampleId
    ) {
        ExampleInfo info = exampleService.getExample(exampleId);
        ExampleV1Dto.ExampleResponse response = ExampleV1Dto.ExampleResponse.from(info);
        return ApiResponse.success(response);
    }
}
```

**Step 3: domain/example/ExampleService.java, application/example/ExampleFacade.java 삭제**

**Step 4: 테스트 패키지 이동 + import 수정**
- `ExampleServiceIntegrationTest` → `application/example/` 로 이동, import 수정

**Step 5: 테스트 실행**

```bash
./gradlew test --tests "*Example*"
```
Expected: ALL PASS

**Step 6: 커밋**

```bash
git add -A
git commit -m "refactor: Example 도메인 레이어 분리 - Service를 application으로 이동, pass-through Facade 제거"
```

---

## Task 2: Stock 도메인

Controller 없음. Service 이동만 하면 됨.

**Files:**
- Move: `domain/stock/StockService.java` → `application/stock/StockService.java`
- Modify: `application/product/ProductFacade.java` (import 변경)
- Modify: `application/order/OrderFacade.java` (import 변경)
- Move test: `domain/stock/StockServiceTest.java` → `application/stock/StockServiceTest.java`

**Step 1: StockService를 application으로 이동**

`application/stock/StockService.java`:
```java
package com.loopers.application.stock;

import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class StockService {

    private final StockRepository stockRepository;

    @Transactional
    public StockModel create(Long productId, int quantity) {
        StockModel stock = new StockModel(productId, quantity);
        return stockRepository.save(stock);
    }

    @Transactional(readOnly = true)
    public StockModel getByProductId(Long productId) {
        return stockRepository.findByProductId(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "재고를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public Map<Long, StockModel> getByProductIds(List<Long> productIds) {
        return stockRepository.findAllByProductIdIn(productIds)
            .stream()
            .collect(Collectors.toMap(StockModel::productId, stock -> stock));
    }
}
```

**Step 2: ProductFacade, OrderFacade import 수정**

```java
// 변경 전
import com.loopers.domain.stock.StockService;
// 변경 후
import com.loopers.application.stock.StockService;
```

**Step 3: domain/stock/StockService.java 삭제**

**Step 4: StockServiceTest 패키지 이동 + import 수정**

**Step 5: 테스트 실행**

```bash
./gradlew test --tests "*Stock*" --tests "*Product*" --tests "*Order*"
```
Expected: ALL PASS

**Step 6: 커밋**

```bash
git commit -m "refactor: Stock 도메인 레이어 분리 - StockService를 application으로 이동"
```

---

## Task 3: Like 도메인

**Files:**
- Move: `domain/like/LikeService.java` → `application/like/LikeService.java`
- Modify: `application/like/LikeFacade.java` (import 변경)
- Move test: (LikeService 단독 테스트 없음, LikeFacadeIntegrationTest만 존재)

**Step 1: LikeService를 application으로 이동**

`application/like/LikeService.java`:
```java
package com.loopers.application.like;

import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class LikeService {

    private final LikeRepository likeRepository;

    public LikeModel save(LikeModel like) {
        return likeRepository.save(like);
    }

    public Optional<LikeModel> findByUserIdAndProductId(Long userId, Long productId) {
        return likeRepository.findByUserIdAndProductId(userId, productId);
    }

    public Optional<LikeModel> findActiveLike(Long userId, Long productId) {
        return likeRepository.findByUserIdAndProductIdAndDeletedAtIsNull(userId, productId);
    }

    public Page<LikeModel> getMyLikes(Long userId, Pageable pageable) {
        return likeRepository.findActiveLikesWithActiveProduct(userId, pageable);
    }
}
```

**Step 2: LikeFacade import 수정**

```java
// 변경 전
import com.loopers.domain.like.LikeService;
// 변경 후
// LikeService가 같은 패키지(application.like)에 있으므로 import 불필요
```

**Step 3: domain/like/LikeService.java 삭제**

**Step 4: 테스트 실행**

```bash
./gradlew test --tests "*Like*"
```
Expected: ALL PASS

**Step 5: 커밋**

```bash
git commit -m "refactor: Like 도메인 레이어 분리 - LikeService를 application으로 이동"
```

---

## Task 4: Brand 도메인

이 도메인부터 Presentation 레이어 위반도 함께 수정한다.

**문제점:**
1. `BrandV1Controller` → `BrandService` 직접 호출
2. `BrandAdminV1Controller` → `BrandService` + `BrandFacade` 혼용
3. `BrandV1Dto.BrandResponse.from(BrandModel)` → domain 모델 직접 참조
4. `BrandAdminV1Dto.BrandResponse.from(BrandModel)` → domain 모델 직접 참조

**Files:**
- Move: `domain/brand/BrandService.java` → `application/brand/BrandService.java`
- Modify: `application/brand/BrandFacade.java` (import 변경)
- Modify: `interfaces/api/brand/BrandV1Controller.java` (BrandFacade 경유)
- Modify: `interfaces/api/brand/BrandV1Dto.java` (BrandInfo로 변환)
- Modify: `interfaces/api/brand/admin/BrandAdminV1Controller.java` (BrandFacade만 의존)
- Modify: `interfaces/api/brand/admin/BrandAdminV1Dto.java` (BrandInfo로 변환)
- Move tests: `domain/brand/BrandServiceTest.java`, `BrandServiceIntegrationTest.java` → `application/brand/`

**Step 1: BrandService를 application으로 이동**

`application/brand/BrandService.java`:
```java
package com.loopers.application.brand;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandName;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class BrandService {

    private final BrandRepository brandRepository;

    @Transactional
    public BrandModel register(String name, String description) {
        BrandName brandName = new BrandName(name);

        brandRepository.findByName(name).ifPresent(existing -> {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 브랜드 이름입니다.");
        });

        BrandModel brand = new BrandModel(brandName, description);
        return brandRepository.save(brand);
    }

    @Transactional(readOnly = true)
    public BrandModel getBrand(Long brandId) {
        BrandModel brand = findById(brandId);
        if (brand.getDeletedAt() != null) {
            throw new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다.");
        }
        return brand;
    }

    @Transactional(readOnly = true)
    public BrandModel getBrandForAdmin(Long brandId) {
        return findById(brandId);
    }

    @Transactional
    public BrandModel update(Long brandId, String name, String description) {
        BrandModel brand = findById(brandId);
        BrandName newName = new BrandName(name);

        if (!brand.name().equals(newName)) {
            brandRepository.findByName(name).ifPresent(existing -> {
                throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 브랜드 이름입니다.");
            });
        }

        brand.update(newName, description);
        return brand;
    }

    @Transactional
    public void delete(Long brandId) {
        BrandModel brand = findById(brandId);
        brand.delete();
    }

    @Transactional(readOnly = true)
    public Page<BrandModel> getAll(Pageable pageable) {
        return brandRepository.findAll(pageable);
    }

    private BrandModel findById(Long brandId) {
        return brandRepository.findById(brandId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));
    }
}
```

**Step 2: BrandFacade 수정 - 누락된 메서드 추가 (Controller가 Facade만 호출하도록)**

`application/brand/BrandFacade.java`:
```java
package com.loopers.application.brand;

import com.loopers.application.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class BrandFacade {

    private final BrandService brandService;
    private final ProductService productService;

    public BrandInfo register(String name, String description) {
        return BrandInfo.from(brandService.register(name, description));
    }

    public BrandInfo getBrand(Long brandId) {
        return BrandInfo.from(brandService.getBrand(brandId));
    }

    public BrandInfo getBrandForAdmin(Long brandId) {
        return BrandInfo.from(brandService.getBrandForAdmin(brandId));
    }

    public BrandInfo update(Long brandId, String name, String description) {
        return BrandInfo.from(brandService.update(brandId, name, description));
    }

    @Transactional
    public void delete(Long brandId) {
        brandService.delete(brandId);
        productService.deleteAllByBrandId(brandId);
    }

    public Page<BrandInfo> getAll(Pageable pageable) {
        return brandService.getAll(pageable).map(BrandInfo::from);
    }
}
```

**Step 3: BrandV1Controller 수정 - BrandFacade만 의존**

```java
package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandFacade;
import com.loopers.application.brand.BrandInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/brands")
public class BrandV1Controller {

    private final BrandFacade brandFacade;

    @GetMapping("/{brandId}")
    public ApiResponse<BrandV1Dto.BrandResponse> getBrand(@PathVariable Long brandId) {
        BrandInfo info = brandFacade.getBrand(brandId);
        return ApiResponse.success(BrandV1Dto.BrandResponse.from(info));
    }
}
```

**Step 4: BrandV1Dto 수정 - BrandInfo로 변환**

```java
package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandInfo;

public class BrandV1Dto {

    public record BrandResponse(
        Long id,
        String name,
        String description
    ) {
        public static BrandResponse from(BrandInfo info) {
            return new BrandResponse(info.id(), info.name(), info.description());
        }
    }
}
```

**Step 5: BrandAdminV1Controller 수정 - BrandFacade만 의존**

```java
package com.loopers.interfaces.api.brand.admin;

import com.loopers.application.brand.BrandFacade;
import com.loopers.application.brand.BrandInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.AdminInfo;
import com.loopers.interfaces.auth.AdminUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/brands")
public class BrandAdminV1Controller {

    private final BrandFacade brandFacade;

    @PostMapping
    public ApiResponse<BrandAdminV1Dto.BrandResponse> create(
        @AdminUser AdminInfo admin,
        @RequestBody BrandAdminV1Dto.CreateRequest request
    ) {
        BrandInfo info = brandFacade.register(request.name(), request.description());
        return ApiResponse.success(BrandAdminV1Dto.BrandResponse.from(info));
    }

    @GetMapping
    public ApiResponse<Page<BrandAdminV1Dto.BrandResponse>> getAll(
        @AdminUser AdminInfo admin,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Page<BrandInfo> result = brandFacade.getAll(PageRequest.of(page, size));
        return ApiResponse.success(result.map(BrandAdminV1Dto.BrandResponse::from));
    }

    @GetMapping("/{brandId}")
    public ApiResponse<BrandAdminV1Dto.BrandResponse> getBrand(
        @AdminUser AdminInfo admin,
        @PathVariable Long brandId
    ) {
        BrandInfo info = brandFacade.getBrandForAdmin(brandId);
        return ApiResponse.success(BrandAdminV1Dto.BrandResponse.from(info));
    }

    @PutMapping("/{brandId}")
    public ApiResponse<BrandAdminV1Dto.BrandResponse> update(
        @AdminUser AdminInfo admin,
        @PathVariable Long brandId,
        @RequestBody BrandAdminV1Dto.UpdateRequest request
    ) {
        BrandInfo info = brandFacade.update(brandId, request.name(), request.description());
        return ApiResponse.success(BrandAdminV1Dto.BrandResponse.from(info));
    }

    @DeleteMapping("/{brandId}")
    public ApiResponse<Object> delete(@AdminUser AdminInfo admin, @PathVariable Long brandId) {
        brandFacade.delete(brandId);
        return ApiResponse.success();
    }
}
```

**Step 6: BrandAdminV1Dto 수정 - BrandInfo로 변환**

```java
package com.loopers.interfaces.api.brand.admin;

import com.loopers.application.brand.BrandInfo;

import java.time.ZonedDateTime;

public class BrandAdminV1Dto {

    public record CreateRequest(String name, String description) {}

    public record UpdateRequest(String name, String description) {}

    public record BrandResponse(
        Long id, String name, String description,
        ZonedDateTime createdAt, ZonedDateTime updatedAt, ZonedDateTime deletedAt
    ) {
        public static BrandResponse from(BrandInfo info) {
            return new BrandResponse(
                info.id(), info.name(), info.description(),
                info.createdAt(), info.updatedAt(), info.deletedAt()
            );
        }
    }
}
```

**Step 7: domain/brand/BrandService.java 삭제**

**Step 8: 테스트 패키지 이동 + import 수정**
- `BrandServiceTest.java` → `application/brand/BrandServiceTest.java`
- `BrandServiceIntegrationTest.java` → `application/brand/BrandServiceIntegrationTest.java`

**Step 9: 테스트 실행**

```bash
./gradlew test --tests "*Brand*"
```
Expected: ALL PASS

**Step 10: 커밋**

```bash
git commit -m "refactor: Brand 도메인 레이어 분리 - Service를 application으로 이동, Controller는 Facade만 의존, Dto는 BrandInfo로 변환"
```

---

## Task 5: Product 도메인

**문제점:**
1. `ProductAdminV1Controller` → `ProductService` + `ProductFacade` 혼용
2. ProductFacade에 없는 update/delete 메서드를 Controller에서 ProductService 직접 호출

**Files:**
- Move: `domain/product/ProductService.java` → `application/product/ProductService.java`
- Modify: `application/product/ProductFacade.java` (import + update/delete 메서드 추가)
- Modify: `interfaces/api/product/admin/ProductAdminV1Controller.java` (ProductFacade만 의존)
- Move tests: `domain/product/ProductServiceTest.java`, `ProductServiceIntegrationTest.java` → `application/product/`

**Step 1: ProductService를 application으로 이동**

`application/product/ProductService.java` — 기존 코드 그대로, 패키지만 변경:
```java
package com.loopers.application.product;
// ... (기존 코드 동일, import만 domain.product.* 유지)
```

**Step 2: ProductFacade에 update/delete 메서드 추가**

```java
// ProductFacade에 추가
@Transactional
public ProductDetail update(Long productId, String name, String description, Money price) {
    productService.update(productId, name, description, price);
    return getProductForAdmin(productId);
}

public void delete(Long productId) {
    productService.delete(productId);
}
```

**Step 3: ProductAdminV1Controller 수정 - ProductFacade만 의존**

```java
package com.loopers.interfaces.api.product.admin;

import com.loopers.application.product.ProductDetail;
import com.loopers.application.product.ProductFacade;
import com.loopers.domain.product.Money;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.AdminInfo;
import com.loopers.interfaces.auth.AdminUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/products")
public class ProductAdminV1Controller {

    private final ProductFacade productFacade;

    @PostMapping
    public ApiResponse<ProductAdminV1Dto.ProductResponse> create(
        @AdminUser AdminInfo admin,
        @RequestBody ProductAdminV1Dto.CreateRequest request
    ) {
        var product = productFacade.register(
            request.name(), request.description(), new Money(request.price()),
            request.brandId(), request.initialStock()
        );
        ProductDetail detail = productFacade.getProductForAdmin(product.getId());
        return ApiResponse.success(ProductAdminV1Dto.ProductResponse.from(detail));
    }

    @GetMapping
    public ApiResponse<Page<ProductAdminV1Dto.ProductResponse>> getAll(
        @AdminUser AdminInfo admin,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) Long brandId
    ) {
        Page<ProductDetail> result = productFacade.getProductsForAdmin(brandId, PageRequest.of(page, size));
        return ApiResponse.success(result.map(ProductAdminV1Dto.ProductResponse::from));
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductAdminV1Dto.ProductResponse> getProduct(
        @AdminUser AdminInfo admin,
        @PathVariable Long productId
    ) {
        ProductDetail detail = productFacade.getProductForAdmin(productId);
        return ApiResponse.success(ProductAdminV1Dto.ProductResponse.from(detail));
    }

    @PutMapping("/{productId}")
    public ApiResponse<ProductAdminV1Dto.ProductResponse> update(
        @AdminUser AdminInfo admin,
        @PathVariable Long productId,
        @RequestBody ProductAdminV1Dto.UpdateRequest request
    ) {
        ProductDetail detail = productFacade.update(productId, request.name(), request.description(), new Money(request.price()));
        return ApiResponse.success(ProductAdminV1Dto.ProductResponse.from(detail));
    }

    @DeleteMapping("/{productId}")
    public ApiResponse<Object> delete(@AdminUser AdminInfo admin, @PathVariable Long productId) {
        productFacade.delete(productId);
        return ApiResponse.success();
    }
}
```

**Step 4: domain/product/ProductService.java 삭제**

**Step 5: 테스트 패키지 이동 + import 수정**

**Step 6: 테스트 실행**

```bash
./gradlew test --tests "*Product*"
```
Expected: ALL PASS

**Step 7: 커밋**

```bash
git commit -m "refactor: Product 도메인 레이어 분리 - Service를 application으로 이동, AdminController는 Facade만 의존"
```

---

## Task 6: Order 도메인

**문제점:**
1. `OrderV1Controller` → `OrderService`, `MemberAuthService` 직접 호출
2. `OrderAdminV1Controller` → `OrderService` 직접 호출
3. `OrderV1Dto`, `OrderAdminV1Dto` → `OrderModel`, `OrderItemModel` 직접 참조
4. `OrderModel`에 소유권 검증 로직이 Service에 새어나옴

**Files:**
- Move: `domain/order/OrderService.java` → `application/order/OrderService.java`
- Modify: `domain/order/OrderModel.java` (validateOwner 추가)
- Create: `application/order/OrderInfo.java` (NEW)
- Modify: `application/order/OrderFacade.java` (조회 메서드 추가)
- Modify: `interfaces/api/order/OrderV1Controller.java`
- Modify: `interfaces/api/order/OrderV1Dto.java`
- Modify: `interfaces/api/order/admin/OrderAdminV1Controller.java`
- Modify: `interfaces/api/order/admin/OrderAdminV1Dto.java`

**Step 1: OrderModel에 소유권 검증 메서드 추가**

`domain/order/OrderModel.java`에 추가:
```java
public void validateOwner(Long userId) {
    if (!this.userId.equals(userId)) {
        throw new CoreException(ErrorType.BAD_REQUEST, "본인의 주문만 조회할 수 있습니다.");
    }
}
```

**Step 2: OrderInfo 생성 (application DTO)**

`application/order/OrderInfo.java`:
```java
package com.loopers.application.order;

import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;

import java.time.ZonedDateTime;
import java.util.List;

public record OrderInfo(
    Long orderId,
    Long userId,
    String status,
    int totalAmount,
    List<OrderItemInfo> items,
    ZonedDateTime createdAt
) {

    public static OrderInfo from(OrderModel order, List<OrderItemModel> items) {
        List<OrderItemInfo> itemInfos = items.stream()
            .map(OrderItemInfo::from)
            .toList();
        return new OrderInfo(
            order.getId(), order.userId(), order.status().name(),
            order.totalAmount().value(), itemInfos, order.getCreatedAt()
        );
    }

    public static OrderInfo summaryFrom(OrderModel order) {
        return new OrderInfo(
            order.getId(), order.userId(), order.status().name(),
            order.totalAmount().value(), List.of(), order.getCreatedAt()
        );
    }

    public record OrderItemInfo(
        Long productId, String productName, int productPrice, int quantity, int subtotal
    ) {
        public static OrderItemInfo from(OrderItemModel item) {
            return new OrderItemInfo(
                item.productId(), item.productName(),
                item.productPrice().value(), item.quantity(), item.subtotal().value()
            );
        }
    }
}
```

**Step 3: OrderService를 application으로 이동 + validateOwner 사용**

`application/order/OrderService.java`:
```java
package com.loopers.application.order;

import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderItemRepository;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    @Transactional
    public OrderModel save(OrderModel order) {
        return orderRepository.save(order);
    }

    @Transactional
    public List<OrderItemModel> saveAllItems(List<OrderItemModel> orderItems) {
        return orderItemRepository.saveAll(orderItems);
    }

    @Transactional(readOnly = true)
    public OrderModel getOrder(Long orderId, Long userId) {
        OrderModel order = findById(orderId);
        order.validateOwner(userId);
        return order;
    }

    @Transactional(readOnly = true)
    public OrderModel getOrderForAdmin(Long orderId) {
        return findById(orderId);
    }

    @Transactional(readOnly = true)
    public List<OrderModel> getOrdersByUser(Long userId, ZonedDateTime startAt, ZonedDateTime endAt) {
        return orderRepository.findAllByUserIdAndCreatedAtBetween(userId, startAt, endAt);
    }

    @Transactional(readOnly = true)
    public Page<OrderModel> getAllForAdmin(Pageable pageable) {
        return orderRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<OrderItemModel> getOrderItems(Long orderId) {
        return orderItemRepository.findAllByOrderId(orderId);
    }

    private OrderModel findById(Long orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
    }
}
```

**Step 4: OrderFacade에 조회 메서드 추가**

```java
package com.loopers.application.order;

import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.ProductModel;
import com.loopers.application.product.ProductService;
import com.loopers.application.stock.StockService;
import com.loopers.domain.stock.StockModel;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final OrderService orderService;
    private final ProductService productService;
    private final StockService stockService;

    @Transactional
    public OrderResult placeOrder(Long userId, List<OrderItemCommand> commands) {
        Money totalAmount = Money.ZERO;
        List<SnapshotHolder> snapshots = new ArrayList<>();

        for (OrderItemCommand cmd : commands) {
            ProductModel product = productService.getProduct(cmd.productId());

            StockModel stock = stockService.getByProductId(cmd.productId());
            stock.decrease(cmd.quantity());

            Money subtotal = product.price().multiply(cmd.quantity());
            totalAmount = totalAmount.add(subtotal);

            snapshots.add(new SnapshotHolder(
                product.getId(), product.name(), product.price(), cmd.quantity()
            ));
        }

        OrderModel order = orderService.save(new OrderModel(userId, totalAmount));

        List<OrderItemModel> items = snapshots.stream()
            .map(s -> new OrderItemModel(
                order.getId(), s.productId(), s.productName(), s.productPrice(), s.quantity()
            ))
            .toList();

        List<OrderItemModel> savedItems = orderService.saveAllItems(items);

        return OrderResult.of(order, savedItems);
    }

    @Transactional(readOnly = true)
    public OrderInfo getOrder(Long orderId, Long userId) {
        OrderModel order = orderService.getOrder(orderId, userId);
        List<OrderItemModel> items = orderService.getOrderItems(orderId);
        return OrderInfo.from(order, items);
    }

    @Transactional(readOnly = true)
    public List<OrderInfo> getOrdersByUser(Long userId, ZonedDateTime startAt, ZonedDateTime endAt) {
        List<OrderModel> orders = orderService.getOrdersByUser(userId, startAt, endAt);
        return orders.stream()
            .map(OrderInfo::summaryFrom)
            .toList();
    }

    @Transactional(readOnly = true)
    public OrderInfo getOrderForAdmin(Long orderId) {
        OrderModel order = orderService.getOrderForAdmin(orderId);
        List<OrderItemModel> items = orderService.getOrderItems(orderId);
        return OrderInfo.from(order, items);
    }

    @Transactional(readOnly = true)
    public Page<OrderInfo> getAllForAdmin(Pageable pageable) {
        Page<OrderModel> orders = orderService.getAllForAdmin(pageable);
        return orders.map(OrderInfo::summaryFrom);
    }

    private record SnapshotHolder(
        Long productId, String productName, Money productPrice, int quantity
    ) {}
}
```

**Step 5: OrderV1Controller 수정 - OrderFacade만 의존**

```java
package com.loopers.interfaces.api.order;

import com.loopers.application.member.MemberFacade;
import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.application.order.OrderResult;
import com.loopers.domain.member.MemberModel;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@RestController
public class OrderV1Controller {

    private final OrderFacade orderFacade;
    private final MemberFacade memberFacade;

    @PostMapping("/api/v1/orders")
    public ApiResponse<OrderV1Dto.OrderResponse> createOrder(
        @RequestBody OrderV1Dto.CreateRequest request,
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String password
    ) {
        MemberModel member = memberFacade.authenticate(loginId, password);
        OrderResult result = orderFacade.placeOrder(member.getId(), request.toCommands());
        return ApiResponse.success(OrderV1Dto.OrderResponse.fromResult(result));
    }

    @GetMapping("/api/v1/orders")
    public ApiResponse<List<OrderV1Dto.OrderSummaryResponse>> getMyOrders(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String password,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startAt,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endAt
    ) {
        MemberModel member = memberFacade.authenticate(loginId, password);
        ZonedDateTime start = startAt.atStartOfDay(ZoneId.of("Asia/Seoul"));
        ZonedDateTime end = endAt.plusDays(1).atStartOfDay(ZoneId.of("Asia/Seoul"));

        List<OrderInfo> orders = orderFacade.getOrdersByUser(member.getId(), start, end);
        List<OrderV1Dto.OrderSummaryResponse> response = orders.stream()
            .map(OrderV1Dto.OrderSummaryResponse::from)
            .toList();
        return ApiResponse.success(response);
    }

    @GetMapping("/api/v1/orders/{orderId}")
    public ApiResponse<OrderV1Dto.OrderResponse> getOrder(
        @PathVariable Long orderId,
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String password
    ) {
        MemberModel member = memberFacade.authenticate(loginId, password);
        OrderInfo info = orderFacade.getOrder(orderId, member.getId());
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(info));
    }
}
```

**Step 6: OrderV1Dto 수정 - OrderInfo로 변환**

```java
package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderItemCommand;
import com.loopers.application.order.OrderInfo;
import com.loopers.application.order.OrderResult;

import java.time.ZonedDateTime;
import java.util.List;

public class OrderV1Dto {

    public record CreateRequest(List<OrderItemRequest> items) {
        public List<OrderItemCommand> toCommands() {
            return items.stream()
                .map(item -> new OrderItemCommand(item.productId(), item.quantity()))
                .toList();
        }
    }

    public record OrderItemRequest(Long productId, int quantity) {}

    public record OrderResponse(
        Long orderId, String status, int totalAmount,
        List<OrderItemResponse> items, ZonedDateTime createdAt
    ) {
        public static OrderResponse from(OrderInfo info) {
            List<OrderItemResponse> items = info.items().stream()
                .map(OrderItemResponse::from)
                .toList();
            return new OrderResponse(
                info.orderId(), info.status(), info.totalAmount(), items, info.createdAt()
            );
        }

        public static OrderResponse fromResult(OrderResult result) {
            List<OrderItemResponse> items = result.items().stream()
                .map(item -> new OrderItemResponse(
                    item.productId(), item.productName(),
                    item.productPrice().value(), item.quantity(), item.subtotal().value()
                ))
                .toList();
            return new OrderResponse(
                result.order().getId(), result.order().status().name(),
                result.order().totalAmount().value(), items, result.order().getCreatedAt()
            );
        }
    }

    public record OrderSummaryResponse(
        Long orderId, String status, int totalAmount, ZonedDateTime createdAt
    ) {
        public static OrderSummaryResponse from(OrderInfo info) {
            return new OrderSummaryResponse(
                info.orderId(), info.status(), info.totalAmount(), info.createdAt()
            );
        }
    }

    public record OrderItemResponse(
        Long productId, String productName, int productPrice, int quantity, int subtotal
    ) {
        public static OrderItemResponse from(OrderInfo.OrderItemInfo item) {
            return new OrderItemResponse(
                item.productId(), item.productName(),
                item.productPrice(), item.quantity(), item.subtotal()
            );
        }
    }
}
```

**Step 7: OrderAdminV1Controller 수정 - OrderFacade만 의존**

```java
package com.loopers.interfaces.api.order.admin;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/orders")
public class OrderAdminV1Controller {

    private final OrderFacade orderFacade;

    @GetMapping
    public ApiResponse<Page<OrderAdminV1Dto.OrderSummaryResponse>> getAll(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Page<OrderInfo> orders = orderFacade.getAllForAdmin(PageRequest.of(page, size));
        return ApiResponse.success(orders.map(OrderAdminV1Dto.OrderSummaryResponse::from));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderAdminV1Dto.OrderResponse> getOrder(@PathVariable Long orderId) {
        OrderInfo info = orderFacade.getOrderForAdmin(orderId);
        return ApiResponse.success(OrderAdminV1Dto.OrderResponse.from(info));
    }
}
```

**Step 8: OrderAdminV1Dto 수정 - OrderInfo로 변환**

```java
package com.loopers.interfaces.api.order.admin;

import com.loopers.application.order.OrderInfo;

import java.time.ZonedDateTime;
import java.util.List;

public class OrderAdminV1Dto {

    public record OrderResponse(
        Long orderId, Long userId, String status, int totalAmount,
        List<OrderItemResponse> items, ZonedDateTime createdAt
    ) {
        public static OrderResponse from(OrderInfo info) {
            List<OrderItemResponse> items = info.items().stream()
                .map(OrderItemResponse::from)
                .toList();
            return new OrderResponse(
                info.orderId(), info.userId(), info.status(),
                info.totalAmount(), items, info.createdAt()
            );
        }
    }

    public record OrderSummaryResponse(
        Long orderId, Long userId, String status, int totalAmount, ZonedDateTime createdAt
    ) {
        public static OrderSummaryResponse from(OrderInfo info) {
            return new OrderSummaryResponse(
                info.orderId(), info.userId(), info.status(),
                info.totalAmount(), info.createdAt()
            );
        }
    }

    public record OrderItemResponse(
        Long productId, String productName, int productPrice, int quantity, int subtotal
    ) {
        public static OrderItemResponse from(OrderInfo.OrderItemInfo item) {
            return new OrderItemResponse(
                item.productId(), item.productName(),
                item.productPrice(), item.quantity(), item.subtotal()
            );
        }
    }
}
```

**Step 9: domain/order/OrderService.java 삭제**

**Step 10: 테스트 실행**

```bash
./gradlew test --tests "*Order*"
```
Expected: ALL PASS

**Step 11: 커밋**

```bash
git commit -m "refactor: Order 도메인 레이어 분리 - Service를 application으로 이동, validateOwner를 엔티티로 이관, Controller/Dto가 OrderInfo 경유"
```

---

## Task 7: Member 도메인

**문제점:**
1. `MemberAuthService`가 `domain/`에 있지만 application service
2. `OrderV1Controller`가 `MemberAuthService` 직접 호출 → Task 6에서 `MemberFacade.authenticate()` 경유로 변경
3. `MemberFacade`에 `authenticate()` 메서드 추가 필요

**Files:**
- Move: `domain/member/MemberSignupService.java` → `application/member/MemberSignupService.java`
- Move: `domain/member/MemberAuthService.java` → `application/member/MemberAuthService.java`
- Move: `domain/member/MemberPasswordService.java` → `application/member/MemberPasswordService.java`
- Modify: `application/member/MemberFacade.java` (authenticate 추가)
- Move tests: 6개 테스트 파일 이동

**Step 1: MemberSignupService를 application으로 이동**

`application/member/MemberSignupService.java`:
```java
package com.loopers.application.member;
// ... (기존 코드 동일, 패키지만 변경)
```

**Step 2: MemberAuthService를 application으로 이동**

`application/member/MemberAuthService.java`:
```java
package com.loopers.application.member;
// ... (기존 코드 동일, 패키지만 변경)
```

**Step 3: MemberPasswordService를 application으로 이동**

`application/member/MemberPasswordService.java`:
```java
package com.loopers.application.member;
// ... (기존 코드 동일, 패키지만 변경)
```

**Step 4: MemberFacade에 authenticate() 추가**

```java
package com.loopers.application.member;

import com.loopers.domain.member.MemberModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@RequiredArgsConstructor
@Component
public class MemberFacade {

    private final MemberSignupService memberSignupService;
    private final MemberAuthService memberAuthService;
    private final MemberPasswordService memberPasswordService;

    public MemberInfo signup(String loginId, String password, String name,
                             LocalDate birthDate, String email) {
        MemberModel member = memberSignupService.signup(loginId, password, name, birthDate, email);
        return MemberInfo.from(member);
    }

    public MemberModel authenticate(String loginId, String password) {
        return memberAuthService.authenticate(loginId, password);
    }

    public MemberInfo getMyInfo(MemberModel member) {
        return MemberInfo.fromWithMaskedName(member);
    }

    public void changePassword(MemberModel member, String currentPassword, String newPassword) {
        memberPasswordService.changePassword(member, currentPassword, newPassword);
    }
}
```

**Step 5: domain/member/ 에서 3개 Service 파일 삭제**

**Step 6: 테스트 패키지 이동 (6개 파일)**
- `MemberSignupServiceTest.java` → `application/member/`
- `MemberSignupServiceIntegrationTest.java` → `application/member/`
- `MemberAuthServiceTest.java` → `application/member/`
- `MemberAuthServiceIntegrationTest.java` → `application/member/`
- `MemberPasswordServiceTest.java` → `application/member/`
- `MemberPasswordServiceIntegrationTest.java` → `application/member/`

**Step 7: 테스트 실행**

```bash
./gradlew test --tests "*Member*"
```
Expected: ALL PASS

**Step 8: 커밋**

```bash
git commit -m "refactor: Member 도메인 레이어 분리 - 3개 Service를 application으로 이동, MemberFacade에 authenticate 추가"
```

---

## Task 8: 전체 테스트 + 레이어 위반 검증

**Step 1: 전체 테스트 실행**

```bash
./gradlew test
```
Expected: ALL PASS

**Step 2: 레이어 위반 확인 - domain에 Service가 남아있지 않은지**

```bash
# domain 패키지에 Service 클래스가 없어야 함
find apps/commerce-api/src/main/java/com/loopers/domain -name "*Service*.java" -type f
```
Expected: 0 results

**Step 3: 레이어 위반 확인 - Controller가 domain Service를 import하지 않는지**

```bash
# interfaces 패키지에서 domain import 중 Service import가 없어야 함
grep -r "import com.loopers.domain.*Service" apps/commerce-api/src/main/java/com/loopers/interfaces/
```
Expected: 0 results

**Step 4: 레이어 위반 확인 - Dto가 domain Model을 직접 참조하지 않는지**

```bash
# Dto 파일에서 domain 모델 import가 없어야 함 (Money 같은 VO는 예외 가능)
grep -r "import com.loopers.domain.*Model" apps/commerce-api/src/main/java/com/loopers/interfaces/
```
Expected: MemberModel만 남음 (LoginMember 어노테이션 때문에 Controller에서 사용, 이건 인증 인프라 문제로 별도 처리)

**Step 5: 최종 커밋**

```bash
git commit -m "refactor: 아키텍처 레이어 분리 완료 - 전체 테스트 통과 및 레이어 위반 검증"
```

---

## 참고: 리팩토링 후 의존 방향

```
interfaces/api (Presentation)
    ↓ depends on
application (Service + Facade + Info/Result DTOs)
    ↓ depends on
domain (Entity + VO + Repository interface)
    ↑ implements
infrastructure (JPA Repository 구현체)
```

**Controller 의존 규칙:**
- Controller → Facade (다중 도메인 조합이 필요한 경우)
- Controller → Service (단일 도메인이고 Facade가 불필요한 경우) — 현재 해당 없음, 모든 Controller가 Facade 경유
- Controller ✗ domain Service/Model 직접 참조 금지

**Dto 변환 규칙:**
- Dto.from(Info/Result record) — application DTO로부터 변환
- Dto ✗ from(Entity/Model) 금지
