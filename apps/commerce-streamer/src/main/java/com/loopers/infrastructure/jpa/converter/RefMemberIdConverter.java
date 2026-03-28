package com.loopers.infrastructure.jpa.converter;

import com.loopers.domain.common.vo.RefMemberId;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class RefMemberIdConverter implements AttributeConverter<RefMemberId, Long> {

    @Override
    public Long convertToDatabaseColumn(RefMemberId attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public RefMemberId convertToEntityAttribute(Long dbData) {
        return dbData == null ? null : new RefMemberId(dbData);
    }
}
