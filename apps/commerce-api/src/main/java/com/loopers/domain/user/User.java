package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Entity
@Table(name = "user")
public class User extends BaseEntity {
    private Long id;
    private String loginId;
    private String password;
    private String name;
    private LocalDate birthDate;
    private String email;

    protected User() {}

    public User(String loginId, String password, String name, LocalDate birthDate, String email) {
        if (!loginId.matches("^[a-zA-Z0-9]+$")) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 영문/숫자만 가능합니다.");
        }

        if (!name.matches("^\\S+$")) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 빈 값이거나 공백을 포함할 수 없습니다.");
        }

        if (!email.contains("@") || !email.contains(".")) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일 형식이 올바르지 않습니다.");
        }

        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.birthDate = birthDate;
        this.email = email;
    }
}
