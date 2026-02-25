package com.loopers.application.brand;

import com.loopers.domain.brand.BrandModel;

/**
 * 브랜드 응답용 애플리케이션 DTO.
 * Controller 응답에 사용하며, interfaces DTO와 분리한다.
 */
public record BrandInfo(Long id, String name) {
    public static BrandInfo from(BrandModel brand) {
        if (brand == null) {
            return null;
        }
        return new BrandInfo(brand.getId(), brand.getName());
    }
}
