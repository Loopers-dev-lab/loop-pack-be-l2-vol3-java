package com.loopers.infrastructure.jpa.converter;

import com.loopers.domain.member.MemberId;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class MemberIdConverter implements AttributeConverter<MemberId, String> {

    @Override
    public String convertToDatabaseColumn(MemberId attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public MemberId convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new MemberId(dbData);
    }
}
