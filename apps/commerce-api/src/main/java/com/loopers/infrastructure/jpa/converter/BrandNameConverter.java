package com.loopers.infrastructure.jpa.converter;

import com.loopers.domain.brand.BrandName;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class BrandNameConverter implements AttributeConverter<BrandName, String> {

    @Override
    public String convertToDatabaseColumn(BrandName attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public BrandName convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new BrandName(dbData);
    }
}
