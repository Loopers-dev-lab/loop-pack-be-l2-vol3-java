package com.loopers.interfaces.api.product;

import com.loopers.application.product.command.CreateProductCommand;
import com.loopers.application.product.command.UpdateProductCommand;
import com.loopers.application.product.view.ProductListView;
import com.loopers.application.product.view.ProductView;
import com.loopers.application.product.view.PublicProductListItemView;
import com.loopers.application.product.view.PublicProductListView;
import com.loopers.domain.product.Product;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public class ProductDto {

    public record CreateProductRequest(
            @NotBlank(message = "상품명은 필수입니다")
            String name,
            @NotNull(message = "가격은 필수입니다")
            @Min(value = 0, message = "가격은 0 이상이어야 합니다")
            Integer price,
            @NotNull(message = "재고는 필수입니다")
            @Min(value = 0, message = "재고는 0 이상이어야 합니다")
            Integer stock,
            String description,
            @NotNull(message = "카테고리 ID는 필수입니다")
            UUID categoryId,
            @NotNull(message = "브랜드 ID는 필수입니다")
            UUID brandId
    ) {
        public CreateProductCommand toCommand() {
            return new CreateProductCommand(name, price, stock, description, categoryId, brandId);
        }
    }

    public record ProductResponse(
            UUID id,
            String name,
            Integer price,
            Integer stock,
            String description,
            UUID categoryId,
            UUID brandId,
            BrandInfo brand,
            Integer likeCount,
            ZonedDateTime deletedAt
    ) {
        public static ProductResponse from(Product product) {
            return from(product, null);
        }

        public static ProductResponse from(ProductView productView) {
            return new ProductResponse(
                    productView.id(),
                    productView.name(),
                    productView.price(),
                    productView.stock(),
                    productView.description(),
                    productView.categoryId(),
                    productView.brandId(),
                    new BrandInfo(productView.brandId(), productView.brandName()),
                    productView.likeCount(),
                    productView.deletedAt()
            );
        }

        public static ProductResponse from(Product product, String brandName) {
            return new ProductResponse(
                    product.id(),
                    product.name(),
                    product.price(),
                    product.stock(),
                    product.description(),
                    product.categoryId(),
                    product.brandId(),
                    new BrandInfo(product.brandId(), brandName),
                    product.likeCount(),
                    product.deletedAt()
            );
        }
    }

    public record BrandInfo(
            UUID id,
            String name
    ) {
    }

    public record UpdateProductRequest(
            @NotBlank(message = "상품명은 필수입니다")
            String name,
            @NotNull(message = "가격은 필수입니다")
            @Min(value = 0, message = "가격은 0 이상이어야 합니다")
            Integer price,
            @NotNull(message = "재고는 필수입니다")
            @Min(value = 0, message = "재고는 0 이상이어야 합니다")
            Integer stock,
            String description,
            @NotNull(message = "카테고리 ID는 필수입니다")
            UUID categoryId,
            UUID brandId
    ) {
        public UpdateProductCommand toCommand() {
            return new UpdateProductCommand(name, price, stock, description, categoryId, brandId);
        }
    }

    public record ProductListResponse(
            List<ProductResponse> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
        public static ProductListResponse from(Page<?> pageData, List<ProductResponse> items) {
            return new ProductListResponse(
                    items,
                    pageData.getNumber(),
                    pageData.getSize(),
                    pageData.getTotalElements(),
                    pageData.getTotalPages()
            );
        }

        public static ProductListResponse from(Page<Product> pageData) {
            List<ProductResponse> items = pageData.getContent().stream()
                    .map(ProductResponse::from)
                    .toList();
            return from(pageData, items);
        }

        public static ProductListResponse from(ProductListView view) {
            List<ProductResponse> items = view.items().stream()
                    .map(ProductResponse::from)
                    .toList();
            return new ProductListResponse(items, view.page(), view.size(), view.totalElements(), view.totalPages());
        }
    }

    public record PublicProductListItemResponse(
            UUID id,
            String name,
            Integer price,
            Integer stock,
            UUID categoryId,
            UUID brandId,
            BrandInfo brand,
            Integer likeCount
    ) {
        public static PublicProductListItemResponse from(PublicProductListItemView view) {
            return new PublicProductListItemResponse(
                    view.id(),
                    view.name(),
                    view.price(),
                    view.stock(),
                    view.categoryId(),
                    view.brandId(),
                    new BrandInfo(view.brandId(), view.brandName()),
                    view.likeCount()
            );
        }
    }

    public record PublicProductListResponse(
            List<PublicProductListItemResponse> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
        public static PublicProductListResponse from(PublicProductListView view) {
            List<PublicProductListItemResponse> items = view.items().stream()
                    .map(PublicProductListItemResponse::from)
                    .toList();
            return new PublicProductListResponse(items, view.page(), view.size(), view.totalElements(), view.totalPages());
        }
    }
}
