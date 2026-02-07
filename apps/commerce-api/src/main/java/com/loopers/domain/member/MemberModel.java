package com.loopers.domain.member;


import com.loopers.domain.BaseEntity;
import com.loopers.infrastructure.jpa.converter.BirthDateConverter;
import com.loopers.infrastructure.jpa.converter.EmailConverter;
import com.loopers.infrastructure.jpa.converter.MemberIdConverter;
import com.loopers.infrastructure.jpa.converter.NameConverter;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;
import lombok.Getter;

import java.util.regex.Pattern;

@Entity
@Table(name = "member")
public class MemberModel extends BaseEntity {

    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]).{8,16}$"
    );

    @Getter
    @Convert(converter = MemberIdConverter.class)
    @Column(nullable = false, unique = true, length = 10)
    private MemberId memberId;

    @Column(nullable = false)
    private String password;

    @Getter
    @Convert(converter = EmailConverter.class)
    @Column(length = 100)
    private Email email;

    @Getter
    @Convert(converter = BirthDateConverter.class)
    @Column(length = 10)
    private BirthDate birthDate;

    @Getter
    @Convert(converter = NameConverter.class)
    @Column(length = 50)
    private Name name;

    @Getter
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Gender gender;

    protected MemberModel() {}

    MemberModel(String memberId, String password) {
        this.memberId = new MemberId(memberId);
        this.password = password;
    }

    MemberModel(String memberId, String password, String email) {
        this(memberId, password);
        this.email = new Email(email);
    }

    MemberModel(String memberId, String password, String email, String birthDate) {
        this(memberId, password, email);
        this.birthDate = BirthDate.fromString(birthDate);
    }

    MemberModel(String memberId, String password, String email, String birthDate, String name) {
        this(memberId, password, email, birthDate);
        this.name = new Name(name);
    }

    MemberModel(String memberId, String password, String email, String birthDate, String name, Gender gender) {
        this(memberId, password, email, birthDate, name);
        this.gender = gender;
    }

    public static MemberModel create(String memberId, String rawPassword, String email,
                                      String birthDate, String name, Gender gender,
                                      PasswordHasher passwordHasher) {
        validateRawPassword(rawPassword);
        validatePasswordNotContainsBirthDate(rawPassword, birthDate);
        validateGender(gender);

        String hashedPassword = passwordHasher.hash(rawPassword);
        return new MemberModel(memberId, hashedPassword, email, birthDate, name, gender);
    }

    public boolean verifyPassword(PasswordHasher passwordHasher, String rawPassword) {
        return passwordHasher.matches(rawPassword, this.password);
    }

    public void matchesPassword(PasswordHasher passwordHasher, String rawPassword) {
        if (!verifyPassword(passwordHasher, rawPassword)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호가 일치하지 않습니다.");
        }
    }

    public void changePassword(String rawCurrentPassword, String newRawPassword,
                               PasswordHasher passwordHasher) {
        matchesPassword(passwordHasher, rawCurrentPassword);

        if (passwordHasher.matches(newRawPassword, this.password)) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    "새 비밀번호는 기존 비밀번호와 다르게 설정해야 합니다.");
        }

        validateRawPassword(newRawPassword);
        validatePasswordNotContainsBirthDate(newRawPassword,
                this.birthDate != null ? this.birthDate.asString() : null);

        this.password = passwordHasher.hash(newRawPassword);
    }

    private static void validateRawPassword(String rawPassword) {
        if (rawPassword == null || !PASSWORD_PATTERN.matcher(rawPassword).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    "비밀번호는 8~16자의 영문 대소문자, 숫자, 특수문자를 모두 포함해야 합니다.");
        }
    }

    private static void validatePasswordNotContainsBirthDate(String rawPassword, String birthDate) {
        if (birthDate == null || birthDate.isBlank()) {
            return;
        }

        String cleanBirthDate = birthDate.replace("-", "");
        String monthDay = cleanBirthDate.substring(4);
        String year = cleanBirthDate.substring(0, 4);

        if (rawPassword.contains(cleanBirthDate) ||
            rawPassword.contains(monthDay) ||
            rawPassword.contains(year)) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    "비밀번호에 생년월일을 포함할 수 없습니다.");
        }
    }

    private static void validateGender(Gender gender) {
        if (gender == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "성별은 필수입니다.");
        }
    }
}
