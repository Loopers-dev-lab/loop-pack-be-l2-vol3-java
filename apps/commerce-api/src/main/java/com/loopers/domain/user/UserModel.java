package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor
public class UserModel extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String loginId;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private LocalDate birthday;

    @Column(nullable = false)
    private String email;

    private static final String LOGIN_ID_PATTERN = "^[a-zA-Z0-9]+$";
    private static final String EMAIL_PATTERN = "^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$";

    public UserModel(String loginId, String password, String name, String birthday, String email){
        validateLoginId(loginId);
        validateName(name);
        validateEmail(email);
        validatePassword(password);

        this.loginId = loginId;
        this.name = name;
        this.email = email;
        this.password = password;
        this.birthday = parseBirthday(birthday);
    }

    private void validateLoginId(String loginId){
        if(loginId == null || loginId.isBlank()){
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 비어있을 수 없습니다.");
        }
        if(!loginId.matches(LOGIN_ID_PATTERN)){
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 영문과 숫자만 허용됩니다.");
        }
    }

    private void validateName(String name){
        if(name == null || name.isBlank()){
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 비어있을 수 없습니다.");
        }
    }

    private void validateEmail(String email){
        if(email == null || email.isBlank()){
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일은 비어있을 수 없습니다.");
        }
        if(!email.matches(EMAIL_PATTERN)){
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일 형식이 올바르지 않습니다.");
        }
    }

    private void validatePassword(String password){
        if(password == null || password.isBlank()){
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 비어있을 수 없습니다.");
        }
    }

    public void changePassword(String encryptedPassword) {
        validatePassword(encryptedPassword);
        this.password = encryptedPassword;
    }

    private LocalDate parseBirthday(String birthday){
        if(birthday == null || birthday.isBlank()){
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 비어있을 수 없습니다.");
        }
        LocalDate parsed;
        try{
            parsed = LocalDate.parse(birthday);
        }catch(DateTimeParseException e){
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일 형식이 올바르지 않습니다.");
        }
        if(parsed.isAfter(LocalDate.now())){
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 미래 날짜일 수 없습니다.");
        }
        return parsed;
    }
}
