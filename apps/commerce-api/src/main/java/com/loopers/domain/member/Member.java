package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class Member {

    private static final DateTimeFormatter BIRTHDAY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private Long id;
    private String loginId;
    private String password;
    private String name;
    private LocalDate birthday;
    private String email;

    public Member(String loginId, String password, String name, LocalDate birthday, String email) {
        validateBirthday(birthday);
        validatePasswordNotContainsBirthday(password, birthday);

        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.birthday = birthday;
        this.email = email;
    }

    public Member(Long id, String loginId, String password, String name, LocalDate birthday, String email) {
        this.id = id;
        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.birthday = birthday;
        this.email = email;
    }

    public void encryptPassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void changePassword(String newRawPassword, String newEncodedPassword) {
        validatePasswordNotContainsBirthday(newRawPassword, this.birthday);
        this.password = newEncodedPassword;
    }

    private void validateBirthday(LocalDate birthday) {
        if (birthday != null && birthday.isAfter(LocalDate.now())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 미래 날짜일 수 없습니다.");
        }
    }

    private void validatePasswordNotContainsBirthday(String password, LocalDate birthday) {
        if (password != null && birthday != null) {
            String birthdayStr = birthday.format(BIRTHDAY_FORMATTER);
            if (password.contains(birthdayStr)) {
                throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.");
            }
        }
    }

    public Long getId() {
        return id;
    }

    public String getLoginId() {
        return loginId;
    }

    public String getPassword() {
        return password;
    }

    public String getName() {
        return name;
    }

    public LocalDate getBirthday() {
        return birthday;
    }

    public String getEmail() {
        return email;
    }
}