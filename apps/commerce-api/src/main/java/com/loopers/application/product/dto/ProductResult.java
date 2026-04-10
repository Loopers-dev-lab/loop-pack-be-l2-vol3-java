package com.loopers.application.product.dto;

import com.loopers.domain.product.ImageType;
import com.loopers.domain.product.ProductImageModel;
import com.loopers.domain.product.ProductModel;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;

public record ProductResult(
    Long id,
    Long brandId,
    String brandName,
    String name,
    int price,
    int stock,
    long likeCount,
    String thumbnailUrl,
    ZonedDateTime createdAt,
    ZonedDateTime updatedAt,
    ZonedDateTime deletedAt
) {
    public static ProductResult of(ProductModel model, String brandName) {
        return new ProductResult(
                model.getId(),
                model.getBrandId(),
                brandName,
                model.getName(),
                model.getPrice(),
                model.getStock(),
                model.getLikeCount(),
                model.getThumbnailUrl(),
                model.getCreatedAt(),
                model.getUpdatedAt(),
                model.getDeletedAt());
    }

    public static List<ProductResult> fromWithActiveBrand(
            List<ProductModel> products, Map<Long, String> brandNameMap) {
        return products.stream()
                .filter(product -> brandNameMap.containsKey(product.getBrandId()))
                .map(product -> ProductResult.of(
                        product, brandNameMap.get(product.getBrandId())))
                .toList();
    }

    public record ImageResult(Long id, String imageUrl, ImageType imageType, int sortOrder) {
        public static ImageResult from(ProductImageModel model) {
            return new ImageResult(
                    model.getId(), model.getImageUrl(), model.getImageType(), model.getSortOrder());
        }
    }

    public record ListPage(
        int page, int size, long totalElements, int totalPages,
        List<ProductResult> items
    ) {
        public static ListPage from(Page<ProductResult> resultPage) {
            return new ListPage(
                    resultPage.getNumber(),
                    resultPage.getSize(),
                    resultPage.getTotalElements(),
                    resultPage.getTotalPages(),
                    new ArrayList<>(resultPage.getContent()));
        }
    }

    public record DetailWithImages(
        ProductResult product,
        List<ImageResult> mainImages,
        List<ImageResult> detailImages
    ) {
    }
}
