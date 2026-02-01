package com.loopers.infrastructure.jpa.converter;

import com.loopers.domain.member.Name;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class NameConverter implements AttributeConverter<Name, String> {

    @Override
    public String convertToDatabaseColumn(Name attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public Name convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new Name(dbData);
    }
}
