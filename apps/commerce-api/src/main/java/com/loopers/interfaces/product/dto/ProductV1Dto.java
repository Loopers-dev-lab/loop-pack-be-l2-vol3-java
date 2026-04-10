package com.loopers.interfaces.product.dto;

import com.loopers.application.product.dto.ProductResult;
import com.loopers.application.product.dto.ProductResult.ListPage;
import java.util.List;

public class ProductV1Dto {

    public record DetailResponse(
        Long id,
        Long brandId,
        String brandName,
        String name,
        int price,
        int stock,
        long likeCount,
        String thumbnailUrl,
        List<ImageResponse> mainImages,
        List<ImageResponse> detailImages,
        Long rank
    ) {
        public static DetailResponse from(ProductResult.DetailWithImages detail) {
            ProductResult product = detail.product();
            return new DetailResponse(
                    product.id(),
                    product.brandId(),
                    product.brandName(),
                    product.name(),
                    product.price(),
                    product.stock(),
                    product.likeCount(),
                    product.thumbnailUrl(),
                    detail.mainImages().stream().map(ImageResponse::from).toList(),
                    detail.detailImages().stream().map(ImageResponse::from).toList(),
                    detail.rank());
        }
    }

    public record ImageResponse(Long id, String imageUrl, String imageType, int sortOrder) {
        public static ImageResponse from(ProductResult.ImageResult image) {
            return new ImageResponse(
                    image.id(), image.imageUrl(), image.imageType().name(), image.sortOrder());
        }
    }

    public record ListResponse(
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<ListItem> items
    ) {
        public static ListResponse from(ListPage listPage) {
            return new ListResponse(
                    listPage.page(),
                    listPage.size(),
                    listPage.totalElements(),
                    listPage.totalPages(),
                    listPage.items().stream()
                            .map(ListItem::from)
                            .toList());
        }

        public record ListItem(
            Long id,
            Long brandId,
            String brandName,
            String name,
            int price,
            long likeCount,
            String thumbnailUrl
        ) {
            public static ListItem from(ProductResult info) {
                return new ListItem(
                        info.id(),
                        info.brandId(),
                        info.brandName(),
                        info.name(),
                        info.price(),
                        info.likeCount(),
                        info.thumbnailUrl());
            }
        }
    }
}
