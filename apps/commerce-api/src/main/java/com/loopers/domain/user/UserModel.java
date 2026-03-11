package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorMessage;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

@Entity
@Table(name = "users")
@NoArgsConstructor
@Getter
public class UserModel extends BaseEntity {

    @Id
    @GeneratedValue
    private Long id;

    @Comment("아이디")
    @Column(name = "login_id", nullable = false)
    private String loginId;

    @Comment("비밀번호")
    @Column(name = "password", nullable = false)
    private String password;

    @Comment("생년월일")
    @Column(name = "birth", nullable = false)
    private LocalDate birthDate;

    @Comment("이름")
    @Column(name = "name", nullable = false)
    private String name;

    @Comment("이메일")
    @Column(name = "email", nullable = false)
    private String email;

    private UserModel(String loginId, String password, LocalDate birthDate, String name, String email) {
        this.loginId = loginId;
        this.password = password;
        this.birthDate = birthDate;
        this.name = name;
        this.email = email;
    }

    @Override
    protected void guard() {
        if (loginId == null || loginId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.User.LOGIN_ID_REQUIRED);
        }
        if (password == null || password.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.User.PASSWORD_REQUIRED);
        }
        if (birthDate == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.User.BIRTH_DATE_REQUIRED);
        }
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.User.NAME_REQUIRED);
        }
        if (email == null || email.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.User.EMAIL_REQUIRED);
        }
    }

    public static UserModel create(String loginId, String encodedPassword, LocalDate birthDate, String name, String email) {
        return new UserModel(loginId, encodedPassword, birthDate, name, email);
    }

    public void changePassword(String newEncodedPassword) {
        this.password = newEncodedPassword;
    }
}
