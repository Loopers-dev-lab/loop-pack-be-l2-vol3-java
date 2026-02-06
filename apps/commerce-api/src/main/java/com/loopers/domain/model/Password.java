package com.loopers.domain.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

@Getter
@EqualsAndHashCode
@ToString
public class Password {
    private static final Pattern ALLOWED_CHARS = Pattern.compile("^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?`~]{8,16}$");
    private static final DateTimeFormatter FMT_YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter FMT_YYMMDD = DateTimeFormatter.ofPattern("yyMMdd");
    private static final DateTimeFormatter FMT_MMDD = DateTimeFormatter.ofPattern("MMdd");
    private static final DateTimeFormatter FMT_YYYY_MM_DD = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter FMT_YY_MM_DD = DateTimeFormatter.ofPattern("yy-MM-dd");
    private static final DateTimeFormatter FMT_MM_DD = DateTimeFormatter.ofPattern("MM-dd");

    private final String value;

    private Password(String value) {this.value = value;}

    public static Password of(String rawPassword, LocalDate birthday) {
        validate(rawPassword, birthday);
        return new Password(rawPassword);
    }
    private static void validate(String rawPassword, LocalDate birthday) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("비밀번호는 필수 입력값입니다.");
        }

        if (!ALLOWED_CHARS.matcher(rawPassword).matches()) {
            throw new IllegalArgumentException("비밀번호는 8~16자리 영문 대소문자, 숫자, 특수문자만 가능합니다.");
        }
        if (birthday != null && containsBirthday(rawPassword, birthday)) {
            throw new IllegalArgumentException("생년월일은 비밀번호 내에 포함될 수 없습니다.");
        }
    }

    static boolean containsBirthday(String rawPassword, LocalDate birthday) {
        //yyyyMMdd, yyMMdd, MMdd 같은 포멧은 다 제외 하는걸로
        List<String> patterns = List.of(
                birthday.format(FMT_YYYYMMDD),
                birthday.format(FMT_YYMMDD),
                birthday.format(FMT_MMDD),
                birthday.format(FMT_YYYY_MM_DD),
                birthday.format(FMT_YY_MM_DD),
                birthday.format(FMT_MM_DD)
        );

        return patterns.stream().anyMatch(rawPassword::contains);

    }
}
