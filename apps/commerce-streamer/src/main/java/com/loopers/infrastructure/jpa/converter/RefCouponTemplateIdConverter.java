package com.loopers.infrastructure.jpa.converter;

import com.loopers.domain.coupon.vo.RefCouponTemplateId;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class RefCouponTemplateIdConverter implements AttributeConverter<RefCouponTemplateId, Long> {

    @Override
    public Long convertToDatabaseColumn(RefCouponTemplateId attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public RefCouponTemplateId convertToEntityAttribute(Long dbData) {
        return dbData == null ? null : new RefCouponTemplateId(dbData);
    }
}
