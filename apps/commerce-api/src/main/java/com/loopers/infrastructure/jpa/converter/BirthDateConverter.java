package com.loopers.infrastructure.jpa.converter;

import com.loopers.domain.member.BirthDate;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class BirthDateConverter implements AttributeConverter<BirthDate, String> {

    @Override
    public String convertToDatabaseColumn(BirthDate attribute) {
        return attribute == null ? null : attribute.asString(); // yyyy-MM-dd
    }

    @Override
    public BirthDate convertToEntityAttribute(String dbData) {
        return dbData == null ? null : BirthDate.fromString(dbData);
    }
}
