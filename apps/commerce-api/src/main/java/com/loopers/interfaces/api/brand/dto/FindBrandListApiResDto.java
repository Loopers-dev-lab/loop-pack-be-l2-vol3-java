package com.loopers.interfaces.api.brand.dto;

import com.loopers.application.brand.dto.FindBrandListResDto;

public record FindBrandListApiResDto(Long id, String name, String description) {

    public static FindBrandListApiResDto from(FindBrandListResDto dto) {
        return new FindBrandListApiResDto(dto.id(), dto.name(), dto.description());
    }
}
