package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.product.ProductCursor;
import com.loopers.domain.product.ProductSortType;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.common.CursorEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController implements ProductApiSpec {

    private final ProductFacade productFacade;

    public ProductController(ProductFacade productFacade) {
        this.productFacade = productFacade;
    }

    @GetMapping
    @Override
    public ApiResponse<ProductResponse.ProductCursorListResponse> getProducts(
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) ProductSortType sort,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size
    ) {
        ProductSortType effectiveSort = sort != null ? sort : ProductSortType.LATEST;
        ProductCursor productCursor = decodeCursor(cursor, effectiveSort);

        ProductFacade.ProductCursorResult result = productFacade.getDisplayableProductsWithCursor(
                brandId, effectiveSort, productCursor, size);

        List<ProductResponse.ProductSummary> summaries = result.products().stream()
                .map(ProductResponse.ProductSummary::from)
                .toList();

        String nextCursor = null;
        if (result.hasNext() && !result.products().isEmpty()) {
            ProductInfo lastProduct = result.products().get(result.products().size() - 1);
            nextCursor = encodeCursor(effectiveSort, lastProduct);
        }

        return ApiResponse.success(new ProductResponse.ProductCursorListResponse(
                summaries,
                new ProductResponse.PagingInfo(result.hasNext(), nextCursor, size)));
    }

    @GetMapping("/{productId}")
    @Override
    public ApiResponse<ProductResponse.ProductDetail> getProduct(@PathVariable Long productId) {
        ProductFacade.ProductDetailResult result = productFacade.getProductDetail(productId);
        ProductInfo product = result.product();
        String brandName = result.brand().name();

        return ApiResponse.success(ProductResponse.ProductDetail.from(product, brandName));
    }

    private ProductCursor decodeCursor(String cursor, ProductSortType sort) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        Map<String, Object> data = CursorEncoder.decode(cursor);

        String cursorSort = (String) data.get("sort");
        if (!sort.name().equals(cursorSort)) {
            throw new IllegalArgumentException("커서의 정렬 타입이 요청과 일치하지 않습니다.");
        }

        Long id = ((Number) data.get("id")).longValue();

        return switch (sort) {
            case LATEST -> ProductCursor.ofLatest(ZonedDateTime.parse((String) data.get("sv")), id);
            case PRICE_ASC, PRICE_DESC -> ProductCursor.ofPrice(sort, ((Number) data.get("sv")).intValue(), id);
            case LIKES_DESC -> ProductCursor.ofLikes(((Number) data.get("sv")).intValue(), id);
        };
    }

    private String encodeCursor(ProductSortType sort, ProductInfo lastProduct) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sort", sort.name());
        data.put("id", lastProduct.id());

        Object sortValue = switch (sort) {
            case LATEST -> lastProduct.createdAt().toString();
            case PRICE_ASC, PRICE_DESC -> lastProduct.basePrice();
            case LIKES_DESC -> lastProduct.likeCount();
        };
        data.put("sv", sortValue);

        return CursorEncoder.encode(data);
    }
}
