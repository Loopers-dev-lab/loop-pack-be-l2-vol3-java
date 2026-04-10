package com.loopers.infrastructure.product;

import com.loopers.domain.product.StockQuantity;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class StockQuantityConverter implements AttributeConverter<StockQuantity, Integer> {

    @Override
    public Integer convertToDatabaseColumn(StockQuantity attribute) {
        return attribute != null ? attribute.value() : null;
    }

    @Override
    public StockQuantity convertToEntityAttribute(Integer dbData) {
        return dbData != null ? new StockQuantity(dbData) : null;
    }
}
