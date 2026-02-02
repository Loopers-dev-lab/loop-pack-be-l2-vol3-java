package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

@Component
public class BirthDateValidator {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("uuuu-MM-dd")
            .withResolverStyle(ResolverStyle.STRICT);

    public void validate(String birthDate) {
        if (birthDate == null || birthDate.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 필수입니다.");
        }

        try {
            LocalDate.parse(birthDate, FORMATTER);
        } catch (DateTimeParseException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 yyyy-MM-dd 형식이어야 합니다.");
        }
    }
}
