package com.loopers.application.product;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.springframework.data.domain.Page;

import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
public record ProductPageInfo(
    List<ProductInfo> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    public static ProductPageInfo from(Page<ProductInfo> page) {
        return new ProductPageInfo(
            page.getContent(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );
    }
}
