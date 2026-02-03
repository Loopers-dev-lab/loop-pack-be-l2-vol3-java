package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

@Getter
@Entity
@Table(name = "user")
public class User extends BaseEntity {
    private Long id;
    private String loginId;
    private String password;
    private String name;
    private String birthDate;
    private String email;

    protected User() {}

    public User(String loginId, String password, String name, String birthDate, String email) {
        if (loginId == null || !loginId.matches("^[a-zA-Z0-9]+$")) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 영문/숫자만 가능합니다.");
        }
        
        if (name == null || !name.matches("^\\S+$")) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 빈 값이거나 공백을 포함할 수 없습니다.");
        }

        if (email == null || !email.contains("@") || !email.contains(".")) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일 형식이 올바르지 않습니다.");
        }

        if (birthDate == null || !birthDate.matches("^\\d{8}$")) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 YYYYMMDD 형식이어야 합니다.");
        }

        if (password == null || !password.matches("^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?~`]{8,16}$")) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 8~16자의 영문/숫자/특수문자만 가능합니다.");
        }

        if (password.contains(birthDate)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.");
        }

        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.birthDate = birthDate;
        this.email = email;
    }
}
