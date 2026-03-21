package com.loopers.infrastructure.jpa.converter;

import com.loopers.domain.payment.vo.RefOrderId;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class RefOrderIdConverter implements AttributeConverter<RefOrderId, Long> {

    @Override
    public Long convertToDatabaseColumn(RefOrderId attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public RefOrderId convertToEntityAttribute(Long dbData) {
        return dbData == null ? null : new RefOrderId(dbData);
    }
}
