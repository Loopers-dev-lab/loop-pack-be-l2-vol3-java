package com.loopers.application.product.dto;

import com.loopers.support.enums.SortFilter;

public record FindProductListReqDto(
        String loginId,
        String password,
        Long brandId,
        SortFilter sortFilter
) {
}
